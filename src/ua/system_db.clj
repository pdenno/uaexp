(ns ua.system-db
  "The system DB: uaexp's catalog of the nodeset stores it has built.

   A store can describe the namespace it holds, because that is in the nodeset. What it cannot
   describe is its own derivation -- which file it came from, when, under which parser, what that
   parser could not represent, and what the nodeset said it depended on. All of that is computed
   while a store is built and, until now, logged and dropped.

   Shape: one :system root -> many :store (one per <namespace, version> on disk) -> many :build
   (append-only, one per time that store was made). The split matters: a rebuild REPLACES a store
   in place -- there is only ever one directory per <namespace, version> -- so :store/id stays
   stable and is what a consumer connects with, while the build records say how the bytes on disk
   came to be. :store/current-build names the one that did.

   This is a catalog, not an API. Tessell's orchestrator is meant to survey it with a Datalog
   query, take a :store/id, and hand ua.nsuri/parse-store-id's answer to dbu/connect-atm."
  (:require
   [clojure.edn                 :as edn]
   [clojure.java.io             :as io]
   [clojure.java.shell          :as shell]
   [clojure.pprint              :refer [pprint]]
   [clojure.set                 :as set]
   [clojure.string              :as str]
   [datahike.api                :as d]
   [malli.core                  :as m]
   [malli.error                 :as me]
   [mount.core                  :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.db-util                  :as dbu :refer [connect-atm db-cfg-map register-db with-connect-atom resolve-db-id]]
   [ua.nsuri                    :as nsuri]
   [ua.util                     :as util :refer [util-state]])) ; For mount

(def ^:diag diag (atom nil))

(def schema+
  "The system DB's schema, in schema+ format. Read from the classpath, as ua.putil reads Part 5's."
  (-> "schema/system-schema+.edn" io/resource slurp edn/read-string))

(def db-schema-sys
  "The system DB's schema, as Datahike wants it."
  (dbu/datahike-schema schema+))

;;; ---------------------------------- Malli ------------------------------------------------
;;; Datahike's :write flexibility rejects an unknown attribute but says nothing about a build
;;; record that is merely incomplete -- one with no :build/at, or with counts missing. These
;;; schemas are the check for that, applied before the transaction rather than after.
(def Requires
  [:map {:closed true}
   [:requires/uri :string]
   [:requires/version {:optional true} :string]
   [:requires/publication-date {:optional true} :string]])

(def Foreign
  [:map {:closed true}
   [:foreign/uri :string]
   [:foreign/target-count {:optional true} :int]])

(def Ignored
  [:map {:closed true}
   [:ignored/kind :keyword]
   [:ignored/content :string]])

(def Build
  "One derivation of one store."
  [:map {:closed true}
   [:build/id :string]
   [:build/at inst?]
   [:build/uaexp-sha {:optional true} :string]
   [:build/source-file :string]
   [:build/source-sha256 {:optional true} :string]
   [:build/node-count :int]
   [:build/content-count {:optional true} :int]
   [:build/ignored-count {:optional true} :int]
   [:build/requires {:optional true} [:vector Requires]]
   [:build/foreign {:optional true} [:vector Foreign]]
   [:build/nyi-tags {:optional true} [:vector :keyword]]
   [:build/ignored {:optional true} [:vector Ignored]]
   [:build/schema-collisions {:optional true} [:vector :keyword]]
   [:build/unexpected-entity-types {:optional true} [:vector :string]]
   [:build/missing-entity-types {:optional true} [:vector :string]]
   [:build/note {:optional true} [:vector :string]]])

(def Store
  "A catalog entry. Its builds are transacted separately, so they are not part of this."
  [:map {:closed true}
   [:store/id :string]
   [:store/prefix :string]
   [:store/version :string]
   [:store/publication-date {:optional true} :string]
   [:store/model-version {:optional true} :string]])

(defn- validate!
  "Return obj if it conforms to schema; otherwise log what is wrong and return nil."
  [schema obj what]
  (if (m/validate schema obj)
    obj
    (do (log! :error (str "Invalid " what ": " (me/humanize (m/explain schema obj))))
        nil)))

