(ns ua.part5-schema
  (:require
   [camel-snake-kebab.core      :as csk]
   [clojure.edn                 :as edn]
   [clojure.pprint              :refer [pprint]]
   [clojure.set                 :as set]
   [datahike.api                :as d]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.build-part5              :as build]
   [ua.db-util                  :as dbu :refer [connect-atm datahike-schema db-cfg-map register-db]]
   [ua.xml-util                 :as xu]))

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


   ;; --------------------------- P3LocalizedText
   :P3LocalizedText/locale
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}

   :P3LocalizedText/str
   #:db{:cardinality :db.cardinality/one, :valueType :db.type/string}


   ;; --------------------------- P5StdRefType
   :P5StdRefType/add-in-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17604",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.21",
    :uaexp/category "Address Space AddIn Reference"}

   :P5StdRefType/aggregated-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=44",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.5",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/aggregates
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=44",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.5",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/alarm-group-member
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=16362",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.5",
    :uaexp/category "A & C First in Group Alarm"}

   :P5StdRefType/alarm-suppression-group-member
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32059",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.6",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/alias-for
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=23469",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part17/8.2",
    :uaexp/category "AliasName Base"}

   :P5StdRefType/always-generated-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=3065",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.17",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/always-generates-event
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=3065",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.17",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/argument-description-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=129",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.18",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/associated-with
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=24137",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.24",
    :uaexp/category "Base Info AssociatedWith",
    :uaexp/symmetric? true}

   :P5StdRefType/attached-component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25264",
    :db/doc "lost?"
    :uaexp/category "Base Info HasAttachedComponent"}

   :P5StdRefType/can-execute
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25253",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.2.2",
    :uaexp/category "Base Info IsExecutableOn"}

   :P5StdRefType/child-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=34",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.4",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=47",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.7",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/contained-component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25263",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.13.2",
    :uaexp/category "Base Info HasContainedComponent"}

   :P5StdRefType/controls
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25254",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.4.2",
    :uaexp/category "Base Info Controls"}

   :P5StdRefType/data-set-to-writer
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=14936",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.4/#9.1.4.2.5",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/deprecates
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=23562",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.22",
    :uaexp/category "Base Info Deprecated Information"}

   :P5StdRefType/description-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=39",
    :uaexp/release-status "Deprecated"}

   :P5StdRefType/dictionary-entry-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=17597",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part19/6.1",
    :uaexp/category "Address Space Dictionary Entries"}

   :P5StdRefType/encoding-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=38",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.13",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/engineering-unit-details-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32558",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.1",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/event-source-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=36",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.14",
    :uaexp/category "Address Space Source Hierarchy"}

   :P5StdRefType/executes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25265",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.3.2",
    :uaexp/category "Base Info IsExecutingOn"}

   :P5StdRefType/from-state
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=51",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.11",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/from-transition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=52",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.12",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/generated-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=41",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.16",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/generates-event
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=41",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.16",
    :uaexp/category "Address Space Events 2"}

   :P5StdRefType/guard-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15112",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.6.3",
    :uaexp/category "Base Info Choice States"}

   :P5StdRefType/has-add-in
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17604",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.21",
    :uaexp/category "Address Space AddIn Reference"}

   :P5StdRefType/has-alarm-suppression-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=16361",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.4",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/has-alias
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=23469",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part17/8.2",
    :uaexp/category "AliasName Base"}

   :P5StdRefType/has-argument-description
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=129",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.18",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/has-attached-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25264",
    :db/doc "lost?"
    :uaexp/category "Base Info HasAttachedComponent"}

   :P5StdRefType/has-cause
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=53",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.13",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/has-child
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=34",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.4",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/has-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=47",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.7",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-condition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=9006",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.12",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/has-contained-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25263",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.13.2",
    :uaexp/category "Base Info HasContainedComponent"}

   :P5StdRefType/has-current-data
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32633",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.3",
    :uaexp/category "Historical Access HasCurrentData"}

   :P5StdRefType/has-current-event
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32634",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.4",
    :uaexp/category "Historical Access HasCurrentEvent"}

   :P5StdRefType/has-data-set-reader
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15297",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.12",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/has-data-set-writer
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15296",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/has-description
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=39",
    :uaexp/release-status "Deprecated"}

   :P5StdRefType/has-dictionary-entry
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=17597",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part19/6.1",
    :uaexp/category "Address Space Dictionary Entries"}

   :P5StdRefType/has-effect
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=54",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.14",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/has-effect-disable
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17276",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.2",
    :uaexp/category "A & C StateMachine Trigger"}

   :P5StdRefType/has-effect-enable
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17983",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.3",
    :uaexp/category "A & C Statemachine Trigger"}

   :P5StdRefType/has-effect-suppressed
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17984",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.4",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/has-effect-unsuppressed
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17985",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.5",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/has-encoding
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=38",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.13",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-engineering-unit-details
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32558",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.1",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/has-event-source
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=36",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.14",
    :uaexp/category "Address Space Source Hierarchy"}

   :P5StdRefType/has-false-sub-state
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=9005",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.3",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/has-guard
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=15112",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.6.3",
    :uaexp/category "Base Info Choice States"}

   :P5StdRefType/has-higher-layer-interface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25238",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.2",
    :uaexp/category "BNM IETF Interface Base Info"}

   :P5StdRefType/has-historical-configuration
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=56",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.2",
    :uaexp/category "Historical Access Events"}

   :P5StdRefType/has-historical-data
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=32633",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.3",
    :uaexp/category "Historical Access HasCurrentData"}

   :P5StdRefType/has-historical-event
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=32634",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.4",
    :uaexp/category "Historical Access HasCurrentEvent"}

   :P5StdRefType/has-interface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=17603",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.20",
    :uaexp/category "Address Space Interfaces"}

   :P5StdRefType/has-key-value-description
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32407",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.25",
    :uaexp/category "Base Info HasKeyValueDescription"}

   :P5StdRefType/has-lower-layer-interface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25238",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.2",
    :uaexp/category "BNM IETF Interface Base Info"}

   :P5StdRefType/has-modelling-rule
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=37",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.11",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-notifier
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=48",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.15",
    :uaexp/category "Address Space Notifier Hierarchy"}

   :P5StdRefType/has-optional-input-argument-description
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=131",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.19",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/has-ordered-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=49",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.8",
    :uaexp/category "Base Info HasOrderedComponent"}

   :P5StdRefType/has-physical-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25262",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.12.2",
    :uaexp/category "Base Info HasPhysicalComponent"}

   :P5StdRefType/has-property
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=46",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.9",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-pub-sub-connection
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=14476",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.3/#9.1.3.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/has-push-target
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25345",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
    :uaexp/category "PubSub Model SKS Push"}

   :P5StdRefType/has-pushed-security-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25345",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
    :uaexp/category "PubSub Model SKS Push"}

   :P5StdRefType/has-quantity
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32559",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.2",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/has-reader-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=18805",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.10",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/has-reference-description
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=32679"}

   :P5StdRefType/has-structured-component
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=24136",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.23",
    :uaexp/category "Base Info Subvariables of Structures"}

   :P5StdRefType/has-sub-state-machine
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=117",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.15",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/has-subtype
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=45",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.10",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-true-sub-state
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=9004",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.2",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/has-type-definition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=40",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.12",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/has-writer-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=18804",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.9",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/hierarchical-references
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=33",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/historical-configuration-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=56",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.2",
    :uaexp/category "Historical Access Events"}

   :P5StdRefType/hosts
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25261",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.11.2",
    :uaexp/category "Base Info IsHostedBy"}

   :P5StdRefType/interface-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=17603",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.20",
    :uaexp/category "Address Space Interfaces"}

   :P5StdRefType/inverse-hierarchical-references
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=33",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true}

   :P5StdRefType/is-alarm-suppression-group-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=16361",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.4",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/is-condition-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9006",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.12",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/is-controlled-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25254",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.4.2",
    :uaexp/category "Base Info Controls"}

   :P5StdRefType/is-deprecated
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=23562",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.22",
    :uaexp/category "Base Info Deprecated Information"}

   :P5StdRefType/is-executable-on
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25253",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.2.2",
    :uaexp/category "Base Info IsExecutableOn"}

   :P5StdRefType/is-executing-on
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25265",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.3.2",
    :uaexp/category "Base Info IsExecutingOn"}

   :P5StdRefType/is-false-sub-state-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9005",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.3",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/is-hosted-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25261",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.11.2",
    :uaexp/category "Base Info IsHostedBy"}

   :P5StdRefType/is-physically-connected-to
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25257",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.7.2",
    :uaexp/category "Base Info IsPhysicallyConnectedTo",
    :uaexp/symmetric? true}

   :P5StdRefType/is-reader-group-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=18805",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.10",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/is-reader-in-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15297",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.12",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/is-required-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25256",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.6.2",
    :uaexp/category "Base Info Requires"}

   :P5StdRefType/is-structured-component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=24136",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.23",
    :uaexp/category "Base Info Subvariables of Structures"}

   :P5StdRefType/is-true-sub-state-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=9004",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.2",
    :uaexp/category "A & C Basic"}

   :P5StdRefType/is-utilized-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25255",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.5.2",
    :uaexp/category "Base Info Utilizes"}

   :P5StdRefType/is-writer-group-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=18804",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.5/#9.1.5.9",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/is-writer-in-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=15296",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.6/#9.1.6.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/key-value-description-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32407",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.25",
    :uaexp/category "Base Info HasKeyValueDescription"}

   :P5StdRefType/may-be-caused-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=53",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.13",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/may-be-disabled-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17276",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.2",
    :uaexp/category "A & C StateMachine Trigger"}

   :P5StdRefType/may-be-effected-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=54",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.14",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/may-be-enabled-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17983",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.3",
    :uaexp/category "A & C Statemachine Trigger"}

   :P5StdRefType/may-be-suppressed-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17984",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.4",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/may-be-unsuppressed-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=17985",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/7.5",
    :uaexp/category "A & C Statemachine Suppression Trigger"}

   :P5StdRefType/member-of-alarm-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=16362",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.5",
    :uaexp/category "A & C First in Group Alarm"}

   :P5StdRefType/member-of-alarm-suppression-group
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32059",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part9/5.4.6",
    :uaexp/category "A & C Suppression Group"}

   :P5StdRefType/modelling-rule-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=37",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.11",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/non-hierarchical-references
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=32",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.3",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true,
    :uaexp/symmetric? true}

   :P5StdRefType/notifier-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=48",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.15",
    :uaexp/category "Address Space Notifier Hierarchy"}

   :P5StdRefType/optional-input-argument-description-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=131",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.19",
    :uaexp/category "Address Space Method Meta Data"}

   :P5StdRefType/ordered-component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=49",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.8",
    :uaexp/category "Base Info HasOrderedComponent"}

   :P5StdRefType/organized-by
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=35",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.6",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/organizes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=35",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.6",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/physical-component-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=25262",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.12.2",
    :uaexp/category "Base Info HasPhysicalComponent"}

   :P5StdRefType/property-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=46",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.9",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/pub-sub-connection-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=14476",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part14/9.1.3/#9.1.3.6",
    :uaexp/category "PubSub Model Base"}

   :P5StdRefType/quantity-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32559",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part8/6.5.2",
    :uaexp/category "Data Access Quantities Base"}

   :P5StdRefType/reference-description-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=32679"}

   :P5StdRefType/references
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=31",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.1",
    :uaexp/category "Base Info Base Types",
    :uaexp/is-abstract? true,
    :uaexp/symmetric? true}

   :P5StdRefType/represents-same-entity-as
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25258",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.8.2",
    :uaexp/category "Base Info RepresentsSameEntityAs",
    :uaexp/symmetric? true}

   :P5StdRefType/represents-same-functionality-as
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25260",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.10.2",
    :uaexp/category "Base Info RepresentsSameFunctionalityAs",
    :uaexp/symmetric? true}

   :P5StdRefType/represents-same-hardware-as
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25259",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.9.2",
    :uaexp/category "Base Info RepresentsSameHardwareAs",
    :uaexp/symmetric? true}

   :P5StdRefType/requires
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25256",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.6.2",
    :uaexp/category "Base Info Requires"}

   :P5StdRefType/sub-state-machine-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=117",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.15",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/subtype-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=45",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.10",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/to-state
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=52",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.12",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/to-transition
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=51",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part16/4.4.11",
    :uaexp/category "Base Info Finite State Machine Instance"}

   :P5StdRefType/type-definition-of
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/inverse? true,
    :uaexp/id "i=40",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.12",
    :uaexp/category "Base Info Base Types"}

   :P5StdRefType/used-by-network-interface
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/inverse? true,
    :uaexp/id "i=25237",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.1",
    :uaexp/category "BNM Priority Mapping 2"}

   :P5StdRefType/uses-priority-mapping-table
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/one,
    :uaexp/id "i=25237",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part22/5.6.1",
    :uaexp/category "BNM Priority Mapping 2"}

   :P5StdRefType/utilizes
   {:db/valueType :db.type/ref,
    :db/cardinality :db.cardinality/many,
    :uaexp/id "i=25255",
    :db/doc "https://reference.opcfoundation.org/v105/Core/docs/Part23/4.5.2",
    :uaexp/category "Base Info Utilizes"}

   :P5StdRefType/writer-to-data-set
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

