(ns ua.p5-cardinality
  "Information about Part 5 references.")

;;; ToDo:
;;;   - Because we really aren't certain we have reasonable cardinality for everything here. We might want to log changes to a properties that have cardinality one for a while!
;;;   - Similarly, when we learn schema in node sets, we ought to check whether what is inferred differs from what we have here.

(def card-table-vec
  "Provide the cardinality of the forward and inverse references as either :db.cardinality/one or :db.cardinality/many.
   The values are guesses based on what we think the semantics should be.
   Note that not every ReferenceType has an inverse."
  [{:browse-name "Aggregates", :cardinality :db.cardinality/one, :inverse-name "AggregatedBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "AlarmGroupMember", :cardinality :db.cardinality/many, :inverse-name "MemberOfAlarmGroup", :inverse-cardinality :db.cardinality/one}
   {:browse-name "AlarmSuppressionGroupMember", :cardinality :db.cardinality/many, :inverse-name "MemberOfAlarmSuppressionGroup", :inverse-cardinality :db.cardinality/one}
   {:browse-name "AliasFor", :cardinality :db.cardinality/one, :inverse-name "HasAlias", :inverse-cardinality :db.cardinality/many}
   {:browse-name "AlwaysGeneratesEvent", :cardinality :db.cardinality/many, :inverse-name "AlwaysGeneratedBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "AssociatedWith", :cardinality :db.cardinality/many}
   {:browse-name "Controls", :cardinality :db.cardinality/many, :inverse-name "IsControlledBy", :inverse-cardinality :db.cardinality/one}
   {:browse-name "DataSetToWriter", :cardinality :db.cardinality/one, :inverse-name "WriterToDataSet", :inverse-cardinality :db.cardinality/one}
   {:browse-name "FromState", :cardinality :db.cardinality/one, :inverse-name "ToTransition", :inverse-cardinality :db.cardinality/one}
   {:browse-name "GeneratesEvent", :cardinality :db.cardinality/many, :inverse-name "GeneratedBy", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasAddIn", :cardinality :db.cardinality/many, :inverse-name "AddInOf", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasAlarmSuppressionGroup", :cardinality :db.cardinality/one, :inverse-name "IsAlarmSuppressionGroupOf", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasArgumentDescription", :cardinality :db.cardinality/one, :inverse-name "ArgumentDescriptionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasAttachedComponent", :cardinality :db.cardinality/many, :inverse-name "AttachedComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasCause", :cardinality :db.cardinality/one, :inverse-name "MayBeCausedBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasChild", :cardinality :db.cardinality/many, :inverse-name "ChildOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasComponent", :cardinality :db.cardinality/many, :inverse-name "ComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasCondition", :cardinality :db.cardinality/one, :inverse-name "IsConditionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasContainedComponent", :cardinality :db.cardinality/many, :inverse-name "ContainedComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasCurrentData", :cardinality :db.cardinality/one, :inverse-name "HasHistoricalData", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasCurrentEvent", :cardinality :db.cardinality/one, :inverse-name "HasHistoricalEvent", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasDataSetReader", :cardinality :db.cardinality/many, :inverse-name "IsReaderInGroup", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasDataSetWriter", :cardinality :db.cardinality/many, :inverse-name "IsWriterInGroup", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasDescription", :cardinality :db.cardinality/one, :inverse-name "DescriptionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasDictionaryEntry", :cardinality :db.cardinality/one, :inverse-name "DictionaryEntryOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasEffect", :cardinality :db.cardinality/many, :inverse-name "MayBeEffectedBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasEffectDisable", :cardinality :db.cardinality/many, :inverse-name "MayBeDisabledBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasEffectEnable", :cardinality :db.cardinality/many, :inverse-name "MayBeEnabledBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasEffectSuppressed", :cardinality :db.cardinality/many, :inverse-name "MayBeSuppressedBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasEffectUnsuppressed", :cardinality :db.cardinality/many, :inverse-name "MayBeUnsuppressedBy",    :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasEncoding", :cardinality :db.cardinality/many, :inverse-name "EncodingOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasEngineeringUnitDetails", :cardinality :db.cardinality/many, :inverse-name "EngineeringUnitDetailsOf",  :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasEventSource", :cardinality :db.cardinality/one, :inverse-name "EventSourceOf", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasFalseSubState",  :cardinality :db.cardinality/many, :inverse-name "IsFalseSubStateOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasGuard", :cardinality :db.cardinality/many, :inverse-name "GuardOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasHistoricalConfiguration", :cardinality :db.cardinality/many, :inverse-name "HistoricalConfigurationOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasInterface", :cardinality :db.cardinality/many, :inverse-name "InterfaceOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasKeyValueDescription", :cardinality :db.cardinality/one, :inverse-name "KeyValueDescriptionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasLowerLayerInterface", :cardinality :db.cardinality/many, :inverse-name "HasHigherLayerInterface", :inverse-cardinality :db.cardinality/one} ; investigate
   {:browse-name "HasModellingRule", :cardinality :db.cardinality/many, :inverse-name "ModellingRuleOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasNotifier", :cardinality :db.cardinality/many, :inverse-name "NotifierOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasOptionalInputArgumentDescription", :cardinality :db.cardinality/one, :inverse-name "OptionalInputArgumentDescriptionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasOrderedComponent", :cardinality :db.cardinality/many, :inverse-name "OrderedComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasPhysicalComponent", :cardinality :db.cardinality/many, :inverse-name "PhysicalComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasProperty", :cardinality :db.cardinality/many, :inverse-name "PropertyOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasPubSubConnection", :cardinality :db.cardinality/one, :inverse-name "PubSubConnectionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasPushedSecurityGroup", :cardinality :db.cardinality/many, :inverse-name "HasPushTarget", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasQuantity", :cardinality :db.cardinality/one, :inverse-name "QuantityOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasReaderGroup", :cardinality :db.cardinality/one, :inverse-name "IsReaderGroupOf", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HasReferenceDescription", :cardinality :db.cardinality/one, :inverse-name "ReferenceDescriptionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasStructuredComponent", :cardinality :db.cardinality/many, :inverse-name "IsStructuredComponentOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasSubStateMachine", :cardinality :db.cardinality/one, :inverse-name "SubStateMachineOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasSubtype", :cardinality :db.cardinality/many, :inverse-name "SubtypeOf", :inverse-cardinality :db.cardinality/one}                       ; <======= Important
   {:browse-name "HasTrueSubState", :cardinality :db.cardinality/many, :inverse-name "IsTrueSubStateOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasTypeDefinition", :cardinality :db.cardinality/one, :inverse-name "TypeDefinitionOf", :inverse-cardinality :db.cardinality/one}
   {:browse-name "HasWriterGroup", :cardinality :db.cardinality/one, :inverse-name "IsWriterGroupOf", :inverse-cardinality :db.cardinality/many}
   {:browse-name "HierarchicalReferences", :cardinality :db.cardinality/many, :inverse-name "InverseHierarchicalReferences", :inverse-cardinality :db.cardinality/one}
   {:browse-name "IsDeprecated", :cardinality :db.cardinality/one, :inverse-name "Deprecates", :inverse-cardinality :db.cardinality/one}
   {:browse-name "IsExecutableOn", :cardinality :db.cardinality/one, :inverse-name "CanExecute", :inverse-cardinality :db.cardinality/many}
   {:browse-name "IsExecutingOn", :cardinality :db.cardinality/one, :inverse-name "Executes", :inverse-cardinality :db.cardinality/many}
   {:browse-name "IsHostedBy", :cardinality :db.cardinality/one, :inverse-name "Hosts", :inverse-cardinality :db.cardinality/many}
   {:browse-name "IsPhysicallyConnectedTo", :cardinality :db.cardinality/many}
   {:browse-name "NonHierarchicalReferences", :cardinality :db.cardinality/many}
   {:browse-name "Organizes", :cardinality :db.cardinality/many, :inverse-name "OrganizedBy", :inverse-cardinality :db.cardinality/one}
   {:browse-name "References", :cardinality :db.cardinality/many}
   {:browse-name "RepresentsSameEntityAs", :cardinality :db.cardinality/many}
   {:browse-name "RepresentsSameFunctionalityAs", :cardinality :db.cardinality/many}
   {:browse-name "RepresentsSameHardwareAs", :cardinality :db.cardinality/many}
   {:browse-name "Requires", :cardinality :db.cardinality/many, :inverse-name "IsRequiredBy", :inverse-cardinality :db.cardinality/many}
   {:browse-name "ToState", :cardinality :db.cardinality/one, :inverse-name "FromTransition", :inverse-cardinality :db.cardinality/one}
   {:browse-name "UsesPriorityMappingTable", :cardinality :db.cardinality/one, :inverse-name "UsedByNetworkInterface", :inverse-cardinality :db.cardinality/many}
   {:browse-name "Utilizes", :cardinality :db.cardinality/many, :inverse-name "IsUtilizedBy", :inverse-cardinality :db.cardinality/many}])

(def card-table
  "A map of card-table vec indexed by browse-name."
  (reduce (fn [m obj] (assoc m (:browse-name obj) obj)) {} card-table-vec))

(defn lookup-ref-type
  "Return the reference type name, a CamelCaseString, or nil if it is not found.
   If forward? is true and the type is known, the argument rtype is returned.
   If forward? is false and rtype is in the table it returns the :inverse-name of the forward entry map.
   If rtype names a valid inverse name, type is returned."
  [rtype forward?]
  (let [entry (get card-table rtype)]
    (cond (and entry forward?)                                 rtype
          entry                                                (:inverse-name entry)
          (some #(= rtype (:inverse-name %)) card-table-vec)  rtype))) ; ToDo: Should I require forward to be false here?
