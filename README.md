# uaexp
This exploratory software implements a server for OPC UA Part 5 for use with CESMII architecture.
The sofware is written in Clojure and uses Datahike, a graph database that features hitchhiker trees and Datalog-native access.
The code only represents about two-weeks of work, but it provides a basic DB with OPC UA Part 5 in it.
We plan to implement graphQL and the upcoming CESMII API.

The software is being developed as part of the NIST project [Human/AI Teaming for Manufacturing Digital Twins](https://www.nist.gov/programs-projects/humanmachine-teaming-manufacturing-digital-twins).
Feel free to contact us if this work interests you!

## Building/Running (development mode)
   These instructions have not been thoroughly tested and are likely not complete. If you have problems, write an issue or email us (see the NIST project page above).

### Set up environment variables
  * Define an environment variable to find the DB you will make. For example,  `export UAEXP_DB=/opt/uaexp`.

### The server
  * Install a Java JDK and [Clojure](https://clojure.org/).
  * The following assumes you are trying things from a shell script, but, of course, there is quite likely a nicer arrangement available in your IDE.
  * BTW, this has only been tested on linux.
  * From a shell prompt in the top-level directory of this repository:

 ```
 clj -M:dev

;;; [There will be some warnings about SLF4J....] then at the prompt 'user>'. Type:
 (start)

;;; Something like the following should be printed:

EVENT/INFO  : - Enabling interop: standard stream/s -> Telemere
EVENT/INFO  : - Logging configured:
{:tools-logging
 {:present? true,
  :enabled-by-env? false,
  :sending->telemere? true,
  :telemere-receiving? true},
 :slf4j
 {:present? true,
  :telemere-provider-present? true,
  :sending->telemere? true,
  :telemere-receiving? true},
 :open-telemetry {:present? false, :use-tracer? false},
 :system/out {:sending->telemere? true, :telemere-receiving? true},
 :system/err {:sending->telemere? true, :telemere-receiving? true}}
2025-04-11T23:07:02.309Z PENDANT INFO [user:30] - started:
	#'ua.util/util-state,
	#'ua.part5-schema/part5,
	#'ua.core/server
 nil
 ```
Since you haven't created a DB yet, do the following to create one.
Next time you start, you'll only have to do the first line of this:

```
(develop.repl/ns-setup!)          ; Provides namespace aliases.
(reset! p5s/recreate-db? true)    ; Allow recreation of a DB with only part 5 in it.
(p5s/init-part5)                  ; Create that database.
```
Expect the following output:

```
LOG/INFO  : - Creating a Part 5-based database named :part5.
LOG/INFO  : - Loading 5822 lookups.
LOG/INFO  : - Loaded 5820 lookups.
LOG/INFO  : - Loading 5822 nodes.
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-pushed-security-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-pushed-security-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-pub-sub-connection
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-pub-sub-connection
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/data-set-to-writer
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/data-set-to-writer
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-writer-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-reader-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-writer-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-reader-group
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-data-set-writer
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-data-set-writer
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-data-set-reader
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-data-set-reader
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-lower-layer-interface
LOG/INFO  : - IMPL/ref in predicate symbol position is :P5StdRefType/has-lower-layer-interface
LOG/INFO  : - Loaded 5822 nodes.
LOG/INFO  : - Loaded 5822 nodes.
{:part5-config #datahike/DB {:max-tx 536871089 :max-eid 30964}}
```

Now look for something in the DB:

```
(pprint (dbu/resolve-node "i=25345" :part5))

#:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
	   :type :UAReferenceType,
	   :references
	   [#:P5StdRefType{:subtype-of
					   #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
							  :type :UAReferenceType,
							  :references
							  [#:P5StdRefType{:subtype-of
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

Of course, things will get more interesting once we have an API!
Our next step is probably to provide one with GraphQL.
