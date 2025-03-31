(ns ua.core
  "Toplevel of uaexp."
  (:require
   [clojure.edn                 :as edn]
   [clojure.instant             :as instant]
   [clojure.pprint              :refer [cl-format]]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.util                     :as util :refer [util-state]])) ; For mount

;;; ToDo: Most stuff in here belongs in a new file part5_db.clj.
;;; ToDo: It is not essential to use defparse or even rewrite-xml. You can check for M/O attributes using attr-status.
;;;       If you went this route, you'd still have to deal with aliases, and extensions, however.
;;;       So it might be neater to use defparse. You only need about a dozen methods.
;;; ToDo: Start work on validation.

(def debugging? (atom false))
(def diag (atom false))

(def nyi (atom #{}))

(def tags&attrs
  "These are the tags and attributes found in Part 5 1.05.04. I use this to plan parsing.
   There is no :p5 objects (namespaces) in the DB. There are instead, UANodeSet, UAAlias UA<some node class>, UAType, UAReference, UADefinition, and UAExtObj."
  {:tags
   #{:p5/Alias                      ; defparse to :UAAlias/...
     :p5/Aliases                    ; defparse to :UANodeset/Aliases ... Everything that is not :UANodeSet/Aliases or :UANodeSet/Models is :UANodeset/content ???
     :UATypes/Argument              ; :UAExtObj
     :UATypes/ArrayDimensions       ; UA Attribute
     :UATypes/Body                  ; :UAExtObj
     :UATypes/Boolean               ; types.xsd, simple
     :UATypes/ByteString            ; types.xsd, simple string child element?
     :p5/Category                   ; <Just Text, I've seen it in documentation, but can't find it now. I'm going to use :Node/category.
     :UATypes/DataType              ; UA Attribute
     :UATypes/DateTime              ; Types.xsd, simple
     :p5/Definition                 ; :UADataType/Definition <==================  Maybe UADefinition as a Namespace????
     :UATypes/Description           ; UA Attribute
     :p5/Description                ; UA Attribute
     :UATypes/DisplayName           ; UA Attribute <==================== Should be :p5 Need to fix this <==============================
     :p5/DisplayName                ; UA Attribute
     :p5/Documentation              ; <Just Text, I've seen it in documentation, but can't find it now. Maybe keep in :p5/Documentation in DB.>
     :UATypes/EUInformation         ; :UAExtObj (for CEFACT stuff, at least)
     :UATypes/EnumValueType         ; :UAExtObj
     :UATypes/ExtensionObject       ; :UAExtObj
     :p5/Field                      ; :UADataType/Definition <================== ?
     :UATypes/Identifier            ; :UAExtObj
     :UATypes/Int32                 ; Types.xsd
     :p5/InverseName                ; UA Attribute
     :UATypes/ListOfExtensionObject ; <plural> Don't keep
     :UATypes/ListOfInt32           ; <plural> Don't keep
     :UATypes/ListOfLocalizedText   ; <plural> Don't keep
     :UATypes/ListOfString          ; <plural? Don't keep
     :UATypes/Locale                ; Types.xsd <================================== Need to check this.
     :UATypes/LocalizedText         ; Types.xsd, simple
     :p5/Model                      ; defparse to :UANodeSet/Models
     :p5/Models                     ; defparse <plural> Don't keep.
     :UATypes/Name                  ; :UAExtObj
     :UATypes/NamespaceUri          ; ignore
     :p5/Reference                  ; <Reference!> Don't keep
     :p5/References                 ; <plural> :db/cardinality :db/many
     :p5/RolePermission             ; <Singular>
     :p5/RolePermissions            ; UA Attribute
     :UATypes/String                ; Types.xsd
     :UATypes/Text                  ; :UAExtObj <================================= Need to check this.
     :UATypes/TypeId                ; :UAExtObj
     :p5/UADataType                 ; NodeClass It's own defparse? Makes :UADataType/<attributes>
     :p5/UAMethod                   ; NodeClass
     :p5/UANodeSet                  ; defparse  Have a UANodeSet object in the DB. Container for all of P5 and likewise for profiles
     :p5/UAObject                   ; NodeClass
     :p5/UAObjectType               ; NodeClass
     :p5/UAReferenceType            ; NodeClass
     :p5/UAVariable                 ; NodeClass
     :p5/UAVariableType             ; NodeClass
     :UATypes/UInt32                ; Types.xsd
     :UATypes/UnitId                ; :UAExtObj (Part of EUInformation)
     :UATypes/Value                 ; :UAExtObj  (or should I let it slide as a UA Attribute? Sometimes an XML attr.)
     :p5/Value                      ; UA Attribute (sometimes an attr)
     :UATypes/ValueRank},           ; UA Attribute (sometimes an attr)

   :attrs ;; Attributes with be kebob-case, including the UA Attributes. They will be in the namespace of the object in which they are used?
          ;; In fact, the only place camelCase will be used is in the namespace names: NodeSet, Alias, <Node Class>
   #{:AccessLevel          ; UA Attribute
     :AccessLevelEx        ; UA Attribute, not found in Part 5 xml.
     :AccessRestrictions   ; UA Attribute
     :Alias                ; Found in the Alias element.
     :AllowSubTypes        ; Found in UADataType Fields
     :ArrayDimensions      ; UA Attribute
     :BrowseName           ; UA Attribute
     :DataType             ; UA Attribute
     :EventNotifier        ; UA Attribute
     :IsAbstract           ; UA Attribute
     :IsForward            ; Found in References
     :IsOptionSet          ; Found in Definitions
     :LastModified         ; On UANodeSet
     :MethodDeclarationId  ; Found in UAMethod
     :ModelUri             ; On Model element
     :ModelVersion         ; On Model element
     :Name                 ; In Definitions, and their Fields (Call it UADefinition?)
     :NodeId               ; UA Attribute
     :ParentNodeId         ; In UAVariable
     :Permissions          ; In UAVarible, its RolePermission objects
     :PublicationDate      ; On Model element
     :Purpose              ; On UADataType
     :ReferenceType        ; On References
     :ReleaseStatus        ; On any NodeClass. Translate to :Node/release-status (even though it is not a NodeClass attribute).
     :SymbolicName         ; Used on UAObject, at least :UAObject/symbolic-name
     :Symmetric            ; UA Attribute
     :Value                ; UA Attribute
     :ValueRank            ; UA Attribute
     :Version              ; On Model element
     :XmlSchemaUri}})      ; On Model element

