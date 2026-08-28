(ns ua.profiles
  "Functions to read any profile and create a Part5-based DB for it."
  (:require
   [clojure.edn                 :as edn]
   [clojure.java.io             :as io]
   [clojure.pprint              :refer [pprint]]
   [clojure.set                 :as set]
   [clojure.string              :as str]
   [datahike.api                :as d]
   [taoensso.telemere           :as log :refer [log!]]
   [ua.p5-cardinality           :as p5-card]
   [ua.db-util                  :as dbu :refer [connect-atm db-cfg-map register-db]]
   [ua.nsuri                    :as nsuri]
   [ua.putil                    :as pu :refer [defparse learn-schema-basic write-nodeset-edn! create-ua-db!]]))

(def ^:diag diag (atom nil))

(def ignored-nodes "I'm not sure whether these are worth storing in the DB." (atom []))

(declare make-schema+)

(defn ^:admin make-store!
  "Create the store for one nodeset EDN file. There is no dependency order to get right and no
   other nodeset that has to be present: references outside this nodeset's own namespace are
   recorded as foreign keys, and whether they resolve is a later question, answered by whether
   that namespace's store is registered. Returns the store's {:prefix .. :version ..}."
  [nodeset-edn-file & {:keys [schema+]}]
  (let [nodeset (-> nodeset-edn-file slurp edn/read-string)
        cfg (pu/create-ua-db! :nodeset nodeset :schema+ (or schema+ {}))
        foreign (-> nodeset nsuri/canonicalize-nodeset nsuri/foreign-addresses)]
    (log! :info (str (last (str/split nodeset-edn-file #"/")) ": "
                     (count foreign) " foreign key(s) into "
                     (->> foreign (map #(:uri (nsuri/parse-address %))) distinct count) " namespace(s)."))
    (:db-id cfg)))

(defn ^:admin  make-profile-db!
  "Create a Part5-based DB for the argument node set. Arguments:
     :xml-file  - the XML file defining the nodeset
     :nodeset-edn-file - a file of the data of the nodeset, loaded into the DB.
     :schema+-file - a file of schema in schema+ format learned from the nodeset.
     :schema-key - a keyword used to register the db, and for use with connect-atm.
     :create-db? - whether to create the db or just stop at creating the schema+ file. (Defaults to true.)
     :make-schema+-file? - whether to make the schema-file with the name or use the file at the name."
  [{:keys [schema-key xml-file nodeset-edn-file schema+-file]}]
  (assert (keyword? schema-key))
  ;; Without an :xml-file there is nothing to generate from, so the EDN has to be there already.
  (when-not xml-file
    (assert nodeset-edn-file "Provide :xml-file to generate the nodeset EDN, or :nodeset-edn-file naming an existing one.")
    (assert (-> nodeset-edn-file io/file .exists) (str "No such nodeset EDN file: " nodeset-edn-file)))
  (reset! ignored-nodes [])
  (when xml-file
    (write-nodeset-edn! xml-file nodeset-edn-file))
  ;; ToDo: :make-schema+-file? (see docstring) is not implemented, so a :schema+-file is always
  ;;       regenerated here and then read back. Deciding generate-vs-use is the open question.
  (when schema+-file
    (make-schema+ schema-key nodeset-edn-file schema+-file))
  (pu/create-ua-db!
   :schema+ (if schema+-file (-> schema+-file slurp edn/read-string) {})
   :nodeset (-> nodeset-edn-file slurp edn/read-string)   ; create-ua-db! wants the nodeset, not its filename.
   :db-id schema-key))

;;; --------- These were encountered in nodesets other than Part 5. (Just AMB so far.)
(defparse :p5/NamespaceUris
  "Return the nodeset's namespace table. These are what make a NodeId's ns=<index> meaningful:
   the index is file-local, the URI is not. See ua.nsuri."
  [xmap]
  {:NodeSet/namespace-uris (->> xmap :xml/content (mapv :xml/content))})

(defparse :p5/Extensions
  "There is one of these in AMB, for example, it is a reference to the tool that built the profile."
  [xmap]
  (let [n {:Node/type :ignore
           :ignore/content (str xmap)}]
    (swap! ignored-nodes conj n)
    n))

(defparse :UATypes/QualifiedName
  "A QualifiedName is a name scoped by a namespace index. Found in AMB and MachineTool, not P5.
   The index is file-local, so ua.nsuri rewrites :P3QualifiedName/namespace-uri at load."
  [{:xml/keys [content]}]
  (let [part (fn [tag] (some #(when (= tag (:xml/tag %)) (:xml/content %)) content))]
    (cond-> {:P3QualifiedName/name (or (part :UATypes/Name) "")}
      (part :UATypes/NamespaceIndex) (assoc :IMPL/namespace-index
                                            (parse-long (part :UATypes/NamespaceIndex))))))

;;; --------------------------------- Stuff for making schema from schema-edn -----------------------------------
(defn make-p5-std-ref-type-schema
  "Return a vector of DataHike schema for Part 5 ReferenceTypes (P5RefType) using the structure produced from Part 5 XML and the P5 cardinality table."
  [p5]
  (letfn [(ref2schema [{:Node/keys [browse-name category documentation id inverse-name is-abstract? release-status symmetric?]}]
            (let [fwd-schema (cond-> {:db/ident (keyword "P5StdRefType" browse-name)
                                      :db/valueType :db.type/ref
                                      :db/cardinality (->  p5-card/card-table (get browse-name) :cardinality)
                                      :uaexp/id id}
                               documentation           (assoc :db/doc documentation)
                               category                (assoc :uaexp/category category)
                               is-abstract?            (assoc :uaexp/is-abstract? true)
                               release-status          (assoc :uaexp/release-status release-status)
                               symmetric?              (assoc :uaexp/symmetric? true))
                  rev-schema (when inverse-name
                               (cond-> {:db/ident (keyword "P5StdRefType" inverse-name)
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

(def expected-namespaces
  "These are keys returned by make-schema-info that are expected. schema is or can be specified for them."
  {:p5   #{"Alias" "Definition"         "Model" "Node" "NodeSet" "P3LocalizedText" "P5StdRefType" "P6ByteString" "RolePerm" "UATypes" "box" "Field"}
   :amb  #{"Alias" "Definition" "Field" "Model" "Node" "NodeSet"                   "P5StdRefType" "P6ByteString"            "UATypes" "box" "ignore"}})

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

(defn write-schema+-file
  "Write the schema-info to a file nicely."
  [schema-info schema-fname]
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
    (spit schema-fname @s)
    (log! :info (str "Wrote " schema-fname))))

(def edn-memo "Keep the Part 5 structure so you don't have to slurp and read-string it." (atom nil))

;;; This is essentially 'top-level' of the functionality for generating schema.
;;; (pro/make-schema-info :p5 "data/part5/p5-nodeset.edn" "data/part5/p5-temp-schema+.edn"))
(defn make-schema+
  "This creates schema maps in a map indexed by the object type strings, for example, 'Node' and 'NodeSet, and 'P5RefType'.
   Some of these can be used as is. Exceptions:
    - P5RefType - is ignored because we want inverse relations too; for these we go back to the edn and do something different."
  [schema-key schema-edn-fname schema-fname]
  (let [schema-info (as-> schema-edn-fname ?d
                      (slurp ?d)
                      (edn/read-string ?d)
                      (reset! edn-memo ?d) ; We'll use this below, but we keep it public for debugging.
                      (learn-schema-basic ?d)
                       (group-by #(if (-> % :db/ident keyword?) (-> % :db/ident namespace) :other) ?d)
                       (dissoc ?d :other)       ; These should be inside P5StdRefType, handled separately below.
                       (dissoc ?d "IMPL"))      ; This is :IMPL/ref, which will be resolved while storing entities.
        found-keys (-> schema-info keys set)
        expected-ns (get expected-namespaces schema-key)
        bad-keys (set/difference found-keys expected-ns)]
    (when (not-empty bad-keys)
      (log! :warn (str "There are entity types that need investigation: " bad-keys)))
    (when-let [missing (not-empty (set/difference expected-ns found-keys))]
      (log! :warn (str "Types not present: " missing)))
    (-> schema-info
        (assoc "P5StdRefType" (make-p5-std-ref-type-schema @edn-memo))
        (mod-schema schema-mods)
        (write-schema+-file schema-fname))))
