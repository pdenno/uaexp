(ns ua.db-util
  "Utilities for schema-db (which will likely become a library separate from rad-mapper"
  (:require
   [datahike.api          :as d]
   [datahike.pull-api     :as dp]
   [taoensso.telemere     :as log :refer [log!]]
   [ua.nsuri              :as nsuri]
   [ua.util               :as util :refer [util-state]]))   ; For mount

;;; Registered stores, keyed by {:prefix <namespace uri> :version <version string>}.
;;; One store holds one version of one namespace, which is what makes an ExpandedNodeId enough to
;;; find a node: the namespace names the store, and the identifier names the node within it.
(defonce databases-atm (atom {}))

(defn store-key
  "Return the registry key for a store. Accepts the key itself, or a bare namespace URI, in which
   case the newest registered version of that namespace is named."
  [k]
  (cond (map? k)     k
        (string? k)  (let [versions (->> @databases-atm keys (filter #(= k (:prefix %))) (map :version))]
                       (when (seq versions)
                         {:prefix k :version (nsuri/newest versions)}))
        :else        (throw (ex-info "A store is named by {:prefix .. :version ..} or by a namespace URI."
                                     {:given k}))))

(defn registered-versions
  "Return the versions of the argument namespace that are registered, newest last."
  [prefix]
  (->> @databases-atm keys (filter #(= prefix (:prefix %))) (map :version) (sort-by nsuri/version-vec) vec))

(defn register-db
  "Add a store configuration. k is {:prefix <namespace uri> :version <version string>}."
  [k config]
  (assert (and (map? k) (:prefix k) (:version k))
          "A store is registered under {:prefix <namespace uri> :version <version string>}.")
  (log! :debug (str "Registering store " k))
  (swap! databases-atm #(assoc % k config)))

(defn deregister-db
  "Remove a store configuration."
  [k]
  (log! :info (str "Deregistering store " k))
  (swap! databases-atm #(dissoc % (store-key k))))

(def db-template
  "Datahike file-based DBs follow this form."
  {:store {:backend :file :path "Provide a value!"} ; This is path to the database's root directory
   :keep-history? false
   :base-dir "Provide a value!"                     ; For convenience, this is just above the database's root directory.
   :schema-flexibility :write})

;;; https://cljdoc.org/d/io.replikativ/datahike/0.6.1545/doc/datahike-database-configuration
(defn db-cfg-map
  "Return a datahike configuration map for the store holding :version of namespace :prefix.

   The namespace URI is used as the path, so what is on disk says what it holds:
     /opt/uaexp/http:/opcfoundation.org/UA/MachineTool/1.02.0
   (the filesystem collapses the '//' of the scheme). Datahike will not create the intervening
   directories, so we do."
  [{:keys [prefix version in-mem?]}]
  (assert (and prefix version) "A store is identified by a namespace URI and a version.")
  (let [base-dir (or (-> (System/getenv) (get "UAEXP_DB"))
                     (throw (ex-info "Set the environment variable UAEXP_DB to the directory containing UA databases." {})))
        db-dir (str base-dir "/" prefix "/" version)]
    (when-not in-mem?
      (-> db-dir java.io.File. .getParentFile .mkdirs))
    (cond-> db-template
      true            (assoc :base-dir base-dir)     ; This is not a datahike thing.
      (not in-mem?)   (assoc :store {:backend :file :path db-dir})
      in-mem?         (assoc :store {:backend :mem :id (str prefix "|" version)}))))

(defn connect-atm
  "Return a connection atom for a store, named either by {:prefix .. :version ..} or by a bare
   namespace URI, which names its newest registered version.
   Throw an error if the store does not exist and :error? is true (default)."
  [k & {:keys [error?] :or {error? true}}]
  (let [key- (store-key k)
        db-cfg (get @databases-atm key-)]
    (cond (and db-cfg (d/database-exists? db-cfg)) (d/connect db-cfg)
          error? (throw (ex-info "No such store" {:asked-for k :resolved-to key-
                                                  :registered (vec (keys @databases-atm))}))
          :else nil)))

(defn datahike-schema
  "Create a Datahike-compatible schema from schema+ style schema with notes such as those in uaexp namespace removed.
   This drops schema from the ignore namespace."
  [schema]
  (reduce-kv (fn [r k v]
               (if (= (namespace k) "ignore")
                 r
                 (conj r (-> (reduce-kv (fn [m kk vv] (if (= (namespace kk) "db") (assoc m kk vv) m)) {} v)
                             (assoc :db/ident k)))))
             []
             schema))

;;; ------------------------------------------ resolve-node ---------------------------------------------------------
(defn db-ref?
  "It looks to me that a datahike ref is a map with exactly one key: :db/id."
  [obj]
  (and (map? obj) (= [:db/id] (keys obj))))

(defn foreign?
  "Truthy when obj is a foreign key -- a reference to a node in another store."
  [obj]
  (and (map? obj) (contains? obj :Node/ref)))

;;; (dbu/get-node-eid "i=25345" "http://opcfoundation.org/UA/")
(defn get-node-eid [i= db-id] (d/q '[:find ?e . :in $ ?id :where [?e :Node/id ?id]] @(connect-atm db-id) i=))
(defn get-node-i=  [eid db-id] (d/q '[:find ?id . :in $ ?eid :where [?eid :Node/id ?id]] @(connect-atm db-id) eid))

(defn resolve-db-id
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  [form db-id & {:keys [keep-set drop-set]
                 :or {drop-set #{:db/id}
                      keep-set #{}}}]
  (let [conn @(connect-atm db-id)
        seen (atom #{})]      ; A node reached twice is emitted as {:db/id n} rather than resolved
                              ; again; instance hierarchies are cyclic through HasComponent/ComponentOf.
    (letfn [(resolve-aux [obj]
              (cond
                (db-ref? obj) (if (@seen (:db/id obj))
                                obj
                                (let [_ (swap! seen conj (:db/id obj))
                                      res (dp/pull conn '[*] (:db/id obj))]
                                  (if (= res obj) nil (resolve-aux res))))
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

(defn deeper
  "Walk the structure, replacing each {:db/id n} with ONE step of resolution: that entity's own
   attributes, with its references left as {:db/id m} for a later pass. This is what resolve-node's
   :depth counts, so each pass reveals one more level.

   It used to call resolve-db-id here, which resolves all the way down rather than one step. That
   made :depth inert -- every depth gave the same answer -- and made resolve-node impractical:
   resolve-db-id leaves a {:db/id n} at each cycle it declines to follow, and every one was
   re-expanded over the whole graph."
  [obj db-id]
  (let [conn @(connect-atm db-id)]
    (letfn [(d-aux [obj]
              (cond (db-ref? obj)   (let [res (dp/pull conn '[*] (:db/id obj))]
                                      (if (= res obj) obj (dissoc res :db/id)))
                    (map? obj)      (reduce-kv (fn [m k v] (assoc m k (d-aux v))) {} obj)
                    (vector? obj)   (mapv d-aux obj)
                    :else           obj))]
      (d-aux obj))))

(defn walk-final
  "Walk the structure, replacing each remaining {:db/id n} with that node's address, so that what
   the walk stopped at is named the same way a foreign key is: {:Node/ref \"nsu=...;i=33\"}.
   The ids are fetched in one query rather than one per marker.

   Markers that are not nodes are left as they are. They have no address to give them -- they are
   the value and reference entities nodes are built from -- and resolving one to name it would
   splice its whole content in at every occurrence."
  [obj db-id]
  (let [conn @(connect-atm db-id)
        prefix (:prefix (store-key db-id))
        eids (atom #{})]
    (letfn [(collect [obj]
              (cond (db-ref? obj)   (swap! eids conj (:db/id obj))
                    (map? obj)      (doseq [[_ v] obj] (collect v))
                    (vector? obj)   (doseq [x obj] (collect x))))]
      (collect obj))
    (let [id-of (into {} (d/q '[:find ?e ?id :in $ [?e ...] :where [?e :Node/id ?id]] conn (vec @eids)))]
      (letfn [(wf-aux [obj]
                (cond (db-ref? obj)     (if-let [i= (id-of (:db/id obj))]
                                          {:Node/ref (nsuri/address prefix i=)}
                                          obj)
                      (map? obj)        (reduce-kv (fn [m k v] (assoc m k (wf-aux v))) {} obj)
                      (vector? obj)     (mapv wf-aux obj)
                      :else             obj))]
        (wf-aux obj)))))

(defn resolve-node
  "Return the node at the argument ExpandedNodeId, e.g.

     (resolve-node \"nsu=http://opcfoundation.org/UA/MachineTool/;i=26\")

   The namespace names the store and the identifier names the node within it, so this takes one
   argument. Resolution stops at the store's boundary: a node in another namespace appears as
   {:Node/ref <its address>} rather than being followed. Use expand-n to go further.

   A bare identifier (\"i=25345\") is accepted as shorthand for the base UA namespace, which is
   what one types at the REPL and what Part 5's documentation uses."
  [address & {:keys [depth] :or {depth 1}}]
  (let [{:keys [uri id]} (or (nsuri/parse-address address)
                             (when (nsuri/local-id? address) {:uri nsuri/base-uri :id address})
                             (throw (ex-info "Not a node address." {:given address})))
        eid (or (get-node-eid id uri)
                (throw (ex-info "No such node in that store." {:address address :namespace uri})))]
    (loop [obj (resolve-db-id {:db/id eid} uri)
           d depth]
      (if (zero? d)
        (walk-final obj uri)
        (recur (deeper obj uri) (dec d))))))

(defn ^:diag expand-n
  "Follow every foreign key in the argument structure, n times. Each pass replaces
   {:Node/ref <address>} with the node at that address, which may itself carry foreign keys.
   Resolution deliberately stops at store boundaries; this is how you cross them on purpose."
  [obj n]
  (letfn [(ex [obj]
            (cond (foreign? obj)  (try (resolve-node (:Node/ref obj))
                                       ;; The store for that namespace may not be registered. Say
                                       ;; so in the result rather than failing the whole walk.
                                       (catch Exception e (assoc obj :Node/unresolved (.getMessage e))))
                  (map? obj)      (reduce-kv (fn [m k v] (assoc m k (ex v))) {} obj)
                  (vector? obj)   (mapv ex obj)
                  :else           obj))]
    (nth (iterate ex obj) n)))
