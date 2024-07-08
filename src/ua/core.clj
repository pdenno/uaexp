(ns ua.core
  "Toplevel of uaexp."
  (:require
   [clojure.data.xml            :as x]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.timbre             :as log]
   [ua.db-util                  :as dbu]
   [ua.xml-util                 :as xu :refer [read-xml]]
   [ua.util                     :refer [util-state]])) ; For mount

(def debugging? (atom false))
(def diag (atom false))

(def nyi (atom #{}))

(defn rewrite-xml-dispatch
  [obj & [specified]]
  (cond ;; Optional 2nd argument specifies method to call
    (keyword? specified)                        specified,
    (and (map? obj) (contains? obj :xml/tag))   (:xml/tag obj)
    :else (throw (ex-info "No method for obj: " {:obj obj}))))

(defmulti rewrite-xml #'rewrite-xml-dispatch)

(defn call-this [arg]
  (reset! diag arg))

(defmethod rewrite-xml nil [obj]
  (log/warn "No method for obj = " obj)
  (call-this {:obj obj})
  :failure/rewrite-xml-nil-method)

(defmethod rewrite-xml :default [obj]
  (log/warn "No method using default = " (:xml/tag obj))
  (swap! nyi conj (:xml/tag obj))
  nil)

(defn process-attrs-map
  [attrs-map]
  (reduce-kv (fn [res k v] (-> res (conj (name k)) (conj (str v)))) [] attrs-map))

;;; ToDo: I think it is pretty odd that we call process-attrs-map here, especially so because
;;;       sometimes specific attrs are mapped again, differently.
(defmacro defparse [tag [arg _props] & body]
  `(defmethod rewrite-xml ~tag [~arg & ~'_]
     ;; Once *skip-doc-processing?* is true, it stays so through the dynamic scope of the where it was set.
     (when @debugging? (println "defparse tag = " ~tag))
       (let [result# (do ~@body)]
         (cond-> result#
           (:xml/attrs result#) (-> (assoc :xml/attributes (-> result# :xml/attrs process-attrs-map))
                                    (dissoc :xml/attrs))))))

 '[#:xml{:tag :p5/UANodeSet,
        :attrs {:LastModified "2021-05-20T00:00:00Z"},
        :content
        [#:xml{:tag :p5/Aliases,
               :content
               [#:xml{:tag :p5/Alias, :attrs {:Alias "Boolean"}, :content "i=1"}
                #:xml{:tag :p5/Alias, :attrs {:Alias "HasDescription"}, :content "i=39"}]}]}]

(defparse :p5/UANodeSet
  [xmap]
  (->> xmap :xml/content (mapv rewrite-xml)))

(defparse :p5/Aliases
  [xmap]
  (log/info "xmap =" xmap)
  (->> xmap :xml/content (mapv rewrite-xml)))

(defparse :p5/Alias
  [xmap]
  (log/info "xmap =" xmap)
  {:alias/name (-> xmap :xml/attrs :Alias)
   :alias/id (:xml/content xmap)})


(defn common-ua-attrs
  [xmap]
  "Return a map of common UA attributes for the XML object."
  (let [{:keys [NodeId BrowseName ParentNodeId DataType ReferenceType IsForward]} (:xml/attrs xmap)]
    (cond-> {}
      NodeId             (assoc :UAbase/NodeId NodeId)
      BrowseName         (assoc :UAbase/BrowseName BrowseName)
      ParentNodeId       (assoc :UAbase/ParentNodeId ParentNodeId)
      DataType           (assoc :UAbase/DataType DataType)
      ReferenceType      (assoc :UAbase/ReferenceType ReferenceType)
      IsForward          (assoc :UAbase/IsForward (if (= "false" IsForward) false true)))))

(defparse :p5/Reference
  [xmap]
  (-> (common-ua-attrs xmap)
      (assoc :Reference/id (:xml/content xmap))))

(defparse :p5/UAVariable
  [xmap]
  (let [{:keys [DisplayName Description References]}
        (xu/xml-group-by xmap :p5/References :p5/DisplayName :p5/Description)]
    (cond-> (common-ua-attrs xmap)
      DisplayName  (assoc :UAVariable/DisplayName (->> DisplayName first :xml/content))
      Description  (assoc :UAVariable/Description (->> Description first :xml/content))
      References   (assoc :UAVariable/References  (->> References  first :xml/content (mapv rewrite-xml)))))) ; ToDo: Not sure about first here (and maybe above either)
                                                                                                              ; Is xml-group-by doing what I want? (I think they have to be vectors).

;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