;;; ---------------------------------- Reading ----------------------------------------------
(defn system-exists?
  "Return the eid of the singleton {:system/name \"SYSTEM\"} entity, or nil."
  []
  (with-connect-atom [conn :system]
    (d/q '[:find ?e . :where [?e :system/name "SYSTEM"]] @conn)))

(defn ^:diag get-system
  "Return the whole system DB as a structure. Big; mostly useful at the REPL and for backup."
  []
  (when-let [eid (system-exists?)]
    (resolve-db-id {:db/id eid} :system)))

(defn store-exists?
  "Return the eid of the catalog entry for store-id, or nil."
  [store-id]
  (with-connect-atom [conn :system]
    (d/q '[:find ?e . :in $ ?id :where [?e :store/id ?id]] @conn store-id)))

(defn list-stores
  "Return every :store/id in the catalog, sorted. The survey query."
  []
  (with-connect-atom [conn :system]
    (-> (d/q '[:find [?id ...] :where [_ :store/id ?id]] @conn) sort vec)))

(defn get-store
  "Return the catalog entry for store-id, builds and all."
  [store-id]
  (when-let [eid (store-exists? store-id)]
    (resolve-db-id {:db/id eid} :system)))

(defn summarize-stores
  "Return one map per catalog entry: what it holds and when it was last built.
   This is what a consumer surveys before deciding which store to connect to."
  []
  (with-connect-atom [conn :system]
    (->> (d/q '[:find ?id ?prefix ?version ?at ?nodes
                :where
                [?e :store/id ?id]
                [?e :store/prefix ?prefix]
                [?e :store/version ?version]
                [?e :store/current-build ?b]
                [?b :build/at ?at]
                [?b :build/node-count ?nodes]]
              @conn)
         (mapv (fn [[id prefix version at nodes]]
                 {:store-id id :prefix prefix :version version :built at :nodes nodes}))
         (sort-by :store-id)
         vec)))

(defn connect-key
  "Return the {:prefix .. :version ..} that dbu/connect-atm takes, for a catalog :store/id.
   The one step between surveying the catalog and querying a nodeset."
  [store-id]
  (or (nsuri/parse-store-id store-id)
      (throw (ex-info "Not a store id." {:given store-id}))))

;;; ------------------------------- Declared vs. actual --------------------------------------
(defn ^:diag dependency-report
  "For each store, what its nodeset DECLARED it needs (<RequiredModel>) against what it actually
   references. Neither implies the other: a model can be declared and never used, and -- because
   canonicalization only needs the namespace table -- a namespace can be referenced without being
   declared. Both are worth seeing."
  []
  (with-connect-atom [conn :system]
    (->> (d/q '[:find ?id ?b :where [?e :store/id ?id] [?e :store/current-build ?b]] @conn)
         (mapv (fn [[id b]]
                 (let [declared (set (d/q '[:find [?u ...] :in $ ?b :where [?b :build/requires ?r] [?r :requires/uri ?u]] @conn b))
                       actual   (set (d/q '[:find [?u ...] :in $ ?b :where [?b :build/foreign ?f] [?f :foreign/uri ?u]] @conn b))]
                   {:store-id id
                    :declared-and-used (vec (sort (set/intersection declared actual)))
                    :declared-not-used (vec (sort (set/difference declared actual)))
                    :used-not-declared (vec (sort (set/difference actual declared)))})))
         (sort-by :store-id)
         vec)))

(defn ^:diag namespaces-wanted
  "Namespaces some store references for which no store has been built, and who wants them.
   Derived rather than stored, so it cannot go stale."
  []
  (with-connect-atom [conn :system]
    (let [have (set (d/q '[:find [?p ...] :where [_ :store/prefix ?p]] @conn))]
      (->> (d/q '[:find ?id ?u :where [?e :store/id ?id] [?e :store/current-build ?b] [?b :build/foreign ?f] [?f :foreign/uri ?u]] @conn)
           (remove (fn [[_ u]] (have u)))
           (reduce (fn [m [id u]] (update m u (fnil conj #{}) id)) {})
           (reduce-kv (fn [v u ids] (conj v {:uri u :wanted-by (vec (sort ids))})) [])
           (sort-by :uri)
           vec))))

;;; ----------------------------------- Provenance -------------------------------------------
(defn sha256
  "Return the hex SHA-256 of a file, or nil if it cannot be read. This is what makes 'is this
   store stale against its source?' answerable."
  [path]
  (try
    (let [md (java.security.MessageDigest/getInstance "SHA-256")
          buf (byte-array 8192)]
      (with-open [in (io/input-stream (io/file path))]
        (loop []
          (let [n (.read in buf)]
            (when (pos? n) (.update md buf 0 n) (recur)))))
      (apply str (map #(format "%02x" %) (.digest md))))
    (catch Exception _ nil)))

(defn uaexp-sha
  "Return the current uaexp commit, with -dirty appended when the tree has uncommitted changes.
   nil when git cannot answer, which is not worth failing a build over."
  []
  (try
    (let [{:keys [exit out]} (shell/sh "git" "rev-parse" "--short" "HEAD")]
      (when (zero? exit)
        (let [sha (str/trim out)
              {:keys [out]} (shell/sh "git" "status" "--porcelain")]
          (if (str/blank? out) sha (str sha "-dirty")))))
    (catch Exception _ nil)))

(defn ^:diag stale-stores
  "Stores whose source file no longer digests to what the current build recorded, or is gone.
   These are the ones that need rebuilding."
  []
  (with-connect-atom [conn :system]
    (->> (d/q '[:find ?id ?f ?sha
                :where
                [?e :store/id ?id] [?e :store/current-build ?b]
                [?b :build/source-file ?f] [?b :build/source-sha256 ?sha]]
              @conn)
         (keep (fn [[id f sha]]
                 (let [now (sha256 f)]
                   (cond (nil? now)      {:store-id id :source f :status :source-missing}
                         (not= now sha)  {:store-id id :source f :status :source-changed}))))
         (sort-by :store-id)
         vec)))

;;; ------------------------------------ Writing ---------------------------------------------
(defn put-store!
  "Add or update the catalog entry for a store. Idempotent: :store/id is :db.unique/identity, so
   a rebuild updates the entry rather than making a second one."
  [{:store/keys [id] :as store}]
  (when-let [store (validate! Store store "store")]
    (with-connect-atom [conn :system]
      (d/transact conn {:tx-data [(assoc store :db/id -1)
                                  {:db/id (system-exists?) :system/stores -1}]}))
    id))

(defn add-build!
  "Append a build record to a store's history and make it the current one.

   `build` is the map of everything known about this derivation; see the Build schema. Anything
   absent is simply not recorded -- a caller that cannot compute the nyi tags should omit them
   rather than claim there were none."
  [store-id build]
  (let [eid (or (store-exists? store-id)
                (throw (ex-info "No catalog entry for that store; call put-store! first."
                                {:store-id store-id})))
        build (-> build
                  (update :build/at #(or % (java.util.Date.)))
                  (as-> $b (assoc $b :build/id (str store-id "|" (.toInstant ^java.util.Date (:build/at $b))))))]
    (when-let [build (validate! Build build "build")]
      (with-connect-atom [conn :system]
        (d/transact conn {:tx-data [(assoc build :db/id -1)
                                    {:db/id eid :store/builds -1 :store/current-build -1}]}))
      (log! :info (str "Recorded build of " store-id " (" (:build/node-count build) " nodes)."))
      (:build/id build))))

(defn build-record
  "Assemble the :build map for one derivation, from the nodeset and what the loader observed.

   Takes data rather than reaching into ua.putil or ua.profiles for it, which is what keeps this
   namespace a leaf: the loader will eventually call add-build!, and a require back the other way
   would be a cycle.

     :nodeset      - the parsed nodeset, canonicalized or not (foreign keys are computed here)
     :source-file  - what it was read from
     :nyi-tags     - contents of pu/nyi after the parse, if the caller reset it beforehand
     :ignored      - contents of pro/ignored-nodes, likewise
     :schema-collisions, :unexpected-entity-types, :missing-entity-types - what merge-warn and
                     make-schema+ warned about."
  [{:keys [nodeset source-file nyi-tags ignored schema-collisions
           unexpected-entity-types missing-entity-types]}]
  (let [content (:NodeSet/content nodeset)
        canon (nsuri/canonicalize-nodeset nodeset)
        foreign (->> (nsuri/foreign-addresses canon)
                     (map #(:uri (nsuri/parse-address %)))
                     frequencies
                     ;; Datahike's :db.type/long will not take the Integer that count returns.
                     (mapv (fn [[uri n]] {:foreign/uri uri :foreign/target-count (long n)})))
        required (->> content (some :NodeSet/models) first :Model/requires
                      (mapv (fn [{:RequiredModel/keys [uri version publication-date]}]
                              (cond-> {:requires/uri uri}
                                version          (assoc :requires/version version)
                                publication-date (assoc :requires/publication-date publication-date)))))]
    (cond-> {:build/at (java.util.Date.)
             :build/source-file source-file
             :build/node-count (long (count (filter :Node/id content)))
             :build/content-count (long (count content))}
      (uaexp-sha)                    (assoc :build/uaexp-sha (uaexp-sha))
      (sha256 source-file)           (assoc :build/source-sha256 (sha256 source-file))
      (seq required)                 (assoc :build/requires required)
      (seq foreign)                  (assoc :build/foreign foreign)
      (seq nyi-tags)                 (assoc :build/nyi-tags (vec nyi-tags))
      (seq ignored)                  (assoc :build/ignored (vec ignored))
      (seq ignored)                  (assoc :build/ignored-count (long (count ignored)))
      (seq schema-collisions)        (assoc :build/schema-collisions (vec schema-collisions))
      (seq unexpected-entity-types)  (assoc :build/unexpected-entity-types (vec unexpected-entity-types))
      (seq missing-entity-types)     (assoc :build/missing-entity-types (vec missing-entity-types)))))

(defn record-build!
  "Put the store in the catalog if it is not there, then append this build and make it current.
   Returns the :store/id."
  [nodeset opts]
  (let [prefix (nsuri/nodeset-uri nodeset)
        version (nsuri/nodeset-version nodeset)
        model (->> nodeset :NodeSet/content (some :NodeSet/models) first)
        id (nsuri/store-id prefix version)]
    (put-store! (cond-> {:store/id id :store/prefix prefix :store/version version}
                  (:Model/publication-date model) (assoc :store/publication-date (:Model/publication-date model))
                  (:Model/model-version model)    (assoc :store/model-version (:Model/model-version model))))
    (add-build! id (build-record (assoc opts :nodeset nodeset)))
    id))

;;; ------------------------------- Backup and restore ---------------------------------------
;;; The stores are recomputable from the XML; the catalog is not. It is the only thing here worth
;;; backing up, which is why this pair exists and there is no equivalent for nodesets.
(defn ^:admin backup-system-db
  "Write the system DB to an EDN file."
  [& {:keys [target-dir] :or {target-dir "data/"}}]
  (io/make-parents (str target-dir "dummy"))
  (if-let [eid (system-exists?)]
    (let [filename (str target-dir "system-db.edn")
          obj (resolve-db-id {:db/id eid} :system)
          s (with-out-str (println "[") (pprint obj) (println "]"))]
      (log! :info (str "Writing system DB to " filename))
      (spit filename s)
      filename)
    (log! :error "Could not find system DB.")))

(defn ^:admin recreate-system-db!
  "Recreate the system DB from the EDN file backup-system-db wrote."
  [& {:keys [target-dir] :or {target-dir "data/"}}]
  (let [filename (str target-dir "system-db.edn")]
    (if (.exists (io/file filename))
      (let [cfg (db-cfg-map {:type :system})]
        (log! :info "Recreating the system database.")
        (when (d/database-exists? cfg) (d/delete-database cfg))
        (d/create-database cfg)
        (register-db :system cfg)
        (with-connect-atom [conn :system]
          (d/transact conn db-schema-sys)
          (d/transact conn (-> filename slurp edn/read-string)))
        cfg)
      (log! :error (str "Not recreating system DB: no backup at " filename)))))

;;; -------------------------- Starting and stopping ------------------------------
(defn init-system-db!
  "Create the system DB if it does not exist; register it either way."
  []
  (let [cfg (db-cfg-map {:type :system})]
    (if (d/database-exists? cfg)
      (do (log! :info "System DB exists; connecting.")
          (register-db :system cfg))
      (do (log! :info "Creating the system DB.")
          (d/create-database cfg)
          (register-db :system cfg)
          (with-connect-atom [conn :system]
            (d/transact conn db-schema-sys)
            (d/transact conn [{:system/name "SYSTEM"}]))
          (log! :info "System DB initialized.")))
    {:cfg cfg :stores (count (list-stores))}))

(defn ^:diag ensure-system-db!
  "Return a connection to the system DB, creating it if need be."
  []
  (or (connect-atm :system :error? false)
      (do (init-system-db!) (connect-atm :system))))

(defstate system-db
  :start (init-system-db!))
