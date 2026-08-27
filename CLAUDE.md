# CLAUDE.md - AI Coding Copilot Instructions

Essential operating instructions for any LLM agent (Claude, Cursor, etc.) working on this Clojure project.
The project implements an MCP server, sched6, and sched6 is running when you start. Sched6 provides approximately 16 MCP tools and a few MCP resources.

## Code layout — not all code is in `src`
Production code lives in `src/`, but there is also live project code under `env/` — notably `env/dev/develop/` (REPL/system) and `env/dev/study/` (reports, study DB, e.g. `study/project_reports.clj`, `study/study_db.clj`). When doing project-wide sweeps (renames, API removals, schema changes) **grep and edit `env/` too, not just `src/`** — misses there compile fine but silently produce wrong results.

# Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

`clj-nrepl-eval --discover-ports`

**Evaluate code:**

`clj-nrepl-eval -p <port> "<clojure-code>"`

With timeout (milliseconds)

`clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"`

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.
Evaluate the following with `xlj-nrepl-eval`: `(require <some namespace> :reload)`.

## Starting up
- The system, including its MCP loop, is probably running when you join.
  To check that things are as they should be:
  1. Review the file `env/dev/develop/repl.clj`, variable `alias map`; the user will use the aliases defined there in communication with you.
  2. `clj-nrepl-eval -p <port> (sutil/connect-atm :system)`; it should return a DB connection object.
  3. `clj-nrepl-eval -p <port> @mutil/mcp-components-atm`; it should return {:tools [...], :prompts [...], :resource [...]}.

## Coding Rules

### Naming Conventions
- **Boolean variables**: End with `?` (e.g. `inv/active?`, `mock?`)
- **Mutating functions**: End with `!` (e.g. `update-db!`, `reset-state!`)
- **Diagnostic definitions**: Tag with `^:diag` metadata for REPL-only usage
- **Namespace aliases**: These should be short and `itools` not `interviewer-tools`. The same alias should be used in all files.
- **Some specific variables**:
	  `pid` should be the only variable name used to refer to a project ID. Its value is a keyword.
	  There is no conversation ID (CID). Messages are stored flat on the project (`:project/messages`); the process/data/resources/optimality distinction survives only as DS namespaces (the `interviewing/domain` subdirs), and a message is classified by its `:message/pursuing-ASCR` namespace. Semantic (embedding) search, not conversation buckets, is how we find what a discussion is about.

## Logging
- The system logs action to `logs/sched6-log.txt`

### When demonstrating code to the user:
- **Avoid writing files of 'hacks' for demonstration** When things don't work stop and ask for instructions.

### Data Management
- **Prefer atoms** to dynamic variable for persistent state. (One exception so far: `ts/*mcp-exchange*`).
  ```clojure
  ;;; Good
  (def mock? (atom false))

  ;;; Avoid
  (def ^:dynamic *mock-enabled* false)
  ```
- **Prefer the Project DB to atoms** Avoid creating atoms where project state is concerned. Use the functions in `src/sched6/project_db.clj`.
   Write a new utility for `project_db.clj` if necessary.
- **Isolate use of Datahike to a few files** There is pretty much nothing persistent that doesn't belong in code, the system DB, or a project DB.
  Therefore, avoid writing Datahike queries and pulls in all files except `src/sched6/system_db.clj`, `src/sched6/project_db.clj`, and `src/sched6/sutil.clj`.
- Whenever you need state information, look into those files and see if something there is appropriate, if not, add a function to whichever of the three above is appropriate.

### Data Structures
- Use maps, vectors, sets, and primitive data types to store data.
- **Do NOT use lists** (sequences) for data storage. We stipulate this because recursive navigation of structures uses just `map?` and `vector?`.
- Write Malli schema for important objects. See `schema.clj` for examples.
- Use Malli schema and transformation to and from MCP tool arguments and implementations.

### Use Promesa Promises where Concurrency is Needed
See https://funcool.github.io/promesa/latest/promesa.core.html

### Surgical Changes Only
- Make **minimal, targeted edits** - don't reformat entire functions
- **Preserve existing spacing and indentation**
- **Respect whitespace patterns** in conditional forms:
  ```clojure
  ;; Preserve this spacing pattern
  (cond
	  (test-1)                     1
	  (much-longer-test)           2)

  ;; Don't collapse to single spaces
  ```
### Code Review Standards
- Maintain existing code style and patterns
- Don't introduce unnecessary formatting changes
- Don't write pointless comments:

```clojure
;;; Read config file. (example pointless comment)
(read-config-file)
```
### Ask Questions
- Any time you need clarification, stop and ask questions.

---
*Keep this file under 120 lines for quick loading. Last updated: $(date)*
