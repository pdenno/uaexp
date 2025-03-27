(ns ua.core
  "Toplevel of uaexp."
  (:require
   [clojure.data.xml            :as x]
   [mount.core                  :as mount :refer [defstate]]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.db-util                  :as dbu]
   [ua.xml-util                 :as xu :refer [read-xml]]
   [ua.util                     :refer [util-state]])) ; For mount

;;; ToDo: Most stuff in here belongs in a new file part5_db.clj.
;;; ToDo: It is not essential to use defparse or even rewrite-xml. You can check for M/O attributes using attr-status.
;;;       If you went this route, you'd still have to deal with aliases, and extensions, however.
;;;       So it might be neater to use defparse. You only need about a dozen methods.

;;; ToDo: Add :Node/type <======================================================================================================================================================================

(def debugging? (atom false))
(def diag (atom false))

(def nyi (atom #{}))

;;; All the tags in :uaTypes are from http://opcfoundation.org/UA/2008/02/Types.xsd
;;;      <DateTime xmlns="http://opcfoundation.org/UA/2008/02/Types.xsd">2025-01-08T00:00:00Z</DateTime>
;;;      <ListOfExtensionObject xmlns="http://opcfoundation.org/UA/2008/02/Types.xsd">
;;;
;;; They can have substructure:
;;;      <ListOfInt32 xmlns="http://opcfoundation.org/UA/2008/02/Types.xsd">
;;;        <Int32>0</Int32>
;;;      </ListOfInt32>
;;;
;;; I have not found one yet where the substructure wasn't ?X for ListOf?X.
;;;      <ListOfExtensionObject xmlns="http://opcfoundation.org/UA/2008/02/Types.xsd">
;;;        <ExtensionObject>
;;;          <TypeId>
;;;            <Identifier>i=297</Identifier>
;;;          </TypeId>
;;;          <Body>
;;;            <Argument>
;;;              <Name>SubscriptionId</Name>
;;;              <DataType>
;;;                <Identifier>i=7</Identifier>
;;;              </DataType>
;;;              <ValueRank>-1</ValueRank>
;;;              <ArrayDimensions />
;;;            </Argument>
;;;          </Body>
;;;        </ExtensionObject>
;;;      </ListOfExtensionObject>
;;;
;;; We need to find all the tags that are inside these ExtensionObjects Body, Argument...
;;; Those really shouldn't be in :uaTypes UNLESS they really ARE a known attribute (e.g. ValueRank, ArrayDimensions, DataType, but not "Body").
;;; (New NS "extObj")

;;;      <LocalizedText xmlns="http://opcfoundation.org/UA/2008/02/Types.xsd">
;;;        <Locale>en</Locale>
;;;        <Text>Enabled</Text>
;;;      </LocalizedText>


;;; Here I don't know what to do with Category (everywhere) but also Definition and Field. Maybe these should be part of a UADataType object?
;;;  <UADataType NodeId="i=14533" BrowseName="KeyValuePair">
;;;    <DisplayName>KeyValuePair</DisplayName>
;;;    <Category>Base Info KeyValuePair</Category>
;;;    <Documentation>https://reference.opcfoundation.org/v105/Core/docs/Part5/12.21</Documentation>
;;;    <References>
;;;      <Reference ReferenceType="HasSubtype" IsForward="false">i=22</Reference>
;;;    </References>
;;;    <Definition Name="KeyValuePair">
;;;      <Field Name="Key" DataType="i=20" AllowSubTypes="true"/>
;;;      <Field Name="Value" />
;;;    </Definition>
;;;  </UADataType>

;;; Should I resolve aliases? NO. NodeId is going to be :db.unique/identity.

;;; I should distinguish the NodeClass Attributes (Table 17 in Clause 5.9 of Part 3) from Types.xsd things now currently namespace uaTypes.
;;; Thus :Node/id :Node/AccessLevel :Node/AccessLevelEx etc. I think I should add :Node/ReleaseStatus

(def tags&attrs
  "These are the tags and attributes found in Part 5 1.05.04. I use this to plan parsing.
   There is no :p5 objects (namespaces) in the DB. There are instead, UANodeSet, UAAlias UA<some node class>, UAType, UAReference, UADefinition, and UAExtObj."
  {:tags
   #{:p5/Alias                      ; defparse to :UAAlias/...
     :p5/Aliases                    ; defparse to :UANodeset/Aliases ... Everything that is not :UANodeSet/Aliases or :UANodeSet/Models is :UANodeset/content ???
     :uaTypes/Argument              ; :UAExtObj
     :uaTypes/ArrayDimensions       ; UA Attribute
     :uaTypes/Body                  ; :UAExtObj
     :uaTypes/Boolean               ; types.xsd, simple
     :uaTypes/ByteString            ; types.xsd, simple string child element?
     :p5/Category                   ; <Just Text, I've seen it in documentation, but can't find it now. I'm going to use :Node/category.
     :uaTypes/DataType              ; UA Attribute
     :uaTypes/DateTime              ; Types.xsd, simple
     :p5/Definition                 ; :UADataType/Definition <==================  Maybe UADefinition as a Namespace????
     :uaTypes/Description           ; UA Attribute
     :p5/Description                ; UA Attribute
     :uaTypes/DisplayName           ; UA Attribute <==================== Should be :p5 Need to fix this <==============================
     :p5/DisplayName                ; UA Attribute
     :p5/Documentation              ; <Just Text, I've seen it in documentation, but can't find it now. Maybe keep in :p5/Documentation in DB.>
     :uaTypes/EUInformation         ; :UAExtObj (for CEFACT stuff, at least)
     :uaTypes/EnumValueType         ; :UAExtObj
     :uaTypes/ExtensionObject       ; :UAExtObj
     :p5/Field                      ; :UADataType/Definition <================== ?
     :uaTypes/Identifier            ; :UAExtObj
     :uaTypes/Int32                 ; Types.xsd
     :p5/InverseName                ; UA Attribute
     :uaTypes/ListOfExtensionObject ; <plural> Don't keep
     :uaTypes/ListOfInt32           ; <plural> Don't keep
     :uaTypes/ListOfLocalizedText   ; <plural> Don't keep
     :uaTypes/ListOfString          ; <plural? Don't keep
     :uaTypes/Locale                ; Types.xsd <================================== Need to check this.
     :uaTypes/LocalizedText         ; Types.xsd, simple
     :p5/Model                      ; defparse to :UANodeSet/Models
     :p5/Models                     ; defparse <plural> Don't keep.
     :uaTypes/Name                  ; :UAExtObj
     :uaTypes/NamespaceUri          ; ignore
     :p5/Reference                  ; <Reference!> Don't keep
     :p5/References                 ; <plural> :db/cardinality :db/many
     :p5/RolePermission             ; <Singular>
     :p5/RolePermissions            ; UA Attribute
     :uaTypes/String                ; Types.xsd
     :uaTypes/Text                  ; :UAExtObj <================================= Need to check this.
     :uaTypes/TypeId                ; :UAExtObj
     :p5/UADataType                 ; NodeClass It's own defparse? Makes :UADataType/<attributes>
     :p5/UAMethod                   ; NodeClass
     :p5/UANodeSet                  ; defparse  Have a UANodeSet object in the DB. Container for all of P5 and likewise for profiles
     :p5/UAObject                   ; NodeClass
     :p5/UAObjectType               ; NodeClass
     :p5/UAReferenceType            ; NodeClass
     :p5/UAVariable                 ; NodeClass
     :p5/UAVariableType             ; NodeClass
     :uaTypes/UInt32                ; Types.xsd
     :uaTypes/UnitId                ; :UAExtObj (Part of EUInformation)
     :uaTypes/Value                 ; :UAExtObj  (or should I let it slide as a UA Attribute? Sometimes an XML attr.)
     :p5/Value                      ; UA Attribute (sometimes an attr)
     :uaTypes/ValueRank},           ; UA Attribute (sometimes an attr)

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
  "Mandatory and optional attributes of the 8 NodeClasses. This was created from Part 3 Clause 5.9 Table 7.
   Note that attributes need not be encoded as XML attributes. The commented values are because things can default."
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

(def is-elem? #{:RolePermissions, :UserRolePermissions})

(def part3-attr?
  "A set/predicate for the Part 3 attributes."
  (let [res (atom #{})]
    (doseq [v (vals attr-status)]
      (swap! res into (:mandatory v))
      (swap! res into (:optional v)))
    @res))

;;; ToDo: This needs work! There are more attrs than just those in Table 17. Also, this is probably the place to assign correct namespaces.
;;;       Decisions are made, in part, based on the element tag. Certainly ExtensionObject needs special processing.
(defn process-part3-attrs
  [xmap]
  (reduce-kv (fn [m k v]
               (if (part3-attr? k)
                 (if (#{:isAbstract :isForward :IsOptionalSet} k)
                   (assoc m (keyword "p3" (name k)) (if (= v "true") true false))
                   (assoc m (keyword "p3" (name k)) v))
                 (log! :warn (str "Unknown attribute " k))))
          {}
          (:xml/atrs xmap)))

(defn rewrite-xml-dispatch
  [obj & [specified]]
  (cond ;; Optional 2nd argument specifies method to call
    (keyword? specified)                        specified,
    (and (map? obj) (contains? obj :xml/tag))   (:xml/tag obj)
    :else (throw (ex-info "No method for obj: " {:obj obj}))))

(defmulti rewrite-xml #'rewrite-xml-dispatch)

(defn call-this [arg]
  (reset! diag arg))

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

;;; ToDo: I think it is pretty odd that we call process-attrs-map here, especially so because
;;;       sometimes specific attrs are mapped again, differently.
#_(defmacro defparse [tag [arg _props] & body]
  `(defmethod rewrite-xml ~tag [~arg & ~'_]
     ;; Once *skip-doc-processing?* is true, it stays so through the dynamic scope of the where it was set.
     (when @debugging? (println "defparse tag = " ~tag))
       (let [result# (do ~@body)]
         (cond-> result#
           (:xml/attrs result#) (-> (assoc :xml/attributes (-> result# :xml/attrs process-attrs-map))
                                    (dissoc :xml/attrs))))))

(defmacro defparse [tag & others]
  (let [doc-string (when (and (-> others first string?)
                              (-> others second vector?))
                     (first others))
        arg  (if doc-string (nth others 1) (first others))
        body (if doc-string (nthrest others 2) (nthrest others 1))]
  `(defmethod rewrite-xml ~tag [~@arg & ~'_]
     ;; Once *skip-doc-processing?* is true, it stays so through the dynamic scope of the where it was set.
     (when @debugging? (println "defparse tag = " ~tag))
       (let [result# (do ~@body)]
         (cond-> result#
           (:xml/attrs result#) (-> (assoc :xml/attributes (-> result# :xml/attrs process-attrs-map))
                                    (dissoc :xml/attrs)))))))


(def ^:diag dt #:xml{:tag :p5/UADataType,
                     :attrs {:NodeId "i=26", :BrowseName "Number", :IsAbstract "true"},
                     :content
                     [#:xml{:tag :p5/DisplayName, :content "Number"}
                     ; #:xml{:tag :p5/UnknownTag, :content "Number"}
                      #:xml{:tag :p5/Category, :content "Base Info Base Types"}
                      #:xml{:tag :p5/Documentation, :content "https://reference.opcfoundation.org/v105/Core/docs/Part5/12.2.9"}
                      #:xml{:tag :p5/References,
                            :content [#:xml{:tag :p5/Reference, :attrs {:ReferenceType "HasSubtype", :IsForward "false"}, :content "i=24"}]}]})

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

#_(defn names-only
  "Rewrite with qualified keywords values as strings. Doesn't touch keys."
  [x]
  (cond (map? x)     (reduce-kv (fn [m k v] (if (keyword? v) (assoc m k (name v)) (assoc m k (names-only v)))) {} x)
        (vector? x)  (mapv names-only x)
        :else        x))

;;; A nice thing about node classes is that they don't contain other node-classes.
(defn analyze-node-xml
  "Return a map for a node, making attributes properties, checking for mandatory properties, and
   setting the namespaces of everything to the proper UA concept."
  [{:xml/keys [tag] :as _xml}]
  (assert (node-class? tag)))

;;; ============================ Defparse ================================================
;;; ToDo: xu/xml-group-by

;;; ----------------- NodeSet -------------------------------------------------------------------------
(defparse :p5/UANodeSet
  [xmap]
  (reset! diag xmap)
  (->> xmap :xml/content (mapv rewrite-xml)))

(defparse :p5/Alias
  [xmap]
  {:Alias/name (-> xmap :xml/attrs :Alias)
   :Alias/id (:xml/content xmap)})

(defparse :p5/Aliases
  "Docstring"
  [xmap]
  (->> xmap :xml/content (mapv rewrite-xml)))

(defparse :p5/Model
  [xmap]
  :p5/Model)

(defparse :p5/Models
  [xmap]
  :p5/Models)

;;; ----------------- Node Classes -------------------------------------------------------------------------
;;; ToDo: If these all do the same thing (still in devl) update the dispatcher...

(defparse :p5/UADataType
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAMethod
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAObject
  [xmap]
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAObjectType
  [xmap]
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAReferenceType
  [xmap]
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAVariable
  [xmap]
  "This just merges small pieces."
  [xmap]
  (reset! diag xmap)
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAVariableType
  [xmap]
  "This just merges small pieces."
  [xmap]
  (let [{:xml/keys [content]} (xml-attrs-as-content xmap)]
    (reduce (fn [res c] (merge res (rewrite-xml c))) {} content)))

(defparse :p5/UAView
  "There are none of these in P5 (nor probably anywhere else!)."
  [_xmap]
  (throw (ex-info "UAView!" {})))

;;; -------------------------- Other ----------------------------------------------------------------
(defn ref-type
  [{:keys [ReferenceType IsForward]}]
  (let [forward? (not= IsForward "false")]
    (let [result (case ReferenceType
                   "FromState"           (if forward? :ref/from-state             :ref/to-state)      ; ToDo: Needs investigation.
                   "HasComponent"        (if forward? :ref/has-component          :ref/component-of)
                   "HasEffect"           (if forward? :ref/has-effect             :ref/effect-of)
                   "HasModellingRule"    (if forward? :ref/has-modeling-rule      :ref/modeling-rule-of)
                   "HasOrderedComponent" (if forward? :ref/has-ordered-component  :ref/ordered-component-of)
                   "HasProperty"         (if forward? :ref/has-property           :ref/property-of)
                   "HasSubtype"          (if forward? :ref/has-subtype            :ref/subtype-of) ; Of course, more of these!
                   "HasTypeDefinition"   (if forward? :ref/has-type-definition    :ref/type-definition-of)
                   "Organizes"           (if forward? :ref/origanizes             :ref/of-organization)
                   "ToState"             (if forward? :ref/to-state               :ref/from-state)
                   nil)]
      (or result (log! :warn (str "No such type: " ReferenceType))))))

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



;;; ------------------------- Content of node classes (return maps to merge) -----------------------------------------------
(defparse :p5/BrowseName    [{:xml/keys [content]}] {:Node/browse-name content})
(defparse :p5/Category      [{:xml/keys [content]}] {:Node/category content})
(defparse :p5/DisplayName   [{:xml/keys [content]}] {:Node/display-name content})   ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/Documentation [{:xml/keys [content]}] {:Node/documentation content})  ; ToDo: I don't think we can depend on it being a simple text string.
(defparse :p5/IsAbstract    [{:xml/keys [content]}] {:Node/id (if (= "false" content) false true)})
(defparse :p5/NodeId        [{:xml/keys [content]}] {:Node/id content})

;;; --------------------------- Definition ----------------------------------------------------------
(def ddd  #:xml{:tag :p5/Definition,
                :attrs {:Name "NamingRuleType"},
                :content
                [#:xml{:tag :p5/Field,
                       :attrs {:Name "Mandatory", :Value "1"},
                       :content [#:xml{:tag :p5/Description, :content "The BrowseName must appear in all instances of the type."}]}
                 #:xml{:tag :p5/Field,
                       :attrs {:Name "Optional", :Value "2"},
                       :content [#:xml{:tag :p5/Description, :content "The BrowseName may appear in an instance of the type."}]}
                 #:xml{:tag :p5/Field,
                       :attrs {:Name "Constraint", :Value "3"},
                       :content
                       [#:xml{:tag :p5/Description,
                              :content
                              "The modelling rule defines a constraint and the BrowseName is not used in an instance of the type."}]}]})


(defparse :p5/Definition ; ToDo: Investigate further.
  "Definitions seem to have fields with values and descriptions. Everything here will be in NS def."
  [xmap]
  (let [content-map (->> xmap xml-attrs-as-content :xml/content (group-by :xml/tag))
        fields (:p5/Field content-map)
        names  (:p5/Name content-map)]
    (when-not (every? #(#{:p5/Name :p5/Field} %) (keys content-map))
      (log! :warn (str "p5/Definition is irregular. Keys = " (keys content-map))))
    (when-not (== 1 (-> content-map :p5/Name count))
      (log! :warn (str "p5/Definition is irregular. Names = " (:p5/Name content-map))))
    (cond-> {:Definition/name (rewrite-xml (first names))}
      fields (assoc :Definition/fields (mapv #(rewrite-xml % :p5/Field) fields)))))

(defparse :p5/Field
  "Return a map with the keys in namespace 'field'. Used in :p5/Definition
   Field typically has Description, Name, and Value." ; ToDo: Warn on irregularities.
  [xmap]
  (reduce (fn [r c] (assoc r (keyword "field" (-> c :xml/tag name)) (:xml/content c)))
          {}
          (-> xmap xml-attrs-as-content :xml/content)))

;;; --------------------------- Types assumed to be strings (and settting namespace) -------------------------------------------------
(defparse :p5/AccessLevel "Assumed string" [{:xml/keys [content]}] (assert (string? content))  {:Node/access-level content})
(defparse :p5/Description "Assumed string" [{:xml/keys [content]}] (assert (string? content))  {:Node/description content})
(defparse :p5/Name          "Used in :p5/Definition." [{:xml/keys [content]}]  (assert (string? content)) {:Definition/name content})
(defparse :p5/ParentNodeId  "Used in :p5/Definition." [{:xml/keys [content]}]  (assert (string? content)) {:Node/parent-node-id content})


;;; --------------------------- ExtensionObject --------------------------------------------------------------------------------------
;;; I think the best thing to do here is to try to parse it and if it fails, store it as :UAExtObj/object-string (or some such thing).

#:xml{:tag :uaTypes/ExtensionObject,
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
                                                                "The BrowseName must appear in all instances of the type."}]}]}]}]}
(defparse :uaTypes/ExtensionObject
  "Return an object network in the UAExtObj namespace." ; ToDo: Maybe there are parts that belong in ordinary object namespaces?
  [xmap]
  :ExtensionObject-nyi)

#{:p5/EventNotifier
  :p5/ValueRank
  :p5/InverseName
  :p5/ReleaseStatus
  :p5/Symmetric
  :p5/RolePermissions
  :p5/ArrayDimensions
  :p5/Description
  :p5/Purpose
  :p5/SymbolicName
  :p5/Name
  :p5/AccessLevel
  :p5/Value
  :p5/MethodDeclarationId
  :p5/DataType
  :p5/ParentNodeId
  :p5/AccessRestrictions}

;;; --------------------------- Lists ---------------------------------------------------------------
(defparse :uaTypes/ListOfExtensionObject
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :uaTypes/ExtensionObject))))

(defparse :uaTypes/ListOfInt32
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :uaTypes/Int32))))

(defparse :uaTypes/ListOfLocalizedText
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :uaTypes/LocalizedText))))

(defparse :uaTypes/ListOfString
  [xmap]
  (->> xmap :xml/content (mapv #(rewrite-xml % :uaTypes/String))))


;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
