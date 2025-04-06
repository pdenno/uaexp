(ns ua.db-util
  "Utilities for schema-db (which will likely become a library separate from rad-mapper"
  (:require
   [datahike.api          :as d]
   [taoensso.telemere     :as log :refer [log!]]))

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
  "Hitchhiker file-based DBs follow this form."
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
