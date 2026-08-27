(ns ua.part5-schema
  "Definition of the Part 5 schema.
   This, and probably not the generator code, will be kept up to date."
  (:require
   [ua.db-util                  :as dbu :refer [datahike-schema]]))

(def part5-schema+
  "Schema for Part 5 created Sat Apr 05 21:15:30 EDT 2025.
   This, and probably not the generator code, will be kept up to date.
   It can be modified here."
  {
   ;; --------------------------- Alias
   :Alias/name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Alias/node-id
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- Definition
   :Definition/fields
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :Definition/name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- Field
   :Field/allow-sub-types?
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/boolean}

   :Field/data-type
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Field/description
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Field/name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Field/value
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Field/value-rank
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- Model
   :Model/model-version
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Model/publication-date
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Model/uri
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Model/version
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Model/xml-schema-uri
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- Node
   :Node/access-level
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/access-restictions
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/array-dimensions
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/number}

   :Node/browse-name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/category
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/data-type
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/description
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/display-name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/documentation
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/event-notifier
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/id
   #:db{:cardinality :db.cardinality/one,
        :valueType :db.type/string,
        :unique :db.unique/identity}

   ;;; :Node/namespace-uri and :Node/local-id are uaexp's, not the standard's. :Node/id is
   ;;; canonicalized to "<namespace-uri>;<local-id>" (see ua.nsuri) because a NodeId's ns=<index>
   ;;; is file-local and collides across nodesets. These two hold the parts, separately queryable.
   :Node/namespace-uri
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/local-id
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/inverse-name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/is-abstract?
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/boolean}

   :Node/method-declaration-id
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/parent-node-id
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/purpose
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/references
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :Node/release-status
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/role-permissions
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :Node/symbolic-name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :Node/symmetric?
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/boolean}

   :Node/type
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/keyword}

   :Node/value
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :Node/value-rank
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/number}


   ;; --------------------------- NodeSet
   :NodeSet/aliases
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :NodeSet/content
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :NodeSet/models
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   ;;; The nodeset's <NamespaceUris>, kept as provenance: it is what ua.nsuri used to canonicalize
   ;;; this nodeset's NodeIds, so it records how the file's ns=<index> were read.
   :NodeSet/namespace-uris
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/string}


   ;; --------------------------- P3LocalizedText
   :P3LocalizedText/locale
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :P3LocalizedText/str
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- P3QualifiedName
   ;; The namespace index here is the one written in the file that carried the value; it is
   ;; resolved to a URI at load, for the same reason NodeIds are. (See ua.nsuri.)
   :P3QualifiedName/name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :P3QualifiedName/namespace-uri
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- P5StdRefType
   :P5StdRefType/AddInOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17604",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.21",
    :uaexp/category "Address Space AddIn Reference"}

   :P5StdRefType/AggregatedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=44",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.5",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/Aggregates
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=44",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.5",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/AlarmGroupMember
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=16362",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.5",
    :uaexp/category "A & C First in Group Alarm"}

   :P5StdRefType/AlarmSuppressionGroupMember
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32059",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.6",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/AliasFor
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=23469",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part17/8.2",
    :uaexp/category "AliasName Base"}

   :P5StdRefType/AlwaysGeneratedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=3065",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.17",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/AlwaysGeneratesEvent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=3065",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.17",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/ArgumentDescriptionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=129",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.18",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/AssociatedWith
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=24137",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.24",
    :uaexp/category "Base Info AssociatedWith",
    :uaexp/symmetric? true}

   :P5StdRefType/AttachedComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25264",
    :db/doc "lost?"
    :uaexp/category "Base Info HasAttachedComponent"}

   :P5StdRefType/CanExecute
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25253",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.2.2",
    :uaexp/category "Base Info IsExecutableOn"}

   :P5StdRefType/ChildOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=34",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.4",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/ComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=47",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.7",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/ContainedComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25263",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.13.2",
    :uaexp/category "Base Info HasContainedComponent"}

   :P5StdRefType/Controls
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25254",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.4.2",
    :uaexp/category "Base Info Controls"}

   :P5StdRefType/DataSetToWriter
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=14936",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.4/#9.1.4.2.5",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/Deprecates
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=23562",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.22",
    :uaexp/category "Base Info Deprecated Information"}

   :P5StdRefType/DescriptionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=39",
    :uaexp/release-status "Deprecated"}

   :P5StdRefType/DictionaryEntryOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=17597",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part19/6.1",
    :uaexp/category "Address Space Dictionary Entries"}

   :P5StdRefType/EncodingOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=38",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.13",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/EngineeringUnitDetailsOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32558",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.1",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/EventSourceOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=36",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.14",
    :uaexp/category "Address Space Source Hierarchy"}

   :P5StdRefType/Executes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25265",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.3.2",
    :uaexp/category "Base Info IsExecutingOn"}

   :P5StdRefType/FromState
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=51",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.11",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/FromTransition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=52",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.12",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/GeneratedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=41",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.16",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/GeneratesEvent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=41",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.16",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/GuardOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15112",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.6.3",
    :uaexp/category "Base Info Choice States"}

   :P5StdRefType/HasAddIn
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17604",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.21",
    :uaexp/category "Address Space AddIn Reference"}

   :P5StdRefType/HasAlarmSuppressionGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=16361",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.4",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/HasAlias
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=23469",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part17/8.2",
    :uaexp/category "AliasName Base"}

   :P5StdRefType/HasArgumentDescription
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=129",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.18",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/HasAttachedComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25264",
    :db/doc "lost?"
    :uaexp/category "Base Info HasAttachedComponent"}

   :P5StdRefType/HasCause
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=53",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.13",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/HasChild
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=34",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.4",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/HasComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=47",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.7",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasCondition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=9006",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.12",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/HasContainedComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25263",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.13.2",
    :uaexp/category "Base Info HasContainedComponent"}

   :P5StdRefType/HasCurrentData
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32633",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.3",
    :uaexp/category "Historical Access HasCurrentData"}

   :P5StdRefType/HasCurrentEvent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32634",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.4",
    :uaexp/category "Historical Access HasCurrentEvent"}

   :P5StdRefType/HasDataSetReader
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15297",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.12",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/HasDataSetWriter
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15296",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/HasDescription
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=39",
    :uaexp/release-status "Deprecated"}

   :P5StdRefType/HasDictionaryEntry
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=17597",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part19/6.1",
    :uaexp/category "Address Space Dictionary Entries"}

   :P5StdRefType/HasEffect
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=54",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.14",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/HasEffectDisable
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17276",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.2",
    :uaexp/category "A & C StateMachine Trigger"}

   :P5StdRefType/HasEffectEnable
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17983",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.3",
    :uaexp/category "A & C Statemachine Trigger"}

   :P5StdRefType/HasEffectSuppressed
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17984",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.4",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/HasEffectUnsuppressed
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17985",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.5",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/HasEncoding
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=38",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.13",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasEngineeringUnitDetails
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32558",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.1",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/HasEventSource
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=36",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.14",
    :uaexp/category "Address Space Source Hierarchy"}

   :P5StdRefType/HasFalseSubState
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=9005",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.3",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/HasGuard
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15112",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.6.3",
    :uaexp/category "Base Info Choice States"}

   :P5StdRefType/HasHigherLayerInterface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25238",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.2",
    :uaexp/category "BNM IETF Interface Base Info"}

   :P5StdRefType/HasHistoricalConfiguration
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=56",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.2",
    :uaexp/category "Historical Access Events"}

   :P5StdRefType/HasHistoricalData
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=32633",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.3",
    :uaexp/category "Historical Access HasCurrentData"}

   :P5StdRefType/HasHistoricalEvent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=32634",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.4",
    :uaexp/category "Historical Access HasCurrentEvent"}

   :P5StdRefType/HasInterface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17603",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.20",
    :uaexp/category "Address Space Interfaces"}

   :P5StdRefType/HasKeyValueDescription
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32407",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.25",
    :uaexp/category "Base Info HasKeyValueDescription"}

   :P5StdRefType/HasLowerLayerInterface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25238",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.2",
    :uaexp/category "BNM IETF Interface Base Info"}

   :P5StdRefType/HasModellingRule
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=37",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.11",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasNotifier
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=48",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.15",
    :uaexp/category "Address Space Notifier Hierarchy"}

   :P5StdRefType/HasOptionalInputArgumentDescription
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=131",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.19",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/HasOrderedComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=49",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.8",
    :uaexp/category "Base Info HasOrderedComponent"}

   :P5StdRefType/HasPhysicalComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25262",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.12.2",
    :uaexp/category "Base Info HasPhysicalComponent"}

   :P5StdRefType/HasProperty
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=46",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.9",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasPubSubConnection
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=14476",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.3/#9.1.3.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/HasPushTarget
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25345",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
    :uaexp/category "PubSub Model SKS Push"}

   :P5StdRefType/HasPushedSecurityGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25345",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
    :uaexp/category "PubSub Model SKS Push"}

   :P5StdRefType/HasQuantity
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32559",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.2",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/HasReaderGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=18805",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.10",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/HasReferenceDescription
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32679"}

   :P5StdRefType/HasStructuredComponent
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=24136",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.23",
    :uaexp/category "Base Info Subvariables of Structures"}

   :P5StdRefType/HasSubStateMachine
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=117",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.15",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/HasSubtype
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=45",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.10",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasTrueSubState
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=9004",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.2",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/HasTypeDefinition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=40",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.12",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/HasWriterGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=18804",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.9",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/HierarchicalReferences
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=33",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/HistoricalConfigurationOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=56",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.2",
    :uaexp/category "Historical Access Events"}

   :P5StdRefType/Hosts
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25261",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.11.2",
    :uaexp/category "Base Info IsHostedBy"}

   :P5StdRefType/InterfaceOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=17603",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.20",
    :uaexp/category "Address Space Interfaces"}

   :P5StdRefType/InverseHierarchicalReferences
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=33",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/IsAlarmSuppressionGroupOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=16361",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.4",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/IsConditionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9006",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.12",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/IsControlledBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25254",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.4.2",
    :uaexp/category "Base Info Controls"}

   :P5StdRefType/IsDeprecated
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=23562",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.22",
    :uaexp/category "Base Info Deprecated Information"}

   :P5StdRefType/IsExecutableOn
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25253",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.2.2",
    :uaexp/category "Base Info IsExecutableOn"}

   :P5StdRefType/IsExecutingOn
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25265",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.3.2",
    :uaexp/category "Base Info IsExecutingOn"}

   :P5StdRefType/IsFalseSubStateOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9005",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.3",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/IsHostedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25261",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.11.2",
    :uaexp/category "Base Info IsHostedBy"}

   :P5StdRefType/IsPhysicallyConnectedTo
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25257",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.7.2",
    :uaexp/category "Base Info IsPhysicallyConnectedTo",
    :uaexp/symmetric? true}

   :P5StdRefType/IsReaderGroupOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=18805",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.10",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/IsReaderInGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15297",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.12",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/IsRequiredBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25256",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.6.2",
    :uaexp/category "Base Info Requires"}

   :P5StdRefType/IsStructuredComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=24136",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.23",
    :uaexp/category "Base Info Subvariables of Structures"}

   :P5StdRefType/IsTrueSubStateOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9004",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.2",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/IsUtilizedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25255",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.5.2",
    :uaexp/category "Base Info Utilizes"}

   :P5StdRefType/IsWriterGroupOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=18804",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.9",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/IsWriterInGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15296",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/KeyValueDescriptionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32407",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.25",
    :uaexp/category "Base Info HasKeyValueDescription"}

   :P5StdRefType/MayBeCausedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=53",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.13",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/MayBeDisabledBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17276",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.2",
    :uaexp/category "A & C StateMachine Trigger"}

   :P5StdRefType/MayBeEffectedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=54",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.14",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/MayBeEnabledBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17983",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.3",
    :uaexp/category "A & C Statemachine Trigger"}

   :P5StdRefType/MayBeSuppressedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17984",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.4",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/MayBeUnsuppressedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17985",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.5",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/MemberOfAlarmGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=16362",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.5",
    :uaexp/category "A & C First in Group Alarm"}

   :P5StdRefType/MemberOfAlarmSuppressionGroup
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32059",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.6",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/ModellingRuleOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=37",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.11",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/NonHierarchicalReferences
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.3",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true,
    :uaexp/symmetric? true}

   :P5StdRefType/NotifierOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=48",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.15",
    :uaexp/category "Address Space Notifier Hierarchy"}

   :P5StdRefType/OptionalInputArgumentDescriptionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=131",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.19",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/OrderedComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=49",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.8",
    :uaexp/category "Base Info HasOrderedComponent"}

   :P5StdRefType/OrganizedBy
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=35",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.6",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/Organizes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=35",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.6",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/PhysicalComponentOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25262",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.12.2",
    :uaexp/category "Base Info HasPhysicalComponent"}

   :P5StdRefType/PropertyOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=46",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.9",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/PubSubConnectionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=14476",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.3/#9.1.3.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/QuantityOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32559",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.2",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/ReferenceDescriptionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32679"}

   :P5StdRefType/References
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=31",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.1",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true,
    :uaexp/symmetric? true}

   :P5StdRefType/RepresentsSameEntityAs
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25258",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.8.2",
    :uaexp/category "Base Info RepresentsSameEntityAs",
    :uaexp/symmetric? true}

   :P5StdRefType/RepresentsSameFunctionalityAs
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25260",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.10.2",
    :uaexp/category "Base Info RepresentsSameFunctionalityAs",
    :uaexp/symmetric? true}

   :P5StdRefType/RepresentsSameHardwareAs
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25259",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.9.2",
    :uaexp/category "Base Info RepresentsSameHardwareAs",
    :uaexp/symmetric? true}

   :P5StdRefType/Requires
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25256",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.6.2",
    :uaexp/category "Base Info Requires"}

   :P5StdRefType/SubStateMachineOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=117",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.15",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/SubtypeOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=45",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.10",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/ToState
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=52",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.12",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/ToTransition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=51",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.11",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/TypeDefinitionOf
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=40",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.12",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/UsedByNetworkInterface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25237",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.1",
    :uaexp/category "BNM Priority Mapping 2"}

   :P5StdRefType/UsesPriorityMappingTable
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25237",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.1",
    :uaexp/category "BNM Priority Mapping 2"}

   :P5StdRefType/Utilizes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25255",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.5.2",
    :uaexp/category "Base Info Utilizes"}

   :P5StdRefType/WriterToDataSet
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=14936",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.4/#9.1.4.2.5",
    :uaexp/category "PubSub Model Base"}


   ;; --------------------------- P6ByteString
   :P6ByteString/str
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- RolePerm
   :RolePerm/permissions
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :RolePerm/ref
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   ;; --------------------------- UATypes
   :UATypes/ArrayDimensions
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/number}

   :UATypes/Body
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/DataType
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/Description
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/DisplayName
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/EUInformation
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/EnumValueType
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/ExtensionObject
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/Locale
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :UATypes/Name
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :UATypes/NamespaceUri
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :UATypes/Text
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :UATypes/TypeId
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/ref}

   :UATypes/UnitId
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :UATypes/Value
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/number}

   :UATypes/ValueRank
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/number}

   ;; --------------------------- box
   :box/boolean
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/boolean}

   :box/date-time
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/instant}

   :box/mix
   #:db{:cardinality :db.cardinality/many, :valueType :db.type/ref}

   :box/number
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/number}

   :box/string
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}
   })

(def part5-schema (datahike-schema part5-schema+))
