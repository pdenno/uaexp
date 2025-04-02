(ns ua.core-test
  (:require
   [clojure.data.xml   :as x]
   [clojure.edn        :as edn]
   [clojure.pprint     :refer [pprint]]
   [clojure.test       :refer [deftest is testing]]
   [ua.core            :as core]
   [develop.dutil      :as dutil :refer [ns-setup!]]
   [ua.db-util         :as dbu]
   [jsonista.core      :as json]
   [ua.xml-util        :as xu :refer [read-xml]]))

(def p5-tags (atom #{}))
(def robot-tags (atom #{}))

(defn xml-tags
  "Return a set of the xml tags in the argument XML"
  [xml tag-atm]
  (letfn [(xt [obj]
            (when (map? obj) (doseq [[k v] (seq obj)]
                               (cond (= k :xml/tag) (swap! tag-atm  conj v)
                                     (= k :xml/content) (doseq [o v] (xt o))))))]
    (reset! tag-atm #{})
    (xt xml)
    @tag-atm))

(defn find-tags []
  (-> (read-xml "data/OPC_UA_Core_Model_2710599569.xml" {:root-name "p5"})
      (xml-tags p5-tags))
  (-> (read-xml "data/Opc.Ua.Robotics.NodeSet2.xml" {:root-name "p5"})
      (xml-tags robot-tags))
  {:p5 @p5-tags
   :robot @robot-tags})

(def robot (read-xml "data/Opc.Ua.Robotics.NodeSet2.xml" {:root-name "p5"}))

#_(defn tryme []
  (->> (read-xml #_"data/Opc.Ua.Robotics.NodeSet2.xml"
                 "data/tiny.xml"
                 {:root-name "p5"})
       #_:xml/content
       #_(mapv rewrite-xml)))

(def example-ua-variable
  '#:xml{:tag :p5/UAVariable,
         :attrs {:NodeId "ns=1;i=15740", :BrowseName "1:OperationalMode", :ParentNodeId "ns=1;i=15698", :DataType "ns=1;i=3006"},
         :content
         [#:xml{:tag :p5/DisplayName, :content "OperationalMode"}
          #:xml{:tag :p5/Description,
                :content
                "The OperationalMode variable provides information about the current operational mode. Allowed values are described in OperationalModeEnumeration, see ISO 10218-1:2011 Ch.5.7 Operational Modes."}
          #:xml{:tag :p5/References,
                :content
                [#:xml{:tag :p5/Reference, :attrs {:ReferenceType "HasTypeDefinition"}, :content "i=63"}
                 #:xml{:tag :p5/Reference, :attrs {:ReferenceType "HasModellingRule"}, :content "i=78"}
                 #:xml{:tag :p5/Reference,
                       :attrs {:ReferenceType "HasComponent", :IsForward "false"},
                       :content "ns=1;i=15698"}]}]})

(deftest parse-parts
  (testing "Testing parse of various small components"
    (testing "Testing parse of UAVariable"
      (is (= (core/rewrite-xml example-ua-variable)
             {:UAbase/NodeId "ns=1;i=15740",
              :UAbase/BrowseName "1:OperationalMode",
              :UAbase/ParentNodeId "ns=1;i=15698",
              :UAbase/DataType "ns=1;i=3006",
              :UAVariable/DisplayName "OperationalMode",
              :UAVariable/Description
              "The OperationalMode variable provides information about the current operational mode. Allowed values are described in OperationalModeEnumeration, see ISO 10218-1:2011 Ch.5.7 Operational Modes.",
              :UAVariable/References
              [{:UAbase/ReferenceType "HasTypeDefinition", :Reference/id "i=63"}
               {:UAbase/ReferenceType "HasModellingRule", :Reference/id "i=78"}
               {:UAbase/ReferenceType "HasComponent", :UAbase/IsForward false, :Reference/id "ns=1;i=15698"}]})))))

(def x5 (-> "data/part5/OPC_UA_Core_Model_2515947497.xml" (xu/read-xml :root-name "p5") #_(update :xml/content no-tags)))

;;; I had to do this in parts!
;;;(def p5 (-> "data/part5/p5.edn" slurp :edn/read-string))
(def ref-types
  "Ref types indexed by their Node/id."
  (->> p5
       :NodeSet/content
       (filterv #(= (:Node/type %) :UAReferenceType))
       #_(reduce (fn [m v] (assoc m (:Node/id v) v)) {})))

(def node-by-id
  (->> p5
       :NodeSet/content
       (filter #(contains? % :Node/id))
       (reduce (fn [m v] (assoc m (:Node/id v) v)) {})))

(def diag (atom nil))

(def schema-info
  "This is useful if only to see how messed up p5.edn is.
   It helps to plan building schema. Some of which are okay right here."
  (as-> "data/part5/p5.edn" ?d
    (slurp ?d)
    (edn/read-string ?d)
    (core/learn-schema ?d)
    (group-by #(if (-> % :db/ident keyword?) (-> % :db/ident namespace) :other) ?d)
    (reduce-kv (fn [m k v]
                 (assoc m k (->> v
                                 (sort (fn [x y]
                                         (cond (and (-> x :db/ident keyword?)
                                                    (-> y :db/ident keyword?))     (compare (:db/ident x) (:db/ident y))
                                               (and (-> x :db/ident map?)
                                                    (-> x :db/ident map?))         (compare (:IMPL/ref x) (:IMPL/ref y))
                                               :else (reset! diag [x y]))))
                                 vec)))
               {}
               ?d)))

(def schema-info
  "This is useful if only to see how messed up p5.edn is.
   It helps to plan building schema. Some of which are okay right here."
  (as-> "data/part5/p5.edn" ?d
    (slurp ?d)
    (edn/read-string ?d)
    (core/learn-schema ?d)
    (group-by #(if (-> % :db/ident keyword?) (-> % :db/ident namespace) :other) ?d)
    (reduce-kv (fn [m k v]
                 (if (= k :other)
                   (assoc m k v)
                   (assoc m k (->> v (sort-by :db/ident) vec))))
               {}
               ?d)))






;;; (->> (with-out-str (pprint x5)) (spit "data/part5/x5.edn"))


;;; (get-attrs (-> x5 :xml/content first :xml/content rest vec))
(defn get-attrs
  "Return a list of all the attribute names in the argument chunk of XML."
  [xml]
  (let [attrs-atm (atom #{})]
    (letfn [(geta [obj]
              (cond (map? obj)     (let [{:xml/keys [attrs content]} obj]
                                     (when attrs (swap! attrs-atm into (keys attrs)))
                                     (when content (geta content)))
                    (vector? obj)  (doseq [x obj] (geta x))))]
      (geta xml)
      (-> attrs-atm deref sort))))

(def ua-attrs (-> "data/part3/table-mandatory-and-optional-attributes.txt" slurp  json/read-value))

(def foo
  {"VariableType"
   {"MandatoryAttributes"
    ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "Value" "DataType" "ValueRank" "IsAbstract"],
    "OptionalAttributes" ["Description" "ArrayDimensions" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]},
   "View"
   {"MandatoryAttributes" ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "ContainsNoLoops" "EventNotifier"],
    "OptionalAttributes" ["Description" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]},
   "DataType"
   {"MandatoryAttributes" ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "IsAbstract"],
    "OptionalAttributes" ["Description" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]},
   "Object"
   {"MandatoryAttributes" ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "EventNotifier"],
    "OptionalAttributes" ["Description"]},
   "Method"
   {"MandatoryAttributes" ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "Executable" "UserExecutable"],
    "OptionalAttributes" ["Description" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]},
   "Variable"
   {"MandatoryAttributes"
    ["NodeId"
     "NodeClass"
     "BrowseName"
     "DisplayName"
     "WriteMask"
     "UserWriteMask"
     "Value"
     "DataType"
     "ValueRank"
     "AccessLevel"
   "UserAccessLevel"
     "Historizing"],
    "OptionalAttributes"
    ["Description"
     "ArrayDimensions"
     "MinimumSamplingInterval"
     "AccessRestrictions"
     "RolePermissions"
     "UserRolePermissions"
     "AccessLevelEx"
     "WriteMaskEx"
     "UserWriteMaskEx"]},
   "ObjectType"
   {"MandatoryAttributes" ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "IsAbstract"],
    "OptionalAttributes" ["Description" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]},
   "ReferenceType"
   {"MandatoryAttributes"
    ["NodeId" "NodeClass" "BrowseName" "DisplayName" "WriteMask" "UserWriteMask" "IsAbstract" "Symmetric" "InverseName"],
    "OptionalAttributes" ["Description" "RolePermissions" "UserRolePermissions" "AccessRestrictions"]}})

(defn cng-attr [obj]
  (cond (map? obj) (reduce-kv (fn [m k v] (cond (= k "MandatoryAttributes") (assoc m :mandatory (cng-attr v))
                                                (= k "OptionalAttributes") (assoc m :optional (cng-attr v))
                                                :else (assoc m k (cng-attr v))))
                              {} obj)
        (vector? obj)  (->> obj (mapv keyword) set)
        :else obj))


(defn tags&attrs
  "Return a map of all tags and elements."
  [xml]
  (let [res (atom {:tags #{} :attrs #{}})]
    (letfn [(t&a [x]
              (cond (map? x)       (doseq [[k v] x]
                                     (cond (= :xml/tag   k)  (swap! res #(update % :tags  conj v))
                                           (= :xml/attrs k)  (swap! res #(update % :attrs into (keys v))))
                                     (t&a v))
                    (vector? x)    (doseq [e x] (t&a e))))]
      (t&a xml))
    (-> res deref (update-vals #(->> % (sort-by name) vec)))))



(def tiny-xml '[#:xml{:tag :p5/UANodeSet,
                      :attrs {:LastModified "2021-05-20T00:00:00Z"},
                      :content
                      [#:xml{:tag :p5/Aliases,
                             :content
                             [#:xml{:tag :p5/Alias, :attrs {:Alias "Boolean"}, :content "i=1"}
                              #:xml{:tag :p5/Alias, :attrs {:Alias "HasDescription"}, :content "i=39"}]}]}])

;;; ToDo: After I demonstrate the simplest parsing working, consider transforming everything to tagged elements.
;;;       The problem is the same UA concept can be serialized either way in some cases.
(defn tryme []
  (reset! core/parse-depth 0)
  (let [p5 (core/rewrite-xml (-> x5 :xml/content first) :p5/UANodeSet)
        s (with-out-str (pprint p5))]
    (spit "data/part5/p5.edn" s)))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))


(defn parse-small
  "Wrap a string of XML in the namespace and try to parse it."
  [s]
  (let [small (str
               "<UANodeSet xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" LastModified=\"2025-01-08T00:00:00Z\" xmlns=\"http://opcfoundation.org/UA/2011/03/UANodeSet.xsd\">\n"
               s
               "</UANodeSet>\n")
        xml (-> small string->stream x/parse)]
    {:xml/ns-info (xu/update-xml-namespaces (x/element-nss xml) {:root-name "p5"
                                                                 :more-maps xu/more-maps})
     :xml/content (-> xml
                      (xu/alienate-xml {:root-name "p5"})
                      xu/clean-whitespace
                      xu/detagify
                      vector)}))