;;; ToDo: isForward, a common XML attribute, is not in this list. Why?
(def attr-status-keys
  "Mandatory and optional attributes of the 8 NodeClasses. This was created from Part 3 Clause 5.9 Table 7. (See https://reference.opcfoundation.org/Core/Part3/v104/docs/5.9)
   Note that attributes need not be encoded as XML attributes. The commented values are because things can default." ; ToDo: Not all defaults investigated; awaiting clojure specs.
  {:p5/VariableType  {:mandatory #{#_:ValueRank #_:IsAbstract :DisplayName :BrowseName :WriteMask :Value :NodeClass :NodeId :UserWriteMask :DataType},
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

;;; ToDo: Study, for example, ObjectType https://reference.opcfoundation.org/Core/Part5/v104/docs/6
(def additional-properties
  "Properties for which I haven't yet studied the documentation, but seem to appear in P5 XML."
  {:p5/VariableType  #{"Category"}
   :p5/View          #{}
   :p5/DataType      #{"Category"}
   :p5/Object        #{"Category"}
   :p5/Method        #{"Category"}
   :p5/Variable      #{}
   :p5/ObjectType    #{"Category"}
   :p5/ReferenceType #{}}) ; ToDo: this is not nearly complete.

(def node-class? #{:p5/DataType :p5/Method :p5/Object :p5/ObjectType :p5/ReferenceType :p5/Variable :p5/VariableType :p5/View})

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

(defmethod rewrite-xml :default [obj]
  (log! :warn (str "No method using default = " (:xml/tag obj)))
  (swap! nyi conj (:xml/tag obj))
  nil)

(defn process-attrs-map
  [attrs-map]
  (reduce-kv (fn [res k v] (-> res (conj (name k)) (conj (str v)))) [] attrs-map))

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
       #_(cond-> result#
         (:xml/attrs result#) (-> (assoc :xml/attributes (-> result# :xml/attrs process-attrs-map))
                                  (dissoc :xml/attrs)))
       (when @debugging?
         (println (cl-format nil "~A<-- ~A" (util/nspaces (* 3 @parse-depth)) ~tag)))
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

;;; A nice thing about node classes is that they don't contain other node-classes.
(defn analyze-node-xml
  "Return a map for a node, making attributes properties, checking for mandatory properties, and
   setting the namespaces of everything to the proper UA concept."
  [{:xml/keys [tag] :as _xml}]
  (assert (node-class? tag)))

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
;;; ToDo: Guessing at most inverse names.
(defn ref-type
  [{:keys [ReferenceType IsForward] :as xml-map}]
  (let [forward? (not= IsForward "false")]
    (when (and (not forward?)
               (#{"AlwaysGeneratesEvent" "GeneratesEvent"} ReferenceType))
      (throw (ex-info "Non-forward boolean relationship" {:xml-map xml-map})))
    (let [result
          (case ReferenceType
            "AlarmGroupMember"            (if forward? :P5RefType/alarm-group-member             :P5RefType/member-of-alarm-group)
            "AlarmSuppressionGroupMember" (if forward? :P5RefType/alarm-suppression-group-member :P5RefType/member-of-alarm-suppression-group)
            "AlwaysGeneratesEvent"        :P5RefType/always-generates-event?
            "FromState"                   (if forward? :P5RefType/from-state                     :P5RefType/to-state)      ; ToDo: Needs investigation.
            "GeneratesEvent"              :P5RefType/generates-event?
            "HasAlarmSuppressionGroup"    (if forward? :P5RefType/has-alarm-suppression-group    :P5RefType/is-alarm-suppression-group-of)
            "HasCause"                    (if forward? :P5RefType/has-cause                      :P5RefType/is-cause-of)
            "HasComponent"                (if forward? :P5RefType/has-component                  :P5RefType/is-component-of)
            "HasCondition"                (if forward? :P5RefType/has-condition                  :P5RefType/is-condition-of)
            "HasDescription"              (if forward? :P5RefType/has-description                :P5RefType/is-description-of)
            "HasEffect"                   (if forward? :P5RefType/has-effect                     :P5RefType/is-effect-of)
            "HasEncoding"                 (if forward? :P5RefType/has-encoding                   :P5RefType/is-encoding-of)
            "HasInterface"                (if forward? :P5RefType/has-interface                  :P5RefType/is-interface-of)
            "HasModellingRule"            (if forward? :P5RefType/has-modeling-rule              :P5RefType/is-modeling-rule-of)
            "HasOrderedComponent"         (if forward? :P5RefType/has-ordered-component          :P5RefType/is-ordered-component-of)
            "HasProperty"                 (if forward? :P5RefType/has-property                   :P5RefType/is-property-of)
            "HasSubtype"                  (if forward? :P5RefType/has-subtype                    :P5RefType/is-subtype-of) ; Of course, more of these!
            "HasTrueSubState"             (if forward? :P5RefType/has-true-substate              :P5RefType/is-true-substate-of) ; checked
            "HasTypeDefinition"           (if forward? :P5RefType/has-type-definition            :P5RefType/is-type-definition-of)
            "Organizes"                   (if forward? :P5RefType/origanizes                     :P5RefType/of-organization)
            "ToState"                     (if forward? :P5RefType/to-state                       :P5RefType/from-transition) ; checked
            nil)
          result (or result (when (re-matches #"^i=\d+$" ReferenceType)
                              {:!UAFwdRef/id ReferenceType}))]
      (or result (log! :warn (str "No such ReferenceType: " ReferenceType))))))

(defparse :p5/References
  "Returns a map with one key :Node/reference
   Value is a vector of 2-place vectors [<ref-name keyword> <index-string>].
   Note use of :Node/references despite references not being attributes of a node class per Table 17."
  [xmap]
  {:Node/references (->> xmap :xml/content (mapv #(rewrite-xml % :p5/Reference)))})

(defparse :p5/Reference
  "Returns a 2-place vectors [<ref-name keyword> <index-string>].
   We don't use the usual single-entry maps because can have more than one ref of the same type."
  [xmap]
  [(-> xmap :xml/attrs ref-type) (:xml/content xmap)])

;;; ------------------------- Content of node classes except :p5/Value (return maps to merge) -------------------
(defparse :p5/AccessLevel         "doc" [{:xml/keys [content]}] {:Node/access-level content})
(defparse :p5/AccessRestrictions  "doc" [{:xml/keys [content]}] {:Node/access-level-restictions content})
(defparse :p5/ArrayDimensions     "doc" [{:xml/keys [content]}] {:Node/array-dimensions (edn/read-string content)})
(defparse :p5/BrowseName          "doc" [{:xml/keys [content]}] {:Node/browse-name content})
(defparse :p5/Category            "doc" [{:xml/keys [content]}] {:Node/category content})
(defparse :p5/DataType            "doc" [{:xml/keys [content]}] {:Node/data-type content})
(defparse :p5/Description         "doc" [{:xml/keys [content]}] {:Node/description content})
(defparse :p5/DisplayName         "doc" [{:xml/keys [content]}] {:Node/display-name content})   ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/Documentation       "doc" [{:xml/keys [content]}] {:Node/documentation content})  ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/EventNotifier       "doc" [{:xml/keys [content]}] {:Node/event-notifier content}) ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/InverseName         "doc" [{:xml/keys [content]}] {:Node/inverse-name content})
(defparse :p5/IsAbstract          "doc" [{:xml/keys [content]}] {:Node/is-abtract? (if (= "false" content) false true)})
(defparse :p5/IsOptionSet         "doc" [{:xml/keys [content]}] {:Node/is-option-set? (if (= "false" content) false true)})
(defparse :p5/MethodDeclarationId "doc" [{:xml/keys [content]}] {:Node/method-declaration-id content})
(defparse :p5/NodeId              "doc" [{:xml/keys [content]}] {:Node/id content})
(defparse :p5/ParentNodeId        "doc" [{:xml/keys [content]}] {:Node/parent-node-id content}) ; Not in Table 17, but I'm putting it on the node.
(defparse :p5/Purpose             "doc" [{:xml/keys [content]}] {:Node/purpose content}) ; This is only on UADataType AFAICS.
(defparse :p5/ReleaseStatus       "doc" [{:xml/keys [content]}] {:Node/release-status content})
(defparse :p5/RolePermissions     "doc" [{:xml/keys [content]}] {:Node/role-permissions content})
(defparse :p5/SymbolicName        "doc" [{:xml/keys [content]}] {:Node/symbolic-name content}) ; This is only on UAObjectType AFAICS.
(defparse :p5/Symmetric           "doc" [{:xml/keys [content]}] {:Node/symmetric? (if (= "false" content) false true)})
(defparse :p5/ValueRank           "doc" [{:xml/keys [content]}] {:Node/value-rank (edn/read-string content)})

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
  (reduce (fn [r c] (assoc r (keyword "field" (-> c :xml/tag name)) (:xml/content c)))
          {}
          (-> xmap xml-attrs-as-content :xml/content)))

(defparse :p5/Name
  "AFAICS, this is only used in Definitions in UADataTypes"
  [{:xml/keys [content]}]
  content)
;;;---------------------------- Value (often an ExtensionObject, datetime, list of strings -------------------------------------------
(defparse :p5/Value
  "Returns the map with one key, :Node/value.
   AFAICS, these have a single child and no attrs."
  [{:xml/keys [content attrs] :as _xmap}]
  (when (or (not= 1 (count content))
            (not-empty attrs))
    (log! :warn "p5/Value not as expected."))
  {:Node/value (rewrite-xml (first content))})

;;; --------------------------- ExtensionObject and other UA types ------------------------------------------------------------------
;;; I think the best thing to do here is to try to parse it and if it fails, store it as :UAExtObj/object-string (or some such thing).
(def ext-keys (atom #{}))

(defparse :uaTypes/ExtensionObject
  "Return an object network in the UAExtObj namespace.
   Extension objects, of course, can have anything in them. I have in mind parsing them to nested map structures, stringified,
   if they vary from what I've seen in Part 5 XML." ; ToDo: Maybe there are parts that belong in ordinary object namespaces?
  [xmap]
  (letfn [(ekeys [obj] (cond (map? obj)     (doseq [[k v] obj] (swap! ext-keys conj k) (ekeys v))
                             (vector? obj)  (doseq [x obj] (ekeys x))))]
    (-> xmap xml-attrs-as-content ekeys))
  {:hey! :extension-obj-nyi}) ; <==================================================================


(def eee #:xml{:tag :uaTypes/ExtensionObject,
               :content
               [#:xml{:tag :uaTypes/TypeId, :content [#:xml{:tag :uaTypes/Identifier, :content "i=7616"}]}
                #:xml{:tag :uaTypes/Body,
                      :content
                      [#:xml{:tag :uaTypes/EnumValueType,
                             :content
                             [#:xml{:tag :uaTypes/Value, :content "1"}
                              #:xml{:tag :uaTypes/DisplayName,
                                    :content [#:xml{:tag :uaTypes/Text, :content "Mandatory"}]}
                              #:xml{:tag :uaTypes/Description,
                                    :content
                                    [#:xml{:tag :uaTypes/Text,
                                           :content
                                           "The BrowseName must appear in all instances of the type."}]}]}]}]})

;;; ToDo: Needs investigation. I'm not wrapping any of these. I'm not defining :UATypes/{String, DateTime, Boolean, Int32, etc.}
;;;       At least :UATypes/LocalizedText can use a reader...when I see the right kind of example...;^)
(defparse :UATypes/Boolean       "doc" [{:xml/keys [content]}]  (-> content edn/read-string boolean))
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
      Locale (assoc :P3LocalizedText/Locale (rewrite-xml Locale :UATypes/Text)))))

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
(defn db-type-of
  "Return a Datahike schema :db/valueType object for the argument"
  [obj]
  (cond (string? obj)  :db.type/string
        (number? obj)  :db.type/number
        (keyword? obj) :db.type/keyword
        (map? obj)     :db.type/ref
        (boolean? obj) :db.type/boolean))

(defn sample-vec
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

;;; ToDo: Pull out all the :redex/ stuff.
(defn schema-for-db
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


;;; (-> "data/part5/p5.edn" slurp edn/read-string core/learn-schema)
(defn learn-schema
  "Return DH/DS schema objects for the data provided.
   Limitation: It can't learn from binding sets; the attributes of those are not the
   data's attributes, and everything will appear as multiplicity 1."
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
                (if (and typ (not= typ this-typ))
                  (log! :warn (str "Different types: " k "first: " typ "second: " this-typ))
                      (swap! learned #(-> %
                                          (assoc-in [k :db/cardinality] this-card)
                                          (assoc-in [k :db/valueType] this-typ))))))
             (lsw-aux [obj]
               (cond (map? obj) (doall (map (fn [[k v]]
                                             (update-learned! k v)
                                             (when (coll? v) (lsw-aux v)))
                                           obj))
                    (coll? obj) (doall (map lsw-aux obj))))]
      (lsw-aux data)
      (schema-for-db @learned (if datahike? :datahike :datascript)))))


;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
