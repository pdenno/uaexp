(ns ua.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ua.core        :as core :refer [rewrite-xml]]
   [ua.db-util     :as dbu]
   [ua.xml-util    :as xu :refer [read-xml]]))

(def alias? (atom (-> (ns-aliases *ns*) keys set)))

(defn safe-alias
  [al ns-sym]
  (when (and (not (@alias? al))
             (find-ns ns-sym))
    (alias al ns-sym)))

(defn ^:diag ns-setup!
  "Use this to setup useful aliases for working in this NS."
  []
  (reset! alias? (-> (ns-aliases *ns*) keys set))
  (safe-alias 'io     'clojure.java.io)
  (safe-alias 's      'clojure.spec.alpha)
  (safe-alias 'uni    'clojure.core.unify)
  (safe-alias 'x      'clojure.data.xml)
  (safe-alias 'edn    'clojure.edn)
  (safe-alias 'io     'clojure.java.io)
  (safe-alias 'str    'clojure.string)
  (safe-alias 'd      'datahike.api)
  (safe-alias 'dp     'datahike.pull-api)
  (safe-alias 'mount  'mount.core)
  (safe-alias 'p      'promesa.core)
  (safe-alias 'px     'promesa.exec)
  (safe-alias 'core   'ua.core)
  (safe-alias 'dbu    'ua.db-util))

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


(defn tryme []
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