(defn ^:admin write-p5-edn!
  []
  (reset! build/parse-depth 0)
  (let [x5 (-> "data/part5/OPC_UA_Core_Model_2515947497.xml" (xu/read-xml :root-name "p5"))
        p5 (build/rewrite-xml (-> x5 :xml/content first) :p5/UANodeSet)
        s (with-out-str (pprint p5))]
    (spit "data/part5/p5.edn" s)))

(def part5-schema (datahike-schema part5-schema+))
(def ^:diag diag (atom nil))

(defn merge-warn
  "Merge the argument schema+ with part5-schema+, warning where there are collisions."
  [schema+]
  (let [collisions (set/intersection (-> schema+ keys set) (-> part5-schema+ keys set))]
    (when (not-empty collisions)
      (log! :warn (str "The following are defined in Part5; their redefinition in the nodeset is being ignored: " collisions)))
    (-> schema+ (merge part5-schema+) datahike-schema)))

(defn collect-lookups
  [obj]
  (let [lookups (atom [])]
    (letfn [(cl [obj]
              (cond (map? obj)        (if (contains? obj :Node/id)
                                        (let [{:Node/keys [id browse-name]} obj]
                                          (when-not (string? browse-name)  (throw (ex-info "No browse-name" {:obj obj})))
                                          (swap! lookups conj {:Node/id id :Node/browse-name browse-name}))
                                        (doseq [[_ v] obj] (cl v)))
                    (vector? obj)     (doseq [x obj] (cl x))))]
      (cl obj)
      @lookups)))

