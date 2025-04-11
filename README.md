# uaexp
Exploratory software implementing an OPC UA server for use with CESMII architecture.
The sofware is written Clojure and uses Datahike, a graph database with Datalog-native access.
The code only represents about two-weeks of work, but it provides a basic DB with Part 5 in it.
We'd like to implement graphQL and the the upcoming CESMII API.

The software is being developed as part of the NIST project [Human/AI Teaming for Manufacturing Digital Twins](https://www.nist.gov/programs-projects/humanmachine-teaming-manufacturing-digital-twins).
Feel free to contact us if this work interests you!

## Building/Running (development mode)
   These instructions have not been thoroughly tested and are likely not complete. If you have problems, write an issue or email us (see the NIST project page above).

### Set up environment variables
  * In your .bashrc file, define an environment variable  `export UAEXP_DB=/opt/uaexp` (or wherever you intend to store databases for the application).

### The server
  * Install install a Java JDK and [Clojure](https://clojure.org/).
  * The following assumes you are trying things from a shell script, but, of course, there is quite likely a nicer arrangement available in your IDE.
  * BTW, this has only been tested on linux.
  * From a shell prompt in the src directory of this repository:

 ```
 clj -M:dev:test

 ;;; Currently there will be some warnings from SLF4J, then the prompt. Type:
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
Since you haven't created a DB yet, do the following to create one:

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
```

Now look for something in the DB:

```
(dbu/resolve-node "i=25345" :part5)
```

Of course, things will get more interesting once we have an API!
Our next step is probably to provide one with graphQL.
