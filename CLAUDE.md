# CLAUDE.md - AI Coding Copilot Instructions

Operating instructions for any LLM agent working on **uaexp**. Style rules live in
`LLM_CODE_STYLE.md`; read both.

## What uaexp is

uaexp reads OPC UA nodeset XML (Part 5 core, companion specifications, and an operator's own
extensions) and loads it into Datahike stores that can be queried with Datalog and walked by
node address. It is exploratory research code from the NIST project *Human/AI Teaming for
Manufacturing Digital Twins*. There is no server and no API despite `ua.core/server` — that
defstate returns `:started` and does nothing else.

The narrative of how it got here is `doc/log-uaexp.org`, newest at the bottom. Read the last
couple of entries before starting anything substantial; decisions are recorded there and
nowhere else.

## The store model — the one concept to get right

**One store holds one version of one namespace.** Not one DB per profile, and not one merged
DB. This is the design that everything else follows from, and it is recent (2026-08-27/28), so
older code, docs and log entries describe a `:part5` DB that no longer exists.

A NodeId's `ns=<index>` is an index into the *file's own* `<NamespaceUris>` table, so the same
literal string names different nodes in different files. `ns=1;i=1002` is `ObligationType` in
OPC 40501-1 and `CNC04MachineOperationMonitoringType` in CNC04's extension. Anything that keys
on the literal NodeId silently merges the two. So:

- Addresses between stores are OPC UA **ExpandedNodeIds** (Part 6, Annex A):
  `nsu=http://opcfoundation.org/UA/MachineTool/;i=26`.
- Inside a store the namespace is implied, so `:Node/id` is the bare identifier: `i=26`.
- A reference leaving the nodeset's own namespace is stored as a **foreign key**,
  `{:Node/ref "nsu=...;i=80"}`, never as an entity id — an entity id means nothing in another
  store. `dbu/resolve-node` stops there; `dbu/expand-n` crosses on purpose.
- Because references out are foreign keys, **nodesets load in any order and alone**. Nothing
  has to be present first, and a target need not exist at all.

`ua.nsuri` owns all of this. Do not parse a NodeId anywhere else.

## Databases

