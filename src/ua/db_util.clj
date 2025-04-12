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

;;; (dbu/get-node-eid "i=25345" :part5)
(defn get-node-eid [i=  db-id] (d/q '[:find ?e  . :in $ ?id   :where [?e   :Node/id ?id]] @(connect-atm db-id) i=))
(defn get-node-i=  [eid db-id] (d/q '[:find ?id . :in $ ?eid  :where [?eid :Node/id ?id]] @(connect-atm db-id) eid))

(defn resolve-db-id
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  [form db-id & {:keys [keep-set drop-set]
                 :or {drop-set #{:db/id}
                      keep-set #{}}}]
  (let [conn @(connect-atm db-id)]
    (letfn [(resolve-aux [obj]
              (cond
                (db-ref? obj) (let [res (dp/pull conn '[*] (:db/id obj))]
                                (if (= res obj) nil (resolve-aux res)))
                (map? obj) (reduce-kv (fn [m k v]
                                        (cond (drop-set k)                                    m
                                              (and (not-empty keep-set) (not (keep-set k)))   m
                                              :else                                           (assoc m k (resolve-aux v))))
                                      {}
                                      obj)
                (vector? obj)      (mapv resolve-aux obj)
                (set? obj)    (set (mapv resolve-aux obj))
                (coll? obj)        (map  resolve-aux obj)
                :else  obj))]
      (resolve-aux form))))

(defn walk-final
  "Walk the structure, replacing :db/id with i=."
  [obj db-id]
  (letfn [(wf-aux [obj]
            (cond (db-ref? obj)     (get-node-i= (:db/id obj) db-id)
                  (map? obj)        (reduce-kv (fn [m k v] (assoc m k (wf-aux v))) {} obj)
                  (vector? obj)     (mapv wf-aux obj)
                  :else             obj))]
    (wf-aux obj)))

(defn deeper
  "Walk the structure, replacing :db/id maps with their one-step resolution."
  [obj db-id]
  (letfn [(d-aux [obj]
            (cond (db-ref? obj)   (resolve-db-id obj db-id)
                  (map? obj)      (reduce-kv (fn [m k v] (assoc m k (d-aux v))) {} obj)
                  (vector? obj)   (mapv d-aux obj)
                  :else           obj))]
    (d-aux obj)))

;;; ToDo: To test, find something that goes deeper. (dbu/resolve-node "i=25345" :part5 {:depth 3}) is the same as depth=1, I think.
(defn resolve-node
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  [i= db-id & {:keys [depth] :or {depth 1}}]
  (let [eid (get-node-eid i= db-id)]
    (loop [obj (resolve-db-id {:db/id eid} db-id)
           d depth]
      (if (zero? d)
        (walk-final obj db-id)
        (recur (deeper obj db-id)
               (dec d))))))
