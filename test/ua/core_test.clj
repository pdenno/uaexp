(ns ua.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ua.core        :as core]
   [ua.db-util     :as dbu :refer [read-xml]]))

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

;;; Most others didn't have .xsd
(def more-ns-maps
  {:p->u
   {"uaTypes" "http://opcfoundation.org/UA/2008/02/Types.xsd"}
   :u->ps
   {"http://opcfoundation.org/UA/2008/02/Types.xsd" ["uaTypes"]}})

(defn tryme []
  (-> (read-xml "data/OPC_UA_Core_Model_2710599569.xml" {:root-name "p5"})
      (xml-tags p5-tags))
  (-> (read-xml "data/Opc.Ua.Robotics.NodeSet2.xml" {:root-name "p5"})
      (xml-tags robot-tags))
  {:p5 @p5-tags
   :robot @robot-tags})