Stores live under `$UAEXP_DB` (`/opt/uaexp`), laid out by namespace URI and version, so the
path says what the store holds (the filesystem collapses the scheme's `//`):

```
/opt/uaexp/http:/opcfoundation.org/UA/1.05.04              68M   Part 5 core, 5820 nodes
/opt/uaexp/http:/opcfoundation.org/UA/MachineTool/1.02.0   4.7M  OPC 40501-1
/opt/uaexp/http:/example-machineworks.com/UA/CNC04/0.1.0   248K  a shop's extension
```

`dbu/discover-stores!` registers them at startup. It confirms each against the store's own root
(`:NodeSet/uri`, `:NodeSet/version`) rather than trusting the path, so a misfiled store is
reported instead of registered under the wrong name.

**Everything here is regenerable** from the XML in `data/`, so rebuilding a store is cheap and
losing one is not a disaster. That is not true of Tessell's DBs — do not carry the assumption over.

**Isolate Datahike.** Queries and pulls belong in `ua.db-util` and `ua.putil`. If you need
state, add a function there rather than writing `d/q` in a new file.

## Code layout

```
src/ua/nsuri.clj          addresses, namespace tables, canonicalization, version compare
src/ua/db_util.clj        store registry, config, resolve-node / deeper / walk-final / expand-n
src/ua/putil.clj          XML->EDN rewriting (defparse multimethod), schema learning, loading
src/ua/build_part5.clj    the defparse methods for Part 5's vocabulary; init-part5 defstate
src/ua/profiles.clj       make-store!, schema+ generation for non-P5 nodesets
src/ua/p5_cardinality.clj hand-written cardinality table for Part 5 ReferenceTypes
src/ua/xml_util.clj       clojure.data.xml wrapper
src/ua/util.cljc          logging config (Telemere); util-state defstate
resources/part5/part5-schema+.edn   the Part 5 schema, read at load by ua.putil
env/dev/                  user.clj (start/stop), develop/repl.clj (alias-map), develop/dutil.clj
test/ua/                  clojure.test; db_util_test.clj is the real one
```

`defparse` forms are methods of `pu/rewrite-xml`, which lives in `ua.putil`. Requiring
`ua.build-part5` for its side effects is deliberate, not an unused require.

## REPL workflow

**Do not start a Clojure process without asking first.** A REPL is normally already running;
use it. `clj-nrepl-eval --discover-ports`, then `clj-nrepl-eval -p <port> "<code>"`. Reload with
`:reload` to pick up edits. A classpath change (a new dependency, a new `:paths` entry) is the
one thing the running REPL cannot absorb — say so and let the user restart it.

Namespace aliases come from `develop.repl/alias-map` (`dbu`, `pu`, `pro`, `bp5`, `nsuri`, `xu`,
`util`). Use those names in code and in conversation, and add to the map when you add a file.

Run tests in the REPL — `(require '[ua.db-util-test] :reload)` then
`(clojure.test/run-tests 'ua.db-util-test)`. As of 2026-08-31 the suite is 4 tests / 9
assertions / 0 failures. **The `:test` alias in `deps.edn` does not work**: it names kaocha and
an `:extra-paths` entry `src/server` that does not exist. Don't invoke it.

## Direction

Decided 2026-08-29, not yet acted on:

- uaexp stays the **producer** — XML parsing, canonicalization, store building. This half is
  OPC-UA-shaped and would be written differently for another SDO's format.
- Tessell (`~/Documents/git/Tessell`, formerly `oide`) **copies the reader slice** (~300 lines:
  `nsuri`, the store-config part of `db_util`, `resolve-node`/`expand-n`) rather than depending
  on uaexp, following the RADmapper precedent. Tessell then reads these stores with its own
  `db_query` MCP tool.
- **No MCP server for uaexp.** Version skew was the only justification and it is gone.
- Next build: a uaexp **system DB** designed as a queryable catalog — store inventory,
  provenance, declared-vs-actual dependencies — rather than an API, so an orchestrator can
  explore it with `db_query` instead of being taught a tool vocabulary.

The motivating use case is composition: an operator subtyping a standard type in their own
namespace. A single-file index cannot represent that at all, which is the case for this code
over `mine_nodeset.py`.

## Known hazards — check before trusting

- **schema+ generation writes unreadable idents.** `pro/make-p5-std-ref-type-schema` builds
  `(keyword "P5StdRefType" browse-name)`, and a browse-name outside ns=0 carries its namespace
  index — `"1:Contains"` — giving `:P5StdRefType/1:Contains`, which `keyword` will make and the
  reader will not read. So any nodeset defining its own ReferenceTypes produces a schema+ file
  that cannot be loaded back — this is what made the former `data/profiles/amb/amb-schema+.edn`
  unreadable. Hence `pro/make-profile-db!` works only *without* `:schema+-file`. Fixing
  it needs a decision, not just an escape: stripping the `N:` prefix is right by the file-local-index
  rule, but then a profile's `Contains` and Part 5's collide in the merged schema.
- `ua.util/custom-console-output-fn` still opens with `(when-not (= (:kind signal) :agents) ...)`.
  The `:agents` handler is gone and nothing emits that kind, so the guard can never be false.
- `data/part5/` holds **two** Part 5 core models, and they are not duplicates:
  `OPC_UA_Core_Model_2515947497.xml` is v1.05.04 and `..._2710599569.xml` is v1.05.03. Since
  stores are versioned, the older one is the material for holding two versions of the base
  namespace at once. Only the 1.05.04 one is referenced by code today
  (`bp5/init-part5` reads the derived `p5-nodeset.edn`, by relative path).

## Working practice

- **Surgical edits.** Preserve existing spacing and alignment; multiple spaces inside a line are
  usually deliberate. Don't reformat a function you are changing one line of.
- **No lists for data.** Maps, vectors, sets and primitives only — recursive walkers throughout
  this code dispatch on `map?` and `vector?` alone, and a seq falls through both.
- `^:diag` for REPL-only definitions, `^:admin` for developer/build-time ones. Both are used
  here and the distinction is worth keeping.
- **Ask questions** when the request is ambiguous rather than guessing.
