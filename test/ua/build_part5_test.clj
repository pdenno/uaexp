(ns ua.build-part5-test
  (:require
   [clojure.data.xml   :as x]
   [clojure.edn        :as edn]
   [clojure.pprint     :refer [pprint]]
   [clojure.test       :refer [deftest is testing]]
   [develop.repl       :as dutil :refer [ns-setup!]]
   [ua.build-part5     :as bp5]
   [ua.xml-util        :as xu]
   [jsonista.core      :as json]))

(ns-setup!)

(def ^:diag p5-tags (atom #{}))

(defn ^:diag xml-tags
  "Return a set of the xml tags in the argument XML"
  [xml tag-atm]
  (letfn [(xt [obj]
            (when (map? obj) (doseq [[k v] (seq obj)]
                               (cond (= k :xml/tag) (swap! tag-atm  conj v)
                                     (= k :xml/content) (doseq [o v] (xt o))))))]
    (reset! tag-atm #{})
    (xt xml)
    @tag-atm))

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
      (is (= (bp5/rewrite-xml example-ua-variable)
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

(def x5 (-> "data/part5/OPC_UA_Core_Model_2515947497.xml" (xu/read-xml :root-name "p5")))
(def p5 (-> "data/part5/p5.edn" slurp edn/read-string))

(def ^:diag ref-types
  "Ref types indexed by their Node/id."
  (->> p5
       :NodeSet/content
       (filterv #(= (:Node/type %) :UAReferenceType))
       #_(reduce (fn [m v] (assoc m (:Node/id v) v)) {})))

(def ^:diag node-by-id
  (->> p5
       :NodeSet/content
       (filter #(contains? % :Node/id))
       (reduce (fn [m v] (assoc m (:Node/id v) v)) {})))

(def diag (atom nil))

(def ^:diag schema-info
  "This is useful if only to see how messed up p5.edn is.
   It helps to plan building schema. Some of which are okay right here."
  (as-> "data/part5/p5.edn" ?d
    (slurp ?d)
    (edn/read-string ?d)
    (bp5/learn-schema-basic ?d)
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

(def ^:diag schema-info
  "This is useful if only to see how messed up p5.edn is.
   It helps to plan building schema. Some of which are okay right here."
  (as-> "data/part5/p5.edn" ?d
    (slurp ?d)
    (edn/read-string ?d)
    (bp5/learn-schema-basic ?d)
    (group-by #(if (-> % :db/ident keyword?) (-> % :db/ident namespace) :other) ?d)
    (reduce-kv (fn [m k v]
                 (if (= k :other)
                   (assoc m k v)
                   (assoc m k (->> v (sort-by :db/ident) vec))))
               {}
               ?d)))






;;; (->> (with-out-str (pprint x5)) (spit "data/part5/x5.edn"))


;;; (get-attrs (-> x5 :xml/content first :xml/content rest vec))
(defn ^:diag get-attrs
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

(def ^:diag ua-attrs (-> "data/part3/table-mandatory-and-optional-attributes.txt" slurp  json/read-value))

(def ^:diag tiny-xml '[#:xml{:tag :p5/UANodeSet,
                             :attrs {:LastModified "2021-05-20T00:00:00Z"},
                             :content
                             [#:xml{:tag :p5/Aliases,
                                    :content
                                    [#:xml{:tag :p5/Alias, :attrs {:Alias "Boolean"}, :content "i=1"}
                                     #:xml{:tag :p5/Alias, :attrs {:Alias "HasDescription"}, :content "i=39"}]}]}])

(defn ^:diag toplevel-make-p5-edn! []
  (reset! bp5/parse-depth 0)
  (let [p5 (bp5/rewrite-xml (-> x5 :xml/content first) :p5/UANodeSet)
        s (with-out-str (pprint p5))]
    (spit "data/part5/p5.edn" s)))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn ^:diag parse-small
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

;;; ----------------------------------
(def ^:diag ref-attrs
  "A set of the attributes found in Part 5 ReferenceTypes."
  (let [slots (atom #{})]
    (doseq [x (->> p5 :NodeSet/content (filter #(= (:Node/type %) :UAReferenceType)))]
      (swap! slots into (keys x)))
    (-> slots deref sort vec)))

#_(def card-table
  (->> p5
       :NodeSet/content
       (filter #(= (:Node/type %) :UAReferenceType))
       (mapv (fn [ref-typ]
               (cond-> {}
                 true                           (assoc :name (:Node/display-name ref-typ))
                 true                           (assoc :cardinality (-> (get p5-card/ref-type-cardinality (:Node/display-name ref-typ)) :cardinality))
                 (:Node/inverse-name ref-typ)   (assoc :inverse-name (:Node/inverse-name ref-typ))
                 (:Node/inverse-name ref-typ)   (assoc :inverse-cardinality :db-cardinality/db-*))))
       (sort-by :name)))

;;; ----------- Stuff for developing bp5's ExtensionObject parsing -------------------------
(def ^:diag example
  #:xml{:tag :UATypes/ExtensionObject,
        :content
        [#:xml{:tag :UATypes/TypeId,
               :content
               [#:xml{:tag
                      :UATypes/Identifier,
                      :content "i=7616"}]}
         #:xml{:tag :UATypes/Body,
               :content
               [#:xml{:tag
                      :UATypes/EnumValueType,
                      :content
                      [#:xml{:tag
                             :UATypes/Value,
                             :content "1"}
                       #:xml{:tag
                             :UATypes/DisplayName,
                             :content
                             [#:xml{:tag
                                    :UATypes/Text,
                                    :content
                                    "Mandatory"}]}
                       #:xml{:tag
                             :UATypes/Description,
                             :content
                             [#:xml{:tag
                                    :UATypes/Text,
                                    :content
                                    "The BrowseName must appear in all instances of the type."}]}]}]}]})

(def ^:diag extobj-tags (atom #{}))
(defn ^:admin collect-exobj-tags ; A throw-away
  [obj]
  (letfn [(cet-aux [obj]
            (cond (and (map? obj) (contains? obj :xml/tag))      (do (swap! extobj-tags conj (:xml/tag obj))
                                                                     (doseq [[k v] obj] (when-not (= k :xml/tag) (cet-aux v))))
                  (map? obj)                                     (doseq [[k v] obj] (when-not (= k :xml/tag) (cet-aux v)))
                  (vector? obj)                                  (doseq [v obj] (cet-aux v))))]
    (cet-aux obj)))
