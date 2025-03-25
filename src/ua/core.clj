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
     :p5/Category                   ; <Just Text, I've seen it in documentation, but can't find it now. Maybe keep in :p5/Category in DB.>
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
(def attr-status
  "Mandatory and optional attributes of the 8 NodeClasses. This was created from Part 3 Clause 5.9 Table 7."
  {"VariableType"  {:mandatory #{:ValueRank :IsAbstract :DisplayName :BrowseName :WriteMask :Value :NodeClass :NodeId :UserWriteMask :DataType},
                    :optional #{:AccessRestrictions :RolePermissions :ArrayDimensions :Description :UserRolePermissions}},
   "View"          {:mandatory #{:DisplayName :BrowseName :WriteMask :ContainsNoLoops :EventNotifier :NodeClass :NodeId :UserWriteMask},
                    :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   "DataType"      {:mandatory #{:IsAbstract :DisplayName :BrowseName :WriteMask :NodeClass :NodeId :UserWriteMask},
                    :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   "Object"        {:mandatory #{:DisplayName :BrowseName :WriteMask :EventNotifier :NodeClass :NodeId :UserWriteMask},
                    :optional #{:Description}},
   "Method"        {:mandatory #{:DisplayName :BrowseName :WriteMask :UserExecutable :NodeClass :Executable :NodeId :UserWriteMask},
                    :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   "Variable"      {:mandatory #{:AccessLevel :ValueRank :Historizing :UserAccessLevel :DisplayName :BrowseName :WriteMask :Value :NodeClass :NodeId :UserWriteMask :DataType},
                    :optional #{:UserWriteMaskEx :AccessRestrictions :MinimumSamplingInterval :RolePermissions :ArrayDimensions :WriteMaskEx :Description :UserRolePermissions :AccessLevelEx}},
   "ObjectType"    {:mandatory #{:IsAbstract :DisplayName :BrowseName :WriteMask :NodeClass :NodeId :UserWriteMask},
                    :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}},
   "ReferenceType" {:mandatory #{:InverseName :IsAbstract :DisplayName :BrowseName :WriteMask :Symmetric :NodeClass :NodeId :UserWriteMask},
                    :optional #{:AccessRestrictions :RolePermissions :Description :UserRolePermissions}}})

(defn is-elem? #{:RolePermissions, :UserRolePermissions})

(def part3-attr?
  "A set/predicate for the Part 3 attributes."
  (let [res (atom #{})]
    (doseq [v (vals attr-status)]
      (swap! res into (:mandatory v))
      (swap! res into (:optional v)))
    @res))

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
(defmacro defparse [tag [arg _props] & body]
  `(defmethod rewrite-xml ~tag [~arg & ~'_]
     ;; Once *skip-doc-processing?* is true, it stays so through the dynamic scope of the where it was set.
     (when @debugging? (println "defparse tag = " ~tag))
       (let [result# (do ~@body)]
         (cond-> result#
           (:xml/attrs result#) (-> (assoc :xml/attributes (-> result# :xml/attrs process-attrs-map))
                                    (dissoc :xml/attrs))))))

 '[#:xml{:tag :p5/UANodeSet,
        :attrs {:LastModified "2021-05-20T00:00:00Z"},
        :content
        [#:xml{:tag :p5/Aliases,
               :content
               [#:xml{:tag :p5/Alias, :attrs {:Alias "Boolean"}, :content "i=1"}
                #:xml{:tag :p5/Alias, :attrs {:Alias "HasDescription"}, :content "i=39"}]}]}]

(defparse :p5/Alias
  [xmap]
  (log! :info (str "xmap =" xmap))
  {:Alias/name (-> xmap :xml/attrs :Alias)
   :Alias/id (:xml/content xmap)})

(defparse :p5/Aliases
  [xmap]
  (log! :info (str "xmap =" xmap))
  (->> xmap :xml/content (mapv rewrite-xml)))

;;; ToDo: xu/xml-group-by
(defparse :p5/UANodeSet
  [xmap]
  (->> xmap :xml/content (mapv rewrite-xml)))


(defparse :p5/Reference
  [xmap]
  (-> (process-part3-attrs xmap)
      (assoc :Reference/id (:xml/content xmap))))

(defparse :p5/UAVariable
  [xmap]
  (let [{:keys [DisplayName Description References]} (xu/xml-group-by xmap :p5/References :p5/DisplayName :p5/Description)]
    (cond-> (process-part3-attrs xmap)
      DisplayName  (assoc :UAVariable/DisplayName (->> DisplayName first :xml/content))
      Description  (assoc :UAVariable/Description (->> Description first :xml/content))
      References   (assoc :UAVariable/References  (->> References  first :xml/content (mapv rewrite-xml)))))) ; ToDo: Not sure about first here (and maybe above either)
                                                                                                              ; Is xml-group-by doing what I want? (I think they have to be vectors).

(defparse :p5/UAObject
  [xmap]
  (let [{:keys [DisplayName Description References]} (xu/xml-group-by xmap :p5/References :p5/DisplayName :p5/Description)]
    (cond-> (process-part3-attrs xmap)
      DisplayName  (assoc :UAObject/DisplayName (->> DisplayName first :xml/content))
      Description  (assoc :UAObject/Description (->> Description first :xml/content))
      References   (assoc :UAObject/References  (->> References  first :xml/content (mapv rewrite-xml))))))

(defparse :p5/UAMethod
  [xmap]
  (let [{:keys [DisplayName Description References]} (xu/xml-group-by xmap :p5/References :p5/DisplayName :p5/Description)]
    (cond-> (process-part3-attrs xmap)
      DisplayName  (assoc :UAMethod/DisplayName (->> DisplayName first :xml/content))
      Description  (assoc :UAMethod/Description (->> Description first :xml/content))
      References   (assoc :UAMethod/References  (->> References  first :xml/content (mapv rewrite-xml))))))



;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
