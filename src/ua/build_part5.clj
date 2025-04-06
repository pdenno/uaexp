(ns ua.build-part5
  "Things to create part5 edn"
  (:require
   [camel-snake-kebab.core      :as csk]
   [clojure.edn                 :as edn]
   [clojure.instant             :as instant]
   [clojure.pprint              :refer [cl-format pprint]]
   [clojure.set                 :as set]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.p5-cardinality           :as p5-card]
   [ua.util                     :as util :refer [util-state]])) ; For mount

;;; ToDo: Start work on validation.
;;; ToDo: Use the core_test node-by-id structure to define defparse :p5/Reference.

(def debugging? (atom false))
(def diag (atom false))
(declare write-schema+-file)

;;; ToDo: isForward, a common XML attribute, is not in this list. Why?
(def attr-status-keys
  "Mandatory and optional attributes of the 8 NodeClasses. This was created from Part 3 Clause 5.9 Table 7. (See https://reference.opcfoundation.org/Core/Part3/v105/docs/5.9)
   Note that attributes need not be encoded as XML attributes. The commented values are because things can default." ; ToDo: Not all defaults investigated; awaiting clojure specs.
  {:p5/VariableType  {:mandatory #{:ValueRank :IsAbstract :DisplayName :BrowseName :WriteMask :Value :NodeClass :NodeId :UserWriteMask :DataType},
                      :optional #{:AccessRestrictions :RolePermissions :ArrayDimensions :Description :UserRolePermissions :ValueRank}},
   :p5/View          {:mandatory #{:DisplayName :BrowseName :WriteMask :ContainsNoLoops :EventNotifier :NodeClass :NodeId :UserWriteMask},
                      :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   :p5/DataType      {:mandatory #{:IsAbstract :DisplayName :BrowseName :WriteMask :NodeClass :NodeId :UserWriteMask},
                      :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   :p5/Object        {:mandatory #{:DisplayName :BrowseName :WriteMask :EventNotifier :NodeClass :NodeId :UserWriteMask},
                      :optional #{:Description}},
   :p5/Method        {:mandatory #{:DisplayName :BrowseName :WriteMask :UserExecutable :NodeClass :Executable :NodeId :UserWriteMask},
                      :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   :p5/Variable      {:mandatory #{:AccessLevel :ValueRank :Historizing :UserAccessLevel :DisplayName :BrowseName :WriteMask :Value :NodeClass :NodeId :UserWriteMask :DataType},
                      :optional #{:UserWriteMaskEx :AccessRestrictions :MinimumSamplingInterval :RolePermissions :ArrayDimensions :WriteMaskEx :Description :UserRolePermissions :AccessLevelEx}},
   :p5/ObjectType    {:mandatory #{:IsAbstract :DisplayName :BrowseName :WriteMask :NodeClass :NodeId :UserWriteMask},
                      :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   :p5/ReferenceType {:mandatory #{:InverseName :IsAbstract :DisplayName :BrowseName :WriteMask :Symmetric :NodeClass :NodeId :UserWriteMask},
                      :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}}})

(def attr-status
  "Same as above but with string values for the :mandatory and :optional sets."
  (reduce-kv (fn [m k v]
               (assoc m k (update-vals v #(->> % (map name) set))))
             {}
             attr-status-keys))

(def default-attr-vals
  {"ValueRank" 0
   "IsAbstract" false})


(def node-class? #{:p5/DataType :p5/Method :p5/Object :p5/ObjectType :p5/ReferenceType :p5/Variable :p5/VariableType :p5/View})

;;; This one needs thought. Being in Table 17 of Part 3 doesn't seem to mean much.
(def part3-attr?
  "A set/predicate for the Part 3 attributes."
  (let [res (atom #{})]
    (doseq [v (vals attr-status)]
      (swap! res into (:mandatory v))
      (swap! res into (:optional v)))
    @res))

(defn rewrite-xml-dispatch
  [obj & [specified]]
  (cond ;; Optional 2nd argument specifies method to call
    (keyword? specified)                        specified,
    (and (map? obj) (contains? obj :xml/tag))   (:xml/tag obj)
    :else (throw (ex-info "No method for obj: " {:obj obj}))))

(defmulti rewrite-xml #'rewrite-xml-dispatch)

(defn call-this [arg]
  (reset! diag arg)
  (throw (ex-info "call-this" {:arg arg})))

(defmethod rewrite-xml nil [obj]
  (log! :warn (str "No method for obj = " obj))
  (call-this {:obj obj})
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
       (println (cl-format nil "~A==> ~A" (util/nspaces (* 3 @parse-depth)) ~tag)))
     (let [result# (do ~@body)]
       (when @debugging?
         (println (cl-format nil "~A<-- ~A : ~A" (util/nspaces (* 3 @parse-depth)) ~tag (util/elide result# 130))))
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

;;; ----------------- NodeSet -------------------------------------------------------------------------
(defparse :p5/UANodeSet
  "This is typically the 'toplevel' parsing task."
  [xmap]
  {:NodeSet/content (->> xmap :xml/content (mapv rewrite-xml))})

(defparse :p5/Alias
  "Return an Alias. It is a map with keys :Alias/name and :Alias/node-id."
  [xmap]
  {:Alias/name (-> xmap :xml/attrs :Alias)
   :Alias/node-id (:xml/content xmap)})

(defparse :p5/Aliases
  "We collect aliases into a property if only because the XML does too."
  [xmap]
  {:NodeSet/aliases (->> xmap :xml/content (mapv rewrite-xml))})

(defparse :p5/Model
  "We collect models into a property if only because the XML does too."
  [{:xml/keys [attrs]}]
  (let [{:keys [ModelUri XmlSchemaUri Version PublicationDate ModelVersion]} attrs]
    (cond-> {}
      ModelUri            (assoc :Model/uri ModelUri)
      XmlSchemaUri        (assoc :Model/xml-schema-uri XmlSchemaUri)
      Version             (assoc :Model/version Version)
      PublicationDate     (assoc :Model/publication-date PublicationDate)
      ModelVersion        (assoc :Model/model-version ModelVersion))))

(defparse :p5/Models
  "We collect models into a property if only because the XML does too."
  [{:xml/keys [content]}]
  {:NodeSet/models (mapv #(rewrite-xml % :p5/Model) content)})

;;; ----------------- Node Classes -------------------------------------------------------------------------
;;; ToDo: If these all do the same thing (still in devl) update the dispatcher...

(defparse :p5/UADataType
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAMethod
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAObject
  "This just merges small pieces."
  [xmap]
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAObjectType
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAReferenceType
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAVariable
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAVariableType
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
    (merge {:Node/type (-> tag name keyword)}
           (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))))

(defparse :p5/UAView
  "There are none of these in P5 (nor probably anywhere else!)."
  [_xmap]
  (throw (ex-info "UAView!" {})))

;;; -------------------------- Other ----------------------------------------------------------------
(defparse :p5/Reference
  "Return a reference instance as a map with one key (the predicate) and one value.
   Both the key and the value could be a {:IMPL/ref <i=num>} to be resolved later.
   Typically, however, the key returned will be a keyworkd in the P5StdRefType
   References Types are defined Part 5, https://reference.opcfoundation.org/Core/Part5/v105/docs/11
   Some Reference Type have more basic in information in Part 3."
  [xmap]
  (let [{:xml/keys [attrs content]} xmap
        {:keys [ReferenceType IsForward]} attrs
        forward? (not= IsForward "false")
        rtype (or (p5-card/lookup-ref-type ReferenceType forward?)
                  (re-matches #"^i=\d+$" (str ReferenceType)))]
    (if rtype
      {(if (re-matches #"^i=\d+$" (str rtype))   {:IMPL/ref rtype}   (keyword "P5StdRefType" (csk/->kebab-case rtype)))
       (if (re-matches #"^i=\d+$" (str content)) {:IMPL/ref content} content)}
      (throw (ex-info "No such ReferenceType: " {:xmap xmap})))))

(defparse :p5/References
  "Returns a map with one key :Node/reference
   Value is a vector of 2-place vectors [<ref-name keyword> <index-string>].
   Note use of :Node/references despite references not being attributes of a node class per Table 17."
  [xmap]
  {:Node/references (->> xmap :xml/content (mapv #(rewrite-xml % :p5/Reference)))})

;;; ------------------------- Content of node classes except :p5/Value (return maps to merge) -------------------
(defparse :p5/AccessLevel         "doc" [{:xml/keys [content]}] {:Node/access-level content})
(defparse :p5/AccessRestrictions  "doc" [{:xml/keys [content]}] {:Node/access-restictions content})
(defparse :p5/ArrayDimensions     "doc" [{:xml/keys [content]}] {:Node/array-dimensions (edn/read-string content)})
(defparse :p5/BrowseName          "doc" [{:xml/keys [content]}] {:Node/browse-name content})
(defparse :p5/Category            "doc" [{:xml/keys [content]}] {:Node/category content})
(defparse :p5/DataType            "doc" [{:xml/keys [content]}] {:Node/data-type content})
(defparse :p5/Description         "doc" [{:xml/keys [content]}] {:Node/description content})
(defparse :p5/DisplayName         "doc" [{:xml/keys [content]}] {:Node/display-name content})   ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/Documentation       "doc" [{:xml/keys [content]}] {:Node/documentation content})  ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/EventNotifier       "doc" [{:xml/keys [content]}] {:Node/event-notifier content}) ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/InverseName         "doc" [{:xml/keys [content]}] {:Node/inverse-name content})
(defparse :p5/IsAbstract          "doc" [{:xml/keys [content]}] {:Node/is-abstract? (if (= "false" content) false true)})
(defparse :p5/IsOptionSet         "doc" [{:xml/keys [content]}] {:Node/is-option-set? (if (= "false" content) false true)})
(defparse :p5/MethodDeclarationId "doc" [{:xml/keys [content]}] {:Node/method-declaration-id content})
(defparse :p5/NodeId              "doc" [{:xml/keys [content]}] {:Node/id content})
(defparse :p5/ParentNodeId        "doc" [{:xml/keys [content]}] {:Node/parent-node-id content}) ; Not in Table 17, but I'm putting it on the node.
(defparse :p5/Purpose             "doc" [{:xml/keys [content]}] {:Node/purpose content}) ; This is only on UADataType AFAICS.
(defparse :p5/ReleaseStatus       "doc" [{:xml/keys [content]}] {:Node/release-status content})
(defparse :p5/RolePermissions     "doc" [{:xml/keys [content]}] {:Node/role-permissions (mapv rewrite-xml content)})
(defparse :p5/SymbolicName        "doc" [{:xml/keys [content]}] {:Node/symbolic-name content}) ; This is only on UAObjectType AFAICS.
(defparse :p5/Symmetric           "doc" [{:xml/keys [content]}] {:Node/symmetric? (if (= "false" content) false true)})
(defparse :p5/ValueRank           "doc" [{:xml/keys [content]}] {:Node/value-rank (edn/read-string content)})

;;; --------------------------- RolePermissions ----------------------------------------------------
(defparse :p5/RolePermission
  "Permissions get their own namespace"
  [{:xml/keys [content attrs]}]
  (assert (re-matches #"^i=\d+$" content)) ; ToDo: Only suitable for P5, I think.
  (assert (= '(:Permissions) (keys attrs))) ; ToDo: Probably could regex match for valid permissions, \d+.
  {:RolePerm/ref content
   :RolePerm/permissions (:Permissions attrs)})

;;; --------------------------- Definition ----------------------------------------------------------
(defparse :p5/Definition
  "Definitions seem to have fields with values and descriptions. Everything here will be in NS def."
  [{:xml/keys [content attrs]}]
  (let [dname (:Name attrs)]
    (cond-> {:Definition/name dname}
      (not-empty content) (assoc :Definition/fields (mapv #(rewrite-xml % :p5/Field) content)))))

(defparse :p5/Field
  "Return a map with the keys in namespace 'field'. Used in :p5/Definition
   Field typically has Description, Name, and Value." ; ToDo: Warn on irregularities.
  [xmap]
  (reduce (fn [r c] (merge r (rewrite-xml c)))
          {}
          (->> xmap
               xml-attrs-as-content
               :xml/content
               (map #(update % :xml/tag (fn [tag] (keyword "Field" (name tag))))))))

(defparse :Field/AllowSubTypes "doc" [{:xml/keys [content]}] {:Field/allow-sub-types? (edn/read-string content)})
(defparse :Field/DataType      "doc" [{:xml/keys [content]}] {:Field/data-type        content})
(defparse :Field/Description   "doc" [{:xml/keys [content]}] {:Field/description      content})
(defparse :Field/Name          "doc" [{:xml/keys [content]}] {:Field/name             content})
(defparse :Field/Value         "doc" [{:xml/keys [content]}] {:Field/value            content})
(defparse :Field/ValueRank     "doc" [{:xml/keys [content]}] {:Field/value-rank       content})

;;;---------------------------- Value (often an ExtensionObject, datetime, list of strings, anything, really.  ------------------
(defparse :p5/Value
  "Returns the map with one key, :Node/value.
   AFAICS, these have a single child and no attrs. Value is boxed."
  [{:xml/keys [content attrs] :as _xmap}]
  (when (or (not= 1 (count content))
            (not-empty attrs))
    (log! :warn "p5/Value not as expected."))
  (letfn [(box [v]
            (cond (map? v)      {:box/ref v}
                  (vector? v)   (mapv box v)
                  (string? v)   {:box/string v}
                  (number? v)   {:box/number v}
                  (boolean? v)  {:box/boolean v}
                  (inst? v)     {:box/date-time v}
                  :else         (do (log! :warn (str "How do I box this?: " v))
                                    (reset! diag v)
                                    (throw (ex-info "box me" {:xmap _xmap})))))]
    {:Node/value (-> content first rewrite-xml box)}))

;;; --------------------------- ExtensionObject and other UA types ------------------------------------------------------------------
;;; I think the best thing to do here is to try to parse it and if it fails, store it as :UAExtObj/object-string (or some such thing).
(def ext-keys (atom #{}))

(defparse :UATypes/ExtensionObject
  "Return an object network in the UAExtObj namespace.
   Extension objects, of course, can have anything in them. I have in mind parsing them to nested map structures, stringified,
   if they vary from what I've seen in Part 5 XML." ; ToDo: Maybe there are parts that belong in ordinary object namespaces?
  [xmap]
  (letfn [(ekeys [obj] (cond (map? obj)     (doseq [[k v] obj] (swap! ext-keys conj k) (ekeys v))
                             (vector? obj)  (doseq [x obj] (ekeys x))))]
    (-> xmap xml-attrs-as-content ekeys))
  {:UAExtObj/hey! :extension-obj-nyi}) ; <==================================================================

;;; ToDo: Needs investigation. I'm not wrapping any of these. I'm not defining :UATypes/{String, DateTime, Boolean, Int32, etc.}
;;;       At least :UATypes/LocalizedText can use a reader...when I see the right kind of example...;^)
(defparse :UATypes/Boolean       "doc" [{:xml/keys [content]}]  (-> content edn/read-string #_boolean))
(defparse :UATypes/ByteString    "doc" [{:xml/keys [content]}]  {:P6ByteString/str content})    ; ToDo Rethink these.
(defparse :UATypes/DateTime      "doc" [{:xml/keys [content]}]  (instant/read-instant-date content))
(defparse :UATypes/Int32         "doc" [{:xml/keys [content]}]  (-> content edn/read-string int))
(defparse :UATypes/Locale        "doc" [{:xml/keys [content]}]  content)
(defparse :UATypes/String        "doc" [{:xml/keys [content]}]  (if content content ""))
(defparse :UATypes/Text          "doc" [{:xml/keys [content]}]  (if content content ""))
(defparse :UATypes/UInt32        "doc" [{:xml/keys [content]}]  (-> content edn/read-string int)) ; ToDo Box? What can DB do?

(defparse :UATypes/LocalizedText "doc" [{:xml/keys [content]}]
  (when-not (every?  #(#{:UATypes/Text :UATypes/Locale} %) (map :xml/tag content))
    (log! :warn (str "Unexpected Localized Text" content))
    (reset! diag content)
    (throw (ex-info "" {})))
  (let [{:UATypes/keys [Text Locale]} (group-by :xml/tag content)]
    (cond-> {:P3LocalizedText/str (rewrite-xml Text :UATypes/Text)}
      Locale (assoc :P3LocalizedText/locale (rewrite-xml Locale :UATypes/Text)))))

;;; --------------------------- Lists ---------------------------------------------------------------
(defparse :UATypes/ListOfExtensionObject
  "Whether containers are required for any lists is not yet clear."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/ExtensionObject))))

(defparse :UATypes/ListOfInt32
  "Whether containers are required for any lists is not yet clear."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/Int32))))

(defparse :UATypes/ListOfLocalizedText
  "Whether containers are required for any lists is not yet clear."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/LocalizedText))))

(defparse :UATypes/ListOfString
  "Whether containers are required for any lists is not yet clear."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/String))))

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

;;; (->> "data/part5/p5.edn" slurp edn/read-string core/learn-schema (sort-by :db/ident))
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

(def p5-memo "Keep the Part 5 structure so you don't have to slurp and read-string it." (atom nil))

(defn make-p5-std-ref-type-schema
  "Return a vector of DataHike schema for Part 5 ReferenceTypes (P5RefType) using the structure produced from Part 5 XML and the P5 cardinality table."
  [p5]
  (letfn [(ref2schema [{:Node/keys [browse-name category documentation id inverse-name is-abstract? release-status symmetric?]}]
            (let [fwd-schema (cond-> {:db/ident (keyword "P5StdRefType" (csk/->kebab-case browse-name))
                                      :db/valueType :db.type/ref
                                      :db/cardinality (->  p5-card/card-table (get browse-name) :cardinality)
                                      :uaexp/id id}
                               documentation           (assoc :db/doc documentation)
                               category                (assoc :uaexp/category category)
                               is-abstract?            (assoc :uaexp/is-abstract? true)
                               release-status          (assoc :uaexp/release-status release-status)
                               symmetric?              (assoc :uaexp/symmetric? true))
                  rev-schema (when inverse-name
                               (cond-> {:db/ident (keyword "P5StdRefType" (csk/->kebab-case inverse-name))
                                        :db/valueType :db.type/ref
                                        :db/cardinality (->  p5-card/card-table (get browse-name) :inverse-cardinality)
                                        :uaexp/inverse? true
                                        :uaexp/id id}
                                 documentation           (assoc :db/doc documentation)
                                 category                (assoc :uaexp/category category)
                                 is-abstract?            (assoc :uaexp/is-abstract? true)
                                 release-status          (assoc :uaexp/release-status release-status)
                                 symmetric?              (assoc :uaexp/symmetric? true)))]
              (cond-> [fwd-schema] rev-schema (conj rev-schema))))]
    (let [ref-types (->> p5 :NodeSet/content (filterv #(= (:Node/type %) :UAReferenceType)))
          result (atom [])]
      (doseq [typ ref-types]
        (swap! result into (ref2schema typ)))
      @result)))

(def expected-ns
  "These are keys returned by make-schema-info that are expected. schema is or can be specified for them."
  #{"Alias" "Definition" "Model" "Node" "NodeSet" "P3LocalizedText" "P5StdRefType" "P6ByteString" "RolePerm" "UAExtObj" "box" "Field"})

(def schema-mods
  "Modifications to computed schema, for example which ones are keys."
  [{:db/ident :Node/id :db/unique :db.unique/identity}])

(defn mod-schema
  "Modify the schema with schema-mods."
  [schemas schema-mods]
  (let [updated-schemas (atom schemas)]
    (doseq [schema-mod schema-mods]
      (let [{:db/keys [ident]} schema-mod]
        (swap! updated-schemas
               (fn [u-s]
                 (reduce-kv (fn [m k v]
                              (assoc m k (reduce (fn [res s]
                                                   (if (= ident (:db/ident s))
                                                     (conj res (merge s schema-mod))
                                                     (conj res s)))
                                                 []
                                                 v)))
                            {}
                            u-s)))))
    @updated-schemas))

;;; This is essentially 'top-level' of the functionality in this file.
(defn ^:admin make-schema-info
  "This creates schema maps in a map indexed by the object type strings, for example, 'Node' and 'NodeSet, and 'P5RefType'.
   Some of these can be used as is. Exceptions:
    - P5RefType - is ignored because we want inverse relations too; for these we go back to the edn and do something different."
  ([] (make-schema-info "data/part5/p5.edn"))
  ([fname]
   (let [schema-info (as-> fname ?d
                       (slurp ?d)
                       (edn/read-string ?d)
                       (reset! p5-memo ?d) ; We'll use this below, but we keep it public for debugging.
                       (learn-schema-basic ?d)
                       (group-by #(if (-> % :db/ident keyword?) (-> % :db/ident namespace) :other) ?d)
                       (dissoc ?d :other)       ; These should be inside P5StdRefType, handled separately below.
                       (dissoc ?d "IMPL"))      ; This is :IMPL/ref, which will be resolved while storing entities.
         found-keys (-> schema-info keys set)
         bad-keys (set/difference found-keys expected-ns)]
     (when (not-empty bad-keys)
       (log! :warn (str "There are entity types that need investigation: " bad-keys)))
     (when-let [missing (not-empty (set/difference expected-ns found-keys))]
       (log! :warn (str "Types not present: " missing)))
     (-> schema-info
         (assoc "P5StdRefType" (make-p5-std-ref-type-schema @p5-memo))
         (mod-schema schema-mods)
         write-schema+-file))))

(defn write-schema+-file
  "Write the schema-info to a file nicely."
  [schema-info]
  (let [s (atom "")
        skeys (-> schema-info keys sort)]
    (letfn [(write [x] (swap! s #(str % x)))]
      (write (str ";;; Schema created " (java.util.Date.) "\n\n"))
      (write "{\n")
      (doseq [k skeys]
        (write (str " ;; --------------------------- " k "\n"))
        (doseq [s (->> (get schema-info k) (sort-by :db/ident))]
          (write (str " " (:db/ident s) "\n "  (with-out-str (pprint (dissoc s :db/ident))) "\n")))
        (write "\n"))
      (write "}"))
    (spit "data/part5/p5-temp-schema+.edn" @s)
    (log! :info "Wrote data/part5/p5-temp-schema+.edn")))
