(ns ua.build-part5
  "Things to create part5 edn"
  (:require
   [clojure.edn                 :as edn]
   [clojure.instant             :as instant]
   [clojure.string]
   [datahike.api                :as d]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.db-util                  :as dbu :refer [connect-atm db-cfg-map register-db]]
   [ua.p5-cardinality           :as p5-card]
   [ua.nsuri                    :as nsuri]
   [ua.putil                    :as pu :refer [create-ua-db! defparse rewrite-xml xml-attrs-as-content]]
   [ua.util                     :as util :refer [util-state]])) ; For mount

;;; ToDo: Start work on validation.
;;; ToDo: Use the core_test node-by-id structure to define defparse :p5/Reference.
(def ^:diag diag (atom false))

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

(defparse :p5/RequiredModel
  "A model this nodeset declares it depends on, with the version and publication date it expects.
   This is the nodeset's own statement of what it needs -- worth keeping distinct from the
   namespaces it is actually observed to reference, which is a different question and often a
   different answer."
  [{:xml/keys [attrs]}]
  (let [{:keys [ModelUri Version PublicationDate]} attrs]
    (cond-> {}
      ModelUri            (assoc :RequiredModel/uri ModelUri)
      Version             (assoc :RequiredModel/version Version)
      PublicationDate     (assoc :RequiredModel/publication-date PublicationDate))))

