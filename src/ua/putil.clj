(ns ua.putil
  "Utililties for working with profiles"
  (:require
   [clojure.edn                 :as edn]
   [clojure.pprint              :refer [cl-format pprint]]
   [clojure.set                 :as set]
   [datahike.api                :as d]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.db-util                  :as dbu :refer [connect-atm datahike-schema db-cfg-map register-db]]
   [ua.nsuri                    :as nsuri]
   [ua.xml-util                 :as xu]))

(def ^:diag diag (atom false))
(def debugging? (atom false))

(defn rewrite-xml-dispatch
  [obj & [specified]]
  (cond ;; Optional 2nd argument specifies method to call
    (keyword? specified)                        specified,
    (and (map? obj) (contains? obj :xml/tag))   (:xml/tag obj)
    :else (throw (ex-info "No method for obj: " {:obj obj}))))

(defmulti rewrite-xml #'rewrite-xml-dispatch)

(defmethod rewrite-xml nil [obj]
  (log! :warn (str "No method for obj = " obj))
  :failure/rewrite-xml-nil-method)

(def nyi "nyi = Not yet implemented" (atom #{}))
(defmethod rewrite-xml :default [obj]
  (log! :warn (str "No method using default = " (:xml/tag obj)))
  (swap! nyi conj (:xml/tag obj))
  nil)

(def parse-depth (atom 0))

;;; ToDo: I think it is pretty odd that we call process-attrs-map here, especially so because
;;;       sometimes specific attrs are mapped again, differently.
(defmacro defparse [tag & others]
  (let [doc-string (when (and (-> others first string?)
                              (-> others second vector?))
                     (first others))
        arg  (if doc-string (nth others 1) (first others))
        body (if doc-string (nthrest others 2) (nthrest others 1))]
  `(defmethod rewrite-xml ~tag [~@arg & ~'_]
     ;; Once *skip-doc-processing?* is true, it stays so through the dynamic scope of the where it was set.
     (swap! parse-depth inc)
     (when @debugging?
       (println (cl-format nil "~A==> ~A" (ua.util/nspaces (* 3 @parse-depth)) ~tag)))
     (let [result# (do ~@body)]
       (when @debugging?
         (println (cl-format nil "~A<-- ~A : ~A" (ua.util/nspaces (* 3 @parse-depth)) ~tag (ua.util/elide result# 130))))
       (swap! parse-depth dec)
       result#))))

(defn xml-attrs-as-content
  "Make XML attrs content with p5-namespaced tags, checking to ensure no collisions."
  [{:xml/keys [attrs content] :as xml}]
  (let [attr-too? (->> attrs keys (map name) set)]
    (when (some #(attr-too? %) (->> content (map :xml/tag) (map name)))
      (throw (ex-info "Attribute/tag collision." {:attrs attr-too? :tags (map :xml/tag content)})))
    (let [attrs (reduce-kv (fn [res k v] (conj res (-> {}
                                                       (assoc :xml/tag (keyword "p5" (name k)))
                                                       (assoc :xml/content v))))
                           []
                           attrs)]
      (-> xml
          (dissoc :xml/attrs)
          (update :xml/content into attrs)))))

;;; --------------------------- Learn Schema ---------------------------------------------------------------
;;; Metadata marks these as ":admin" because they are used by developers, not in deployment.
;;; Actual schema used in practice might be a manual modification of what is generated here.
(defn ^:admin db-type-of
  "Return a Datahike schema :db/valueType object for the argument"
  [obj]
  (cond (string? obj)  :db.type/string
        (number? obj)  :db.type/number
        (keyword? obj) :db.type/keyword
        (map? obj)     :db.type/ref
        (boolean? obj) :db.type/boolean
        (inst? obj)    :db.type/instant
        :else (throw (ex-info  "Unknown type for schema: " {:obj obj}))))

(defn  ^:admin sample-vec
  "Run db-type-of on just some of the data in vec."
  [vec k & {:keys [sample-threshold sample-size]
             :or {sample-threshold 200 sample-size 100}}]
  (let [len (count vec)
        vec (if (< len sample-threshold)
               vec ; ToDo: repeatedly solution less than ideal.
               (repeatedly sample-size #(nth vec (rand-int len))))
        result (-> (map db-type-of vec) set)]
    (if (> (count result) 1)
      (throw (ex-info "Heterogeneous types:"
                      {:types result :attribute k :vector vec}))
      (first result))))

(defn ^:admin schema-for-db
  "Given a map indexed by DB idents with values (maps) containing some information about those
   idents in a form consistent with the type argument of database (either :datascript or :datahike)
   return a conforming schema for that database. To do this it just filters out the extraneous
   key/value pairs of each value, and in the case of :datahike, returns a vector of maps where the
   original keys are used to set :db/ident in each vector element (a map)."
  [smap type]
  (as-> smap ?schema ; Remove schema entries whose keys are not :db
    (reduce-kv (fn [m k v]
                 (let [new-v (reduce-kv (fn [m1 k1 v1] (if (= "db" (namespace k1)) (assoc m1 k1 v1) m1))
                                        {}
                                        v)]
                   (assoc m k new-v)))
               {}
               ?schema)
    (case type
      :datahike ;; DH uses a vec and attr :db/ident.
      (reduce-kv (fn [res k v] (conj res (assoc v :db/ident k))) [] ?schema)
      :datascript ;; DS uses a map indexed by what would be :db/ident (like the input ?schema)
      (reduce-kv (fn [schemas attr schema]
                   (assoc schemas
                          attr ; DS doesn't use :db/valueType except to distinguish refs.
                          (reduce-kv (fn [m k v]
                                       (if (and (= k :db/valueType) (not (= v :db.type/ref)))
                                       m
                                       (assoc m k v)))
                                     {}
                                     schema)))
                 {}
                 ?schema))))

;;; (->> "data/part5/p5-nodeset.edn" slurp edn/read-string core/learn-schema (sort-by :db/ident))
(defn ^:admin learn-schema-basic
  "Return DH/DS schema objects for the data provided."
  [data & {:keys [known-schema datahike?] :or {known-schema {} datahike? true}}]
  (let [learned (atom known-schema)]
    (letfn [(update-learned! [k v]
              (let [typ  (-> @learned k :db/valueType)
                    card (-> @learned k :db/cardinality)
                    vec? (vector? v)
                    this-typ  (if vec? (sample-vec v k) (db-type-of v))
                    this-card (if (or vec? (= card :db.cardinality/many)) ; permissive to many
                                :db.cardinality/many
                                :db.cardinality/one)]
                (when (keyword? this-typ) ; Could be nil.
                  (if (and typ (not= typ this-typ))
                    ;; Silly that (str nil) is ""!
                    (log! :warn (cl-format  nil "Different types for key k =  ~S  typ =  ~S  this-typ = ~S. (Box these?)" k typ this-typ))
                    (swap! learned #(-> %
                                        (assoc-in [k :db/cardinality] this-card)
                                        (assoc-in [k :db/valueType] this-typ)))))))
            (lsw-aux [obj]
              (cond (map? obj) (doall (map (fn [[k v]]
                                             (update-learned! k v)
                                             (when (coll? v) (lsw-aux v)))
                                           obj))
                    (coll? obj) (doall (map lsw-aux obj))))]
      (lsw-aux data)
      (schema-for-db @learned (if datahike? :datahike :datascript)))))


;;; ------------------- Write profile edn -----------------------------------------------
;;; (pu/write-nodeset-edn! "data/part5/OPC_UA_Core_Model_2515947497.xml" "data/part5/p5-nodeset.edn")
(defn write-nodeset-edn!
  [xml-file out-file]
  (reset! parse-depth 0)
  (let [xml (reset! diag (xu/read-xml xml-file :root-name "p5"))
        profile (rewrite-xml (-> xml :xml/content first) :p5/UANodeSet)
        s (with-out-str (pprint profile))]
    (spit out-file s)))

(defn merge-warn
  "Merge the argument schema, which could be {} it with part5-schema+, warning where there are collisions. Return a "
  [schema+]
  (let [part5-schema+ (-> "data/part5/part5-schema+.edn" slurp edn/read-string)
        collisions (set/intersection (-> schema+ keys set) (-> part5-schema+ keys set))]
    (when (not-empty collisions)
      (log! :warn (str "The following are defined in Part5; their redefinition in the nodeset is being ignored: " collisions)))
    (-> schema+ (merge part5-schema+) datahike-schema)))

(defn collect-lookups
  [obj]
  (let [lookups (atom [])]
    (letfn [(cl [obj]
              (cond (map? obj)        (if (contains? obj :Node/id)
                                        (let [{:Node/keys [id browse-name]} obj]
                                          (when-not (string? browse-name)  (throw (ex-info "No browse-name" {:obj obj})))
                                          (swap! lookups conj {:Node/id id :Node/browse-name browse-name}))
                                        (doseq [[_ v] obj] (cl v)))
                    (vector? obj)     (doseq [x obj] (cl x))))]
      (cl obj)
      @lookups)))

;;; (p5s/load-lookups! :part5 p5)
(defn load-lookups! [db-id nodeset] ; ToDo: Chunk these! (Seems you can't do all of them at once.
  (assert (contains? nodeset :NodeSet/content))
  (let [content (:NodeSet/content nodeset)
        cnt (atom 0)]
    (log! (str "Loading " (count content) " lookups."))
    (loop [lookups (collect-lookups content)]
      (let [[these others] (split-at 100 lookups)]
        (when (not-empty these)
          (swap! cnt #(+ % (count these)))
          (d/transact (connect-atm db-id) {:tx-data (vec these)})
          (recur others))))
    (log! :info (str "Loaded " @cnt " lookups."))))

(defn node-by-i=
  "Return the node object having :Node/id = i=. (i= is a string; I know it's sick!)"
  [i= nodeset]
  (some #(when (= i= (:Node/id %)) %) (:NodeSet/content nodeset)))


(defn impl-ref-pred-symbol
  "A UA Reference is a map with just one entry. The key of this refers to a 'predicate symbol' and the value is a {:IMPL/ref <i=n>}
   It is permitted that the 'predicate symbol' position of a UA Reference is  also a {:IMPL/ref <i=n>}.
   In this case, the {:IMPL/ref <i=n>} should point to a UAReferenceType. When we find these, we
   return (keyword 'P5StdRefType' (-> {:IMPL/ref <i=n>} lookup-node :Node/display-name))."
  [i= nodeset]
  (let [node (node-by-i= i= nodeset)]
    (if (= :UAReferenceType (:Node/type node))
      (keyword "P5StdRefType" (:Node/display-name node))
      (throw (ex-info "Could not resolve predicate symbol." {:i= i=, :node node})))))

(def nodeset-memo (atom nil))

(defn resolve-node-ids
  [node db-id]
  (letfn [(key-check [k]
            (if (map? k)
              (if (contains? k :IMPL/ref)
                (let [pred-symbol (impl-ref-pred-symbol (:IMPL/ref k) @nodeset-memo)]
                  (log! :info (str "IMPL/ref in predicate symbol position is " pred-symbol))
                  pred-symbol)
                (log! :warn (str "This map should have an IMPL/ref: " k)))
              k))
          (lookup-ref [i=]
            (or (d/q '[:find ?e . :in $ ?id :where [?e :Node/id ?id]] @(connect-atm db-id) i=)
                (throw (ex-info "No DB entry for index:" {:i= i=}))))
          (rni [obj]
            (cond (and (map? obj) (contains? obj :IMPL/ref))      {:db/id (lookup-ref (:IMPL/ref obj))}
                  ;; A node in another namespace lives in another store, where an entity id means
                  ;; nothing. Keep its address instead. :Node/ref is unique, so the many references
                  ;; to one foreign node collapse to one object, and the target need not exist.
                  (and (map? obj) (contains? obj :IMPL/foreign)) {:Node/ref (:IMPL/foreign obj)}
                  (map? obj)                                     (reduce-kv (fn [m k v] (assoc m (key-check k) (rni v))) {} obj)
                  (vector? obj)                                  (mapv rni obj)
                  :else                                          obj))]
    (rni node)))

(defn load-nodeset!
  "Read the part5 edn into the DB. This is two-staged, wherein the first stage creates lookups,
   and the second stage loads the full object."
  [db-id nodeset]
  (let [nodeset (-> nodeset
                    nsuri/canonicalize-nodeset
                    ;; :ignore nodes (a vendor tool's <Extensions>, so far) have no schema by
                    ;; design -- dbu/datahike-schema drops the namespace -- so they cannot be
                    ;; transacted. Drop them here rather than giving the namespace a schema.
                    (update :NodeSet/content #(filterv (fn [n] (not= :ignore (:Node/type n))) %)))
        root {:NodeSet/uri (nsuri/nodeset-uri nodeset)
              :NodeSet/version (nsuri/nodeset-version nodeset)}
        cnt (atom 0)]
    (reset! nodeset-memo nodeset)
    (load-lookups! db-id nodeset)
    (log! :info (str "Loading " (-> nodeset :NodeSet/content count) " nodes."))
    ;; Everything is loaded under one root, so the models, aliases and namespace table are
    ;; reachable from it rather than floating unreferenced. :NodeSet/uri is unique, so the
    ;; chunks merge into a single root instead of making one apiece.
    (loop [nodes (:NodeSet/content  nodeset)]
      (let [[these others] (split-at 50 nodes)]
        (when (not-empty these)
          (swap! cnt #(+ % (count these)))
          (d/transact (connect-atm db-id)
                      {:tx-data [(assoc root :NodeSet/content (mapv #(resolve-node-ids % db-id) these))]})
          (recur others))))
    (log! :info (str "Loaded " @cnt " nodes."))))

(defn create-ua-db!
  "Create the store for one nodeset. The store holds exactly the namespace the nodeset defines,
   at the version it declares; both are read from its own <Models>, so there is nothing to name
   by hand and no way to put a nodeset in the wrong store. Nodes it references in other namespaces
   are recorded as foreign keys, so nothing else has to be loaded first -- or at all.
   If schema+ is provided it is merged with Part 5's."
  [& {:keys [schema+ nodeset]}]
  (let [prefix (nsuri/nodeset-uri nodeset)
        version (or (nsuri/nodeset-version nodeset)
                    (throw (ex-info "Nodeset's <Models> declares no Version." {:prefix prefix})))
        db-id {:prefix prefix :version version}]
    (log! :info (str "Creating store for " prefix " version " version "."))
    (if (get (System/getenv) "UAEXP_DB")
      (let [schema (merge-warn schema+)
            cfg (db-cfg-map db-id)]
        (when (d/database-exists? cfg) (d/delete-database cfg))
        (d/create-database cfg)
        (register-db db-id cfg)
        (let [conn (connect-atm db-id)]
          (d/transact conn schema)
          (load-nodeset! db-id nodeset)
          (assoc cfg :db-id db-id)))
      (log! :error "You have to set the environment variable UAEXP_DB to a directory."))))