;;; (p5s/load-lookups! :part5 p5)
(defn load-lookups! [db-id nodeset] ; ToDo: Chunk these! (Seems you can't do all of them at once.
  (assert (contains? nodeset :NodeSet/content))
  (let [content (:NodeSet/content nodeset)
        cnt (atom 0)]
    (log! (str "Loading " (count content) " lookups."))
    (loop [lookups (collect-lookups content)]
      (let [[these others] (split-at 100 lookups)]
        (when (not-empty these)
          (swap! cnt #(+ % (count these)))
          (d/transact (connect-atm db-id) {:tx-data (vec these)})
          (recur others))))
    (log! :info (str "Loaded " @cnt " lookups."))))

(defn node-by-i=
  "Return the node object having :Node/id = i=. (i= is a string; I know it's sick!)"
  [i= nodeset]
  (some #(when (= i= (:Node/id %)) %) (:NodeSet/content nodeset)))


(defn impl-ref-pred-symbol
  "A UA Reference is a map with just one entry. The key of this refers to a 'predicate symbol' and the value is a {:IMPL/ref <i=n>}
   It is permitted that the 'predicate symbol' position of a UA Reference is  also a {:IMPL/ref <i=n>}.
   In this case, the {:IMPL/ref <i=n>} should point to a UAReferenceType. When we find these, we
   return (keyword 'P5StdRefType' (-> {:IMPL/ref <i=n>} lookup-node :Node/display-name sck/->kebab-case))."
  [i= nodeset]
  (let [node (node-by-i= i= nodeset)]
    (if (= :UAReferenceType (:Node/type node))
      (keyword "P5StdRefType" (-> node :Node/display-name csk/->kebab-case))
      (throw (ex-info "Could not resolve predicate symbol." {:i= i=, :node node})))))

(def nodeset-memo (atom nil))

(defn resolve-node-ids
  [node db-id]
  (letfn [(key-check [k]
            (if (map? k)
              (if (contains? k :IMPL/ref)
                (let [pred-symbol (impl-ref-pred-symbol (:IMPL/ref k) @nodeset-memo)]
                  (log! :info (str "IMPL/ref in predicate symbol position is " pred-symbol))
                  pred-symbol)
                (log! :warn (str "This map should have an IMPL/ref: " k)))
              k))
          (lookup-ref [i=]
            (or (d/q '[:find ?e . :in $ ?id :where [?e :Node/id ?id]] @(connect-atm db-id) i=)
                (throw (ex-info "No DB entry for index:" {:i= i=}))))
          (rni [obj]
            (cond (and (map? obj) (contains? obj :IMPL/ref))  {:db/id (lookup-ref (:IMPL/ref obj))}
                  (map? obj)                                  (reduce-kv (fn [m k v] (assoc m (key-check k) (rni v))) {} obj)
                  (vector? obj)                               (mapv rni obj)
                  :else                                       obj))]
    (rni node)))

(defn load-nodeset!
  "Read the part5 edn into the DB. This is two-staged, wherein the first stage creates lookups,
   and the second stage loads the full object."
  [db-id nodeset]
  (reset! nodeset-memo nodeset)
  (load-lookups! db-id nodeset)
  (log! :info (str "Loading " (-> nodeset :NodeSet/content count) " nodes."))
  (let [cnt (atom 0)]
    (loop [nodes (:NodeSet/content  nodeset)]
      (let [[these others] (split-at 50 nodes)]
        (when (not-empty these)
          (swap! cnt #(+ % (count these)))
          (d/transact (connect-atm db-id) {:tx-data (mapv #(resolve-node-ids % db-id)  these)})
          (recur others))))
    (log! :info (str "Loaded " @cnt " nodes."))))

(defn ^:admin create-ua-db!
  "Create a part5 database from an EDN file. Every UA DB would start with this.
   If schema is provided it is merged with the part5 schema."
  [& {:keys [schema nodeset db-id] :or {schema {} db-id :part5}}]
  (log! :info (str "Creating a Part 5-based database named " db-id "."))
  (if (get (System/getenv) "UAEXP_DB")
    (let [schema (if schema (merge-warn schema) part5-schema)
          cfg (db-cfg-map {:id db-id})]
      (when (d/database-exists? cfg) (d/delete-database cfg))
      (d/create-database cfg)
      (register-db db-id cfg)
      (let [conn (connect-atm db-id)]
        (d/transact conn schema)
        (load-nodeset! db-id nodeset)
        cfg))
    (log! :error "You have to set the environment variable UAEXP_DB to a directory.")))

(defonce recreate-db? (atom false))

;;; ----------------------- Start and stop ----------------------------------------
(defn init-part5
  "Register DBs (currently just a part5-only DB), loading if DB does not exist and recreate-db? (above) is true."
  []
  (let [cfg (db-cfg-map {:id :part5 :type :ua-base})]
    (register-db :part5 cfg)
    (when @recreate-db?
      (when (d/database-exists? cfg) (d/delete-database cfg))
      (create-ua-db! {:nodeset (-> "data/part5/p5.edn" slurp edn/read-string)})))
  {:part5-config @(connect-atm :part5)})

(defstate part5
  :start (init-part5))
