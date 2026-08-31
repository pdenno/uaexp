# uaexp

This exploratory software reads [OPC UA](https://opcfoundation.org/) nodesets — the Part 5 core
model, companion specifications, and an operator's own extensions to them — into
[Datahike](https://github.com/replikativ/datahike) stores that can be queried with Datalog and
walked by node address.

The software is being developed as part of the NIST project [Human/AI Teaming for Manufacturing
Digital Twins](https://www.nist.gov/programs-projects/humanmachine-teaming-manufacturing-digital-twins).
Feel free to contact us if this work interests you!

## Why not just parse the file?

A NodeId's `ns=<index>` is an index into *the file's own* `<NamespaceUris>` table. The same
literal string names different nodes in different files: `ns=1;i=1002` is `ObligationType` in OPC
40501-1 and `CNC04MachineOperationMonitoringType` in a machine shop's extension of it. Any tool
that keys on the literal NodeId merges the two without saying so.

That matters because the interesting content is rarely in one file. In OPC 40501-1
(MachineTool), 1041 of 2487 reference targets — 41% — lie outside the file, and 40 of its 62
ObjectTypes inherit from a supertype defined elsewhere, so a single-file reader cannot see the
inherited members at all.

uaexp gives every node an address that means the same thing everywhere: OPC UA's own
**ExpandedNodeId** (Part 6, Annex A), which names the namespace by URI rather than by index.

```
nsu=http://opcfoundation.org/UA/;i=25345
nsu=http://opcfoundation.org/UA/MachineTool/;i=26
```

**One store holds one version of one namespace.** A reference that leaves the nodeset's own
namespace is stored as a foreign key — `{:Node/ref "nsu=...;i=80"}` — rather than as an entity
id, which would be meaningless in another store. Consequently nodesets load in any order and
alone: nothing has to be present first, and a reference target need not exist at all.

## Building/Running (development mode)

These instructions have not been thoroughly tested and are likely not complete. If you have
problems, write an issue or email us (see the NIST project page above). This has only been tested
on Linux.

Install a Java JDK and [Clojure](https://clojure.org/), then point an environment variable at the
directory where stores should live:

```
export UAEXP_DB=/opt/uaexp
```

From the top-level directory of this repository, start a REPL and start the system:

```
clj -M:dev
```
```clojure
(start)
```
```
EVENT/INFO  : - Enabling interop: standard stream/s -> Telemere
EVENT/INFO  : - Logging configured:
{:tools-logging
 {:present? true,
  :enabled-by-env? false,
  :sending->telemere? true,
  :telemere-receiving? true},
 :slf4j {:present? true, :telemere-provider-present? false},
 :open-telemetry {:present? false, :use-tracer? false},
 :system/out {:sending->telemere? true, :telemere-receiving? true},
 :system/err {:sending->telemere? true, :telemere-receiving? true}}
2026-08-31T15:22:01.872Z HP840G8 INFO [user:33] - started:
	#'ua.util/util-state,
	#'ua.build-part5/part5,
	#'ua.core/server
```

Startup discovers and registers every store under `$UAEXP_DB`, confirming each against the
store's own root rather than trusting its path, so a misfiled store is reported instead of being
registered under the wrong name:

```clojure
(develop.repl/ns-setup!)   ; namespace aliases: dbu, pu, pro, bp5, nsuri, ...
(:stores bp5/part5)
```
```clojure
[{:prefix "http://example-machineworks.com/UA/CNC04/", :version "0.1.0"}
 {:prefix "http://opcfoundation.org/UA/",              :version "1.05.04"}
 {:prefix "http://opcfoundation.org/UA/MachineTool/",  :version "1.02.0"}]
```

### Creating stores

If you have no stores yet, build the Part 5 core:

```clojure
(reset! bp5/recreate-db? true)    ; allow an existing store to be replaced
(bp5/init-part5)
```

Any other nodeset is two steps — XML to EDN, then EDN to a store. There is no dependency order to
get right and no `:db-id` to choose; the store's namespace and version are read from the
nodeset's own `<Models>`:

```clojure
(pu/write-nodeset-edn! "data/profiles/cnc04/CNC04.NodeSet2.xml"
					   "data/profiles/cnc04/cnc04-nodeset.edn")
(pro/make-store! "data/profiles/cnc04/cnc04-nodeset.edn")
;; => {:prefix "http://example-machineworks.com/UA/CNC04/", :version "0.1.0"}
```

## Looking at what you loaded

`resolve-node` takes an address. A bare identifier is shorthand for the base UA namespace, which
is what Part 5's documentation uses and what you type at the REPL:

```clojure
(pprint (dbu/resolve-node "i=25345"))
```
```clojure
#:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
	   :type :UAReferenceType,
	   :references
	   [#:P5StdRefType{:SubtypeOf
					   #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
							  :type :UAReferenceType,
							  :references
							  [#:P5StdRefType{:SubtypeOf
											  #:Node{:symmetric? true,
													 :documentation "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.1",
													 :type :UAReferenceType,
													 :id "i=31",
													 :category "Base Info Base Types",
													 :display-name "References",
													 :is-abstract? true,
													 :browse-name "References"}}],
							  :inverse-name "InverseHierarchicalReferences",
							  :id "i=33",
							  :category "Base Info Base Types",
							  :display-name "HierarchicalReferences",
							  :is-abstract? true,
							  :browse-name "HierarchicalReferences"}}],
	   :inverse-name "HasPushTarget",
	   :id "i=25345",
	   :category "PubSub Model SKS Push",
	   :display-name "HasPushedSecurityGroup",
	   :browse-name "HasPushedSecurityGroup"}
```

### Crossing a namespace boundary

Resolution deliberately stops at the store's edge. Asking a machine shop's private extension type
for its supertype returns an address, not a node:

```clojure
(->> (dbu/resolve-node "nsu=http://example-machineworks.com/UA/CNC04/;i=1002")
	 :Node/references
	 (keep :P5StdRefType/SubtypeOf))
;; => (#:Node{:ref "nsu=http://opcfoundation.org/UA/MachineTool/;i=26"})
```

`expand-n` follows those foreign keys, n times, so you cross on purpose rather than by accident.
Walking the chain by hand gives the answer a single-file reader cannot produce — an inheritance
path through three namespaces held in three separate stores:

```clojure
(defn supertypes [address]
  (loop [a address, acc []]
	(let [n (dbu/resolve-node a)
		  acc (conj acc [(:Node/browse-name n) (:uri (nsuri/parse-address a))])]
	  (if-let [up (->> n :Node/references (keep :P5StdRefType/SubtypeOf) first :Node/ref)]
		(recur up acc)
		acc))))

(supertypes "nsu=http://example-machineworks.com/UA/CNC04/;i=1002")
```
```clojure
[["CNC04MachineOperationMonitoringType" "http://example-machineworks.com/UA/CNC04/"]
 ["MachineOperationMonitoringType"      "http://opcfoundation.org/UA/MachineTool/"]
 ["BaseObjectType"                      "http://opcfoundation.org/UA/"]]
```

Everything is also open to plain Datalog:

```clojure
(d/q '[:find ?id ?bn
	   :where [?e :Node/id ?id] [?e :Node/browse-name ?bn] [?e :Node/type :UAObjectType]]
	 @(dbu/connect-atm "http://example-machineworks.com/UA/CNC04/"))
;; => #{["i=1002" "CNC04MachineOperationMonitoringType"]}
```

A bare namespace URI names its newest registered version; `{:prefix ... :version ...}` names one
exactly.

## Status

Exploratory research code. There is no API and no server — `ua.core/server` is a placeholder.
`doc/log-uaexp.org` is the development log and records the reasoning behind the current design,
newest entries at the bottom.
