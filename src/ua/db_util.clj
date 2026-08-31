(ns ua.db-util
  "Utilities for schema-db (which will likely become a library separate from rad-mapper"
  (:require
   [clojure.string        :as str]
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
  (cond (= :system k) k
        (map? k)     k
        (string? k)  (let [versions (->> @databases-atm keys (filter #(= k (:prefix %))) (map :version))]
                       (when (seq versions)
                         {:prefix k :version (nsuri/newest versions)}))
        :else        (throw (ex-info "A store is named by {:prefix .. :version ..} or by a namespace URI."
                                     {:given k}))))

(defn ^:admin registered-versions
  "Return the versions of the argument namespace that are registered, newest last."
  [prefix]
  (->> @databases-atm keys (filter #(= prefix (:prefix %))) (map :version) (sort-by nsuri/version-vec) vec))

(defn register-db
  "Add a store configuration. k is {:prefix <namespace uri> :version <version string>}."
  [k config]
  (assert (or (= :system k) (and (map? k) (:prefix k) (:version k)))
          "A store is registered under {:prefix <namespace uri> :version <version string>}; the system DB under :system.")
  (log! :debug (str "Registering store " k))
  (swap! databases-atm #(assoc % k config)))

(defn ^:admin deregister-db
  "Remove a store configuration."
  [k]
  (log! :info (str "Deregistering store " k))
  (swap! databases-atm #(dissoc % (store-key k))))

(def db-template
  "Datahike file-based DBs follow this form."
  {:store {:backend :file :path "Provide a value!"} ; This is path to the database's root directory; :id is added by db-cfg-map
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
  [{:keys [type prefix version in-mem?]}]
  (assert (or (= :system type) (and prefix version))
          "A store is identified by a namespace URI and a version; the system DB by {:type :system}.")
  (let [base-dir (or (-> (System/getenv) (get "UAEXP_DB"))
                     (throw (ex-info "Set the environment variable UAEXP_DB to the directory containing UA databases." {})))
        ;; The system DB sits beside the namespace trees rather than inside one: it is uaexp's own
        ;; bookkeeping, not a nodeset. discover-stores! knows to skip it.
        name- (if (= :system type) "system" (nsuri/store-id prefix version))
        db-dir (if (= :system type) (str base-dir "/system") (str base-dir "/" prefix "/" version))
        ;; konserve requires every store config to carry a UUID :id (datahike 0.7 / konserve 0.7).
        ;; Deriving it from the store's own name keeps it stable across runs -- a fresh random one
        ;; would not find the store it made last time -- and needs nothing recorded anywhere.
        store-id (java.util.UUID/nameUUIDFromBytes (.getBytes ^String name- "UTF-8"))]
    (when-not in-mem?
      (-> db-dir java.io.File. .getParentFile .mkdirs))
    (cond-> db-template
      true            (assoc :base-dir base-dir)     ; This is not a datahike thing.
      (not in-mem?)   (assoc :store {:backend :file :path db-dir :id store-id})
      ;; :mem was renamed :memory in datahike 0.7 / konserve 0.7; :mem now throws.
      in-mem?         (assoc :store {:backend :memory :id store-id}))))

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

(defmacro with-connect-atom
  "Bind conn-sym to a connection for db-id, run body, and release the connection afterwards.
   Datahike connections hold resources; the store-building path is short-lived enough not to care,
   but the system DB is read on nearly every operation."
  [[conn-sym db-id] & body]
  `(let [~conn-sym (connect-atm ~db-id)]
     (try ~@body
          (finally (d/release ~conn-sym)))))

(defn- path->store-key
  "Invert db-cfg-map's layout: <base>/http:/opcfoundation.org/UA/1.05.04 names version 1.05.04 of
   http://opcfoundation.org/UA/. The filesystem collapsed the scheme's '//', so put it back."
  [base-dir path]
  (let [rel (subs path (inc (count base-dir)))
        parts (str/split rel #"/")
        scheme (first parts)
        middle (butlast (rest parts))]
    {:prefix (str scheme "//" (str/join "/" middle) "/")
     :version (last parts)}))

(defn discover-stores!
  "Register every store under UAEXP_DB and return their keys.

   The directory layout says which namespace and version a store holds, and the store's own root
   says the same thing (:NodeSet/uri, :NodeSet/version). The path is used to find candidates; the
   root is used to confirm them, so a store that has been moved or misfiled is reported rather
   than registered under the wrong name."
  []
  (let [base-dir (or (-> (System/getenv) (get "UAEXP_DB"))
                     (throw (ex-info "Set the environment variable UAEXP_DB to the directory containing UA databases." {})))
        system-dir (str base-dir "/system")
        candidates (->> (file-seq (java.io.File. base-dir))
                        (filter #(and (.isDirectory %)
                                      (some (fn [f] (str/ends-with? (.getName f) ".ksv"))
                                            (or (seq (.listFiles %)) []))))
                        (map #(.getPath %))
                        ;; The system DB lives under UAEXP_DB too, but it is not a nodeset store:
                        ;; path->store-key would invent {:prefix "system//" :version "system"} for
                        ;; it and it would be registered on the strength of having no root.
                        (remove #(str/starts-with? % system-dir)))]
    (->> candidates
         (keep (fn [path]
                 (let [k (path->store-key base-dir path)]
                   (register-db k (db-cfg-map k))
                   (let [claimed (d/q '[:find [?uri ?v] :where [?e :NodeSet/uri ?uri] [?e :NodeSet/version ?v]]
                                      @(connect-atm k))]
                     (cond (nil? claimed)
                           (do (log! :warn (str "Store at " path " has no root; leaving it registered as " k ".")) k)

                           (= claimed [(:prefix k) (:version k)]) k

                           :else
                           (do (deregister-db k)
                               (log! :error (str "Store at " path " says it holds " claimed
                                                 " but its location says " ((juxt :prefix :version) k)
                                                 ". Not registered."))
                               nil))))))
         vec)))

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

(defn nodeset-root
  "Return the entity id of the store's root -- the one entity that :NodeSet/content hangs from.
   Its children are the store's nodes plus the nodeset-level records: <Models>, <Aliases> and
   the <NamespaceUris> table. Aliases in particular are needed to read anything else."
  [db-id]
  (let [prefix (:prefix (store-key db-id))]
    (d/q '[:find ?e . :in $ ?uri :where [?e :NodeSet/uri ?uri]] @(connect-atm db-id) prefix)))

(defn ^:diag nodeset-aliases
  "Return the store's alias table as {<alias name> <local identifier>}. A nodeset writes references
   by alias (ReferenceType=\"HasComponent\"), so this is how those names map to nodes."
  [db-id]
  (->> (d/q '[:find ?n ?id :where [_ :NodeSet/aliases ?a] [?a :Alias/name ?n] [?a :Alias/node-id ?id]]
            @(connect-atm db-id))
       (into {})))

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
