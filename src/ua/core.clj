(ns ua.core
  "Toplevel of uaexp."
  (:require
   [clojure.data.xml            :as x]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.timbre             :as log]
   [ua.db-util                  :as dbu :refer [read-xml]]
   [ua.util                     :refer [util-state]])) ; For mount

(def debugging? (atom false))
(def diag (atom false))


(defmulti rewrite-xml #'su/rewrite-xsd-dispatch)

(defn call-this [arg]
  (reset! diag arg))

(defmethod rewrite-xml nil [obj & schema]
  (if schema
    (log/warn "No method for obj = " obj " schema = " schema)
    (log/warn "No method for obj = " obj))
  (call-this {:obj obj :schema schema})
  :failure/rewrite-xml-nil-method)

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

;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
