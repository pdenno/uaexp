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

(declare expected-namespaces make-schema+)

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
  "Create the store for one nodeset, running the generation steps that precede it. Arguments:
     :nodeset-edn-file - the nodeset EDN. Required: written here when :xml-file is given,
                         otherwise it has to exist already.
     :xml-file         - the XML defining the nodeset. When given, the nodeset EDN is generated
                         from it, overwriting whatever was at :nodeset-edn-file.
     :schema+-file     - when given, schema+ is learned from the nodeset, written here, and used
                         for the store. Without it the store gets Part 5's schema alone, which is
                         enough for a nodeset that introduces no attributes of its own.
     :schema-key       - names the entry in expected-namespaces that make-schema+ checks what it
                         learned against. Required with :schema+-file, unused without it.

   The store is named by the namespace and version in the nodeset's own <Models>, so there is
   nothing to name here -- :schema-key used to serve that purpose and no longer does. Returns
   the store's {:prefix .. :version ..}."
  [{:keys [schema-key xml-file nodeset-edn-file schema+-file]}]
  (assert nodeset-edn-file "Provide :nodeset-edn-file: generated from :xml-file, or an existing one.")
  (when schema+-file
    (assert (contains? expected-namespaces schema-key)
            (str ":schema-key must be one of " (-> expected-namespaces keys sort vec) " to check learned schema against.")))
  (reset! ignored-nodes [])
  ;; Without an :xml-file there is nothing to generate from, so the EDN has to be there already.
  (if xml-file
    (write-nodeset-edn! xml-file nodeset-edn-file)
    (assert (-> nodeset-edn-file io/file .exists) (str "No such nodeset EDN file: " nodeset-edn-file)))
  ;; ToDo: a :schema+-file is always regenerated here and then read back; there is no way to say
  ;;       "use the file that is there". Deciding generate-vs-use is the open question.
  (when schema+-file
    (make-schema+ schema-key nodeset-edn-file schema+-file))
  ;; Delegating rather than calling create-ua-db! again keeps one path into a store, so the
  ;; foreign-key report happens here too.
  (make-store! nodeset-edn-file
               :schema+ (when schema+-file (-> schema+-file slurp edn/read-string))))

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
(defn ref-cardinality
  "Return the cardinality recorded for browse-name in the Part 5 table, under key (:cardinality or
   :inverse-cardinality).

   p5-cardinality covers Part 5's ReferenceTypes only, and its values are deliberate guesses at
   what the semantics should be. A nodeset that defines ReferenceTypes of its own -- AMB's
   Contains/LocatedIn family, for instance -- is not in it, and nothing in the nodeset says what
   the cardinality ought to be. Default to many: it is the common case for UA references, it is
   what learn-schema-basic already does when it sees a vector, and being wrong that way costs a
   collection of one, whereas a wrong :one silently discards references."
  [browse-name k]
  (or (-> p5-card/card-table (get browse-name) k)
      (do (log! :warn (str "No cardinality known for ReferenceType " browse-name " (" (name k) "); using many."))
          :db.cardinality/many)))

(defn make-p5-std-ref-type-schema
  "Return a vector of DataHike schema for Part 5 ReferenceTypes (P5RefType) using the structure produced from Part 5 XML and the P5 cardinality table."
  [p5]
  (letfn [(ref2schema [{:Node/keys [browse-name category documentation id inverse-name is-abstract? release-status symmetric?]}]
            (let [fwd-schema (cond-> {:db/ident (keyword "P5StdRefType" browse-name)
                                      :db/valueType :db.type/ref
                                      :db/cardinality (ref-cardinality browse-name :cardinality)
                                      :uaexp/id id}
                               documentation           (assoc :db/doc documentation)
                               category                (assoc :uaexp/category category)
                               is-abstract?            (assoc :uaexp/is-abstract? true)
                               release-status          (assoc :uaexp/release-status release-status)
                               symmetric?              (assoc :uaexp/symmetric? true))
                  rev-schema (when inverse-name
                               (cond-> {:db/ident (keyword "P5StdRefType" inverse-name)
                                        :db/valueType :db.type/ref
                                        :db/cardinality (ref-cardinality browse-name :inverse-cardinality)
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