(defparse :p5/Model
  "We collect models into a property if only because the XML does too.
   <Model> has content as well as attributes: its <RequiredModel> children."
  [{:xml/keys [attrs content]}]
  (let [{:keys [ModelUri XmlSchemaUri Version PublicationDate ModelVersion]} attrs
        required (->> content (filterv #(= :p5/RequiredModel (:xml/tag %))) (mapv rewrite-xml))]
    (cond-> {}
      ModelUri            (assoc :Model/uri ModelUri)
      XmlSchemaUri        (assoc :Model/xml-schema-uri XmlSchemaUri)
      Version             (assoc :Model/version Version)
      PublicationDate     (assoc :Model/publication-date PublicationDate)
      ModelVersion        (assoc :Model/model-version ModelVersion)
      (seq required)      (assoc :Model/requires required))))

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

(def ^:dynamic *array-dimensions*
  "This is needed to deal with null <ArrayDimension /> Elements in UAVariable.  The actual value should be in an ArrayDimensions XML object.
   The value is comma-separated non-negative integers.
   The ArrayDimensions are in the UATypes XML namespace."
  nil)

(defparse :p5/UAVariable
  "This merges small pieces, but also deals with null <ArrayDimensions />."
  [xmap]
  (binding [*array-dimensions*
            (when-let [s (-> xmap :xml/attrs :ArrayDimensions)]
              (->> (clojure.string/split s #"\,")
                   (mapv read-string)))]
    (let [{:xml/keys [tag content]} (xml-attrs-as-content xmap)]
      (merge {:Node/type (-> tag name keyword)}
             (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))))

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
                  (when-let [[m _] (re-matches #"^(ns=\d+;)?i=\d+$" (str ReferenceType))] m))]
    (if rtype
      {(if (re-matches #"^(ns=\d+;)?i=\d+$" (str rtype))   {:IMPL/ref rtype}   (keyword "P5StdRefType" rtype)) ; ToDo: namespace #"^i=\d+$"
       (if (re-matches #"^(ns=\d+;)?i=\d+$" (str content)) {:IMPL/ref content} content)}
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
(defparse :p5/BrowseName
  "A BrowseName is a QualifiedName (Part 3, 8.3), written <namespace index>:<name> with index 0
   left off. The index is file-local -- the same hazard as a NodeId's ns= -- so it must not reach
   the DB: ua.nsuri resolves :IMPL/browse-name-index into :Node/browse-name-uri at load, and drops
   it when the name is in the store's own namespace, where it is implied. Keeping the bare name in
   :Node/browse-name is also what stops schema idents like :P5StdRefType/1:Contains, which keyword
   will build and the reader will not read."
  [{:xml/keys [content]}]
  (let [[_ idx nm] (re-matches #"^(?:(\d+):)?(.*)$" content)]
    (cond-> {:Node/browse-name nm}
      idx (assoc :IMPL/browse-name-index (parse-long idx)))))
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
  (assert (re-matches #"^(ns=\d+;)?i=\d+$" content))
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
  [{:xml/keys [content attrs] :as xmap}]
  (when (or (not= 1 (count content))
            (not-empty attrs))
    (log! :warn "p5/Value not as expected."))
  (letfn [(box [v]
            (cond (map? v)      v
                  (vector? v)   {:box/mix (mapv box v)}
                  (string? v)   {:box/string v}
                  (number? v)   {:box/number v}
                  (boolean? v)  {:box/boolean v}
                  (inst? v)     {:box/date-time v}
                  :else         (do (log! :warn (str "How do I box this?: " v))
                                    (throw (ex-info "box me" {:xmap xmap})))))]
    {:Node/value (-> content first rewrite-xml box)}))

;;; --------------------------- ExtensionObject and other UA types ------------------------------------------------------------------
;;; ToDo: I think the best thing to do with ExtensionObjects is to try to parse it and if it fails, store something related as
;;; :UAExtObj/object-string (or some such thing). But, of course, for the time being (Part 5 only) everything parses.
;;; (BTW, currently I don't use :UAExtObj as a namespace. All that stuff lands in UATypes, as it appears to be in Part 5 XML.

(defparse :UATypes/ExtensionObject
  "Return an object network in the UAExtObj namespace.
   Extension objects, of course, can have anything in them. I have in mind parsing them to nested map structures, stringified,
   if they vary from what I've seen in Part 5 XML." ; ToDo: Maybe there are parts that belong in ordinary object namespaces?
  [{:xml/keys [content] :as _xmap}]
  {:UATypes/ExtensionObject (reduce (fn [res c]
                                      (merge res (rewrite-xml c))) {} content)})

;;; Regarding Part5, the following (up to :UATypes/Boolean) are only found in ExtensionObjects. It looks like none have XML attrs.
(defparse :UATypes/Argument
  "Has structured content."
  [{:xml/keys [content]}]
  (reduce (fn [res c] (merge res (rewrite-xml c))) {} content))

(defparse :UATypes/ArrayDimensions
  "This one is different! In the XML of Part 5 at least, it is always a null element.
   However, in Part 5 I only see it used in UAVariablee, which has ArrayDimensions as an XML attribute.
   Because of this, I use a dynamic variable and check here that it is a number."
  [{:xml/keys [_content]}]
  (if (and (vector? *array-dimensions*) (every? int? *array-dimensions*))
    {:UATypes/ArrayDimensions *array-dimensions*}
    (throw (ex-info "Expected ArrayDimensions XML attribute in a UAVariable." {:dims *array-dimensions*}))))

(defparse :UATypes/Body
  "Has structured content."
  [{:xml/keys [content]}]
  {:UATypes/Body (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/DataType,
  "Has structured content that is (always?) UATypes/Identifier?"
  [{:xml/keys [content]}]
  (assert (== 1 (count content)))
  {:UATypes/DataType (-> content first rewrite-xml)})

(defparse :UATypes/Description
  "Has structured content that is a UATypes/Text and possibly UATypes/Locale so we give it (and the children) structure."
  [{:xml/keys [content] :as _xmap}]
  {:UATypes/Description (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/DisplayName
  "Has structured content that can contains UATypes/Locale and UATypes/Text."
  [{:xml/keys [content] :as _xmap}]
  {:UATypes/DisplayName (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/EUInformation
  "Has structured content."
  [{:xml/keys [content]}]
  {:UATypes/EUInformation (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/EnumValueType
  "Has structured content."
  [{:xml/keys [content]}]
  {:UATypes/EnumValueType (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/Identifier
  "Content is an i=."
  [{:xml/keys [content]}]
  {:IMPL/ref content})

(defparse :UATypes/Locale
  "This is often used with :UATypes/Text, so we give them both structure."
  [{:xml/keys [content]}]
  {:UATypes/Locale content})

(defparse :UATypes/Name
  "A string"
  [{:xml/keys [content]}]
  {:UATypes/Name content})

(defparse :UATypes/NamespaceUri
  "A string"
  [{:xml/keys [content]}]
  {:UATypes/NamespaceUri content})

(defparse :UATypes/Text
  "This is often used with :UATypes/Locale, thus we give it structure."
  [{:xml/keys [content] :as _xmap}]
  {:UATypes/Text (if (not-empty content) content "")})

(defparse :UATypes/TypeId
  "Has structured content which usually a UATypes/Identifier (for which I return {:IMPL/ref}."
  [{:xml/keys [content]}]
  {:UATypes/TypeId (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)})

(defparse :UATypes/UnitId
  "A string"
  [{:xml/keys [content]}]
  {:UATypes/UnitId content})

(defparse :UATypes/Value
  "A string, which in Part 5 is always a number." ; ToDo Check Part 3 for what this could be. Maybe box it.
  [{:xml/keys [content]}]
  {:UATypes/Value (edn/read-string content)})

(defparse :UATypes/ValueRank
  "A string, which in Part 5 is always a number."
  [{:xml/keys [content]}]
  {:UATypes/ValueRank (edn/read-string content)})

;;; ToDo: Needs investigation. I'm not wrapping any of these. I'm not defining :UATypes/{String, DateTime, Boolean, Int32, etc.}
;;;       At least :UATypes/LocalizedText can use a reader...when I see the right kind of example...;^)
;;; Only some of the following are found in ExtensionObjects of Part 5. Some exist because they are used elsewhere in Part5. (Trivia?)
;;; The ones found in ExtensionObjects are DataType, Locale, UInt32, and Text.
(defparse :UATypes/Boolean       "doc" [{:xml/keys [content]}]  (-> content edn/read-string))
(defparse :UATypes/ByteString    "doc" [{:xml/keys [content]}]  {:P6ByteString/str content})    ; ToDo Rethink these.
(defparse :UATypes/DateTime      "doc" [{:xml/keys [content]}]  (instant/read-instant-date content))
(defparse :UATypes/Double        "doc" [{:xml/keys [content]}]  (-> content edn/read-string double))
(defparse :UATypes/Int32         "doc" [{:xml/keys [content]}]  (-> content edn/read-string int))
(defparse :UATypes/String        "doc" [{:xml/keys [content]}]  (if content content ""))
(defparse :UATypes/UInt32        "doc" [{:xml/keys [content]}]  (-> content edn/read-string int)) ; ToDo Box? What can DB do?

(defparse :UATypes/LocalizedText "doc" [{:xml/keys [content] :as _xmap}]
  ;; An empty element, <LocalizedText/>, is how a nodeset writes the default value of a
  ;; LocalizedText-typed variable. MachineTool has 5 of them; Part 5 has none.
  (if (empty? content)
    {:P3LocalizedText/str ""}
    (do
      (when-not (and (every?  #(#{:UATypes/Text :UATypes/Locale} %) (map :xml/tag content))
                     (<= 2 (-> content first count)))
        (throw (ex-info "Unexpected UATypes/LocalizedText" {:xmap _xmap})))
      ;; Other things need :UATypes/Text and :UATypes/Locale as structure; this doesn't.
      (let [Text   (some #(when (= :UATypes/Text   (:xml/tag %)) %) content)
            Locale (some #(when (= :UATypes/Locale (:xml/tag %)) %) content)
            text    (-> Text  (rewrite-xml :UATypes/Text) :UATypes/Text)
            locale  (when Locale (-> Locale (rewrite-xml :UATypes/Locale) :UATypes/Locale))]
        (cond-> {:P3LocalizedText/str text}
          locale (assoc :P3LocalizedText/locale locale))))))

;;; --------------------------- Lists ---------------------------------------------------------------
(defparse :UATypes/ListOfExtensionObject
  "No containers for these."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/ExtensionObject))))

(defparse :UATypes/ListOfInt32
  "No containers for these."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/Int32))))

(defparse :UATypes/ListOfLocalizedText
  "No containers for these."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/LocalizedText))))

(defparse :UATypes/ListOfString
  "No containers for these."
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :UATypes/String))))

(defonce recreate-db? (atom false))

;;; ----------------------- Start and stop ----------------------------------------
(defn init-part5
  "Register DBs (currently just a part5-only DB), loading if DB does not exist and recreate-db? (above) is true."
  []
  (let [nodeset (-> "data/part5/p5-nodeset.edn" slurp edn/read-string)
        db-id {:prefix (nsuri/nodeset-uri nodeset) :version (nsuri/nodeset-version nodeset)}]
    (when @recreate-db?
      (create-ua-db! :schema+ {} :nodeset nodeset))
    ;; Every namespace is its own store now, so starting up means finding them all, not just this one.
    (let [found (dbu/discover-stores!)]
      {:part5-config @(connect-atm db-id) :db-id db-id :stores found})))

(defstate part5
  :start (init-part5))
