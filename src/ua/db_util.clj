(ns ua.db-util
  "Utilities for schema-db (which will likely become a library separate from rad-mapper"
  (:require
   [datahike.api          :as d]
   [datahike.pull-api     :as dp]
   [taoensso.telemere     :as log :refer [log!]]
   [ua.util               :as util :refer [util-state]]))   ; For mount

(defonce databases-atm (atom {}))

(defn register-db
  "Add a DB configuration."
  [k config]
  (log! :debug (str "Registering DB " k "config = " config))
  (swap! databases-atm #(assoc % k config)))

(defn deregister-db
  "Add a DB configuration."
  [k]
  (log! :info (str "Deregistering DB " k))
  (swap! databases-atm #(dissoc % k)))

(def db-template
  "Datahike file-based DBs follow this form."
  {:store {:backend :file :path "Provide a value!"} ; This is path to the database's root directory
   :keep-history? false
   :base-dir "Provide a value!"                     ; For convenience, this is just above the database's root directory.
   :schema-flexibility :write})

;;; https://cljdoc.org/d/io.replikativ/datahike/0.6.1545/doc/datahike-database-configuration
(defn db-cfg-map
  "Return a datahike configuration map for argument database (or its base).
     id   - a keyword uniquely identifying the DB in the scope of DBs.
     type - the type of DB configuration being make: (:project, :system, or :him, so far)"
  [{:keys [id in-mem?]}]
  (let [base-dir (or (-> (System/getenv) (get "UAEXP_DB"))
                     (throw (ex-info "Set the environment variable UAEXP_DB to the directory containing UA databases." {})))
        db-dir (str base-dir "/" (name id))]
    (cond-> db-template
      true            (assoc :base-dir base-dir)     ; This is not a datahike thing.
      (not in-mem?)   (assoc :store {:backend :file :path db-dir})
      in-mem?         (assoc :store {:backend :mem :id (name id)}))))

(defn connect-atm
  "Return a connection atom for the DB.
   Throw an error if the DB does not exist and :error? is true (default)."
  [k & {:keys [error?] :or {error? true}}]
  (if-let [db-cfg (get @databases-atm k)]
    (if (d/database-exists? db-cfg)
      (d/connect db-cfg)
      (when error?
        (throw (ex-info "No such DB" {:key k}))))
    (when error?
      (throw (ex-info "No such DB" {:key k})))))

(defn datahike-schema
  "Create a Datahike-compatible schema from schema+ style schema with notes such as those in uaexp namespace removed."
  [schema]
  (reduce-kv (fn [r k v]
               (conj r (-> (reduce-kv (fn [m kk vv] (if (= (namespace kk) "db") (assoc m kk vv) m)) {} v)
                           (assoc :db/ident k))))
             []
             schema))

(defn box
  "Wrap the argument (an atomic value) in a box.
   Note that unlike unbox, this only accepts atomic values."
  [obj]
  (cond (string?  obj) {:box_string-val  obj},
        (number?  obj) {:box_number-val  obj},
        (keyword? obj) {:box_keyword-val obj},
        (boolean? obj) {:box_boolean-val obj}))

(defn unbox
  "Walk through the form replacing boxed data with the data.
   In the reduce DB, for simplicity, all values are :db.type/ref."
  [data]
  (letfn [(box? [obj]
            (and (map? obj)
                 (#{:box_string-val :box_number-val :box_keyword-val :box_boolean-val}
                  (-> obj seq first first))))  ; There is just one key in a boxed object.
          (ub [obj]
            (if-let [box-typ (box? obj)]
              (box-typ obj)
              (cond (map? obj)      (reduce-kv (fn [m k v] (assoc m k (ub v))) {} obj)
                    (vector? obj)   (mapv ub obj)
                    :else           obj)))]
    (ub data)))
;;; ------------------------------------------ resolve-node ---------------------------------------------------------
(defn db-ref?
  "It looks to me that a datahike ref is a map with exactly one key: :db/id."
  [obj]
  (and (map? obj) (= [:db/id] (keys obj))))

;;; (get-node-eid "i=25345" :part5)
(defn get-node-eid [i=  db-id] (d/q '[:find ?e  . :in $ ?id   :where [?e   :Node/id ?id]] @(connect-atm db-id) i=))
(defn get-node-i=  [eid db-id] (d/q '[:find ?id . :in $ ?eid  :where [?eid :Node/id ?id]] @(connect-atm db-id) eid))

;;; I'd like not to need this; it makes a dangerous assumption!
#_(defn i=? [s] (and (string? s)
                   (or (re-matches #"^i=\d+$"          s)
                       (re-matches #"^ns=\d+;i=\d+$"   s)))) ; ToDo: right?

;;; This one gets called once to replace any remaining :db/id.
(defn ref-ref?
  "Returns true if the map is something like {:P5StdRefType/:subtype-of 'i=33'}."
  [obj]
  (and (map? obj)
       (== 1 (count obj))
       (= "P5StdRefType" (-> obj keys first namespace)))) ; ToDo Replace this with a test that the thing is a ReferenceType (maybe not std).

(defn pull-node-with-i=
  "Pull the object at i=<n>."
  [i= db-id]
  (-> (dp/pull @(connect-atm db-id) '[*] (get-node-eid i= db-id))
      (dissoc :db/id)))

(defn pull-node-with-eid
  "Pull the object at argument entity-id."
  [eid db-id]
  (assert (number? eid))
  (-> (dp/pull @(connect-atm db-id) '[*] eid)
      (dissoc :db/id)))

(defn pull-reference [obj db-id]
  (log! :info (str "pull-reference: obj = " obj))
  (cond (db-ref? obj) (-> (dp/pull @(connect-atm db-id) '[*] (:db/id obj))
                          (dissoc :db/id)
                          #_(dbid2i= db-id))
        ;; ToDo: This assumes the string is i=? true. Would be better were this to come in as a :db/id!
        (and (ref-ref? obj)
             (-> obj vals first db-ref?)) {(-> obj keys first) (-> obj vals first :db/id (pull-node-with-eid db-id))}
        (map? obj)     obj)) ; It is an expanded reference object.


;;; Next 3 could go back into resolve-node once they work!
;;; Use deeper and walk-final with the REPL to debug.
(defn walk-references
  [obj db-id]
  (cond (db-ref? obj)       (-> obj :db/id (get-node-i= db-id))
        (map? obj)          (reduce-kv (fn [m k v] (assoc m k (walk-references v db-id))) {} obj)
        (vector? obj)       (mapv #(walk-references % db-id) obj)
        :else               obj))

(defn walk-final
  [obj db-id]
  (cond (db-ref? obj)     (get-node-i= (:db/id obj) db-id)
        (map? obj)        (reduce-kv (fn [m k v]
                                       (if (= k :Node/references)
                                         (assoc m k (walk-references v db-id))
                                         (assoc m k (walk-final v db-id))))
                                     {}
                                     obj)
        (vector? obj)     (mapv #(walk-final % db-id) obj)
        :else             obj))

(defn deeper
  [obj db-id]
  (letfn [(deeper-ref [obj]
            (cond (db-ref? obj)          (-> obj :db/id (pull-node-with-eid db-id))
                  (map? obj)             (reduce-kv (fn [m k v] (assoc m k (deeper-ref v))) {} obj)
                  (vector? obj)          (mapv deeper-ref obj)
                  :else                  obj))]
    (cond (db-ref? obj)     obj
          (map? obj)        (reduce-kv (fn [m k v] (cond (= k :Node/id)           m
                                                         (= k :Node/references)   (assoc m k (mapv deeper-ref v))
                                                         :else                    (assoc m k (deeper v db-id))))
                                       {}
                                       obj)
          (vector? obj)     (mapv #(deeper % db-id) obj)
          :else             obj)))

;;; ToDo: Still broken (dbu/resolve-node "i=25345" :part5 {:depth 3}) fails.
;;;       But there's more wrong: I'd like not to use i=? true anywhere. pull-reference assumes it.
(defn resolve-node
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  [i= db-id & {:keys [depth] :or {depth 1}}]
  (letfn []
     (loop [obj (pull-node-with-i= i= db-id)
            d depth]
       (if (zero? d)
         (walk-final obj db-id)
         (recur (deeper obj db-id)
                (dec d))))))
