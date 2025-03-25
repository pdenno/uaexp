(ns ua.db-util
  "Utilities for schema-db (which will likely become a library separate from rad-mapper"
  (:require
   [datahike.api          :as d]
   [datahike.pull-api     :as dp]
   [taoensso.telemere     :as log :refer [log!]]))

(def db-cfg-atm "Configuration map used for connecting to the db. It is set in core."  (atom nil))
(def ^diag diag (atom nil))

(defn connect-atm
  "Set the var rad-mapper.db-util_conn by doing a d/connect.
   Return a connection atom."
  []
  (when-let [db-cfg @db-cfg-atm]
    (if (d/database-exists? db-cfg)
      (d/connect db-cfg)
      (log! :warn "There is no DB to connect to."))))

;;; ToDo:
;;;  - cljs complains about not finding x/element-nss, which I don't see in the  0.2.0-alpha8 source at all.
;;;    (Yet it does work in clj!) I suppose reading xml isn't something I need in cljs, but it would be nice to know what is going on here.
;;; ToDo: Get some more types in here, and in implementation generally.
(defn db-type-of
  "Return a Datahike schema :db/valueType object for the argument"
  [obj]
  (cond (string? obj)  :db.type/string
        (number? obj)  :db.type/number
        (keyword? obj) :db.type/keyword
        (map? obj)     :db.type/ref
        (boolean? obj) :db.type/boolean))

;;; This seems to cause problems in recursive resolution. (See resolve-db-id)"
(defn db-ref?
  "It looks to me that a datahike ref is a map with exactly one key: :db/id."
  [obj]
  (and (map? obj) (= [:db/id] (keys obj))))

;;; {:db/id 3779}
(defn resolve-db-id
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  ([form conn-atm] (resolve-db-id form conn-atm #{}))
  ([form conn-atm filter-set]
   (letfn [(resolve-aux [obj]
             (cond
               (db-ref? obj) (let [res (dp/pull @conn-atm '[*] (:db/id obj))]
                               (if (= res obj) nil (resolve-aux res)))
               (map? obj) (reduce-kv (fn [m k v] (if (filter-set k) m (assoc m k (resolve-aux v))))
                                     {}
                                     obj)
               (vector? obj)      (mapv resolve-aux obj)
               (set? obj)    (set (mapv resolve-aux obj))
               (coll? obj)        (map  resolve-aux obj)
               :else  obj))]
     (resolve-aux form))))

(defn keywordize
  "Return the string as a keyword. If the string as a colon in it,
  the prefix is used as the keyword's namespace."
  ([str]
   (if (string? str)
     (if-let [[_ prefix word] (re-matches #"(\w+)\:(\w+)" str)]
       (keyword prefix word)
       (keyword str))
     str))
  ([str ns]
   (keyword ns str)))

(defn call-this [arg]
  (reset! diag arg))

#_(defn condition-form
  "Return the form with certain map values as keywords and certain map values zipped.
   Usually top-level call is a form representing a whole schema file. Walks schema."
  [form]
  (let [key-pred? ; Map keys corresponding to values that should be keywordized
        #{:xsd_extension :cct_PrimitiveType :cct_scUse :cct_scType :cct_CategoryCode}
        needs-zip? ; Map keys to encode as a map for later decoding (See db-utils/resolve-db-id)
        #{:codeList_terms}]
    (letfn [(cf-aux [form]
              (cond (map? form) (reduce-kv (fn [m k v]
                                             ;;(when-not (map? v) (call-this {:form form}))
                                             (reset! diag {:v v})
                                             (if (needs-zip? k)
                                               (-> m
                                                   (assoc :zip_keys (-> v keys vec))
                                                   (assoc :zip_vals (-> v vals vec)))
                                               (if (key-pred? k)
                                                 (assoc m k (keywordize v))
                                                 (assoc m k (cf-aux v)))))
                                           {}
                                           form)
                    (vector? form)   (mapv cf-aux form)
                    (set? form) (set (map  cf-aux form))
                    (coll? form)     (map  cf-aux form)
                    :else form))]
      (cf-aux form))))

;;; ToDo: Spec about this?
(defn storable?
  "Return true if the argument does not contain:
      - nils
      - maps containing :xml/tag
      - the keyword :failure/rewrite-xsd-nil-method
   Such data cannot be stored in datahike."
  [obj]
  (let [ok? (atom true)]
    (letfn [(sto [obj]
              (cond (not @ok?) false
                    (nil? obj) (reset! ok? false)
                    (map? obj) (reset! ok? (reduce-kv (fn [result k v] (cond (not @ok?) false
                                                                             (= k :xml/tag) false
                                                                             (not result) false
                                                                             (nil? v) false
                                                                             :else (sto v)))
                                                      true
                                                      obj))
                    (coll? obj)    (reset! ok? (every? sto obj))
                    (keyword? obj) (if (= obj :failure/rewrite-xsd-nil-method) (reset! ok? false) true) ; ToDo: :failure?
                    :else true))]
      (sto obj))
    @ok?))


#_(defn explicit-root-ns
  "Return argument x/element-nss map modified so that that the empty-string namespace is 'ROOT' or whatever
   If the schema uses 'xs' for 'http://www.w3.org/2001/XMLSchema', change it to xsd"
  [nspaces & {:keys [root-name] :or {root-name "ROOT"}}]
  (when (-> nspaces :p->u (contains? root-name))
    (log! :warn "XML uses explicit 'root' namespace alias.")) ; ToDo: So pick something else.
  (as-> nspaces ?ns
    (assoc-in ?ns [:p->u root-name] (or (get (:p->u ?ns) "") :mm_nil))
    (update ?ns :p->u #(dissoc % ""))
    (update ?ns :u->ps
            (fn [uri2alias-map]
              (reduce-kv (fn [res uri aliases]
                           (assoc res uri (mapv #(if (= % "") root-name %) aliases)))
                         {}
                         uri2alias-map)))
    ;; Now change "xs" to "xsd*" if it exists.
    (if (= "http://www.w3.org/2001/XMLSchema" (get (:p->u ?ns) "xs"))
      (as-> ?ns ?ns1
        (assoc-in ?ns1 [:p->u "xsd"] "http://www.w3.org/2001/XMLSchema") ; 4th
        (update ?ns1 :p->u  #(dissoc % "xs"))
        (update ?ns1 :u->ps #(dissoc % "http://www.w3.org/2001/XMLSchema"))
        (assoc-in ?ns1 [:u->ps "http://www.w3.org/2001/XMLSchema"] ["xsd"]))
      (assoc-in ?ns [:p->u "xsd"] "http://www.w3.org/2001/XMLSchema")))) ; 4th

;;;#?(:clj
;;;(defn parse-xml-string
;;;  "This is useful for debugging. Typical usage:
;;;  (-> sss util/parse-xml-string (xpath :xsd*/schema :xsd*/complexType) rewrite-xsd)"
;;;  [s]
;;;  (let [pre "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
;;;             <xsd:schema xmlns=\"urn:test-string\"
;;;                         xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"
;;;                         targetNamespace=\"urn:test-string\"
;;;                         elementFormDefault=\"qualified\"
;;;                         attributeFormDefault=\"unqualified\"
;;;                         version=\"2.3\">"
;;;        post "</xsd:schema>"
;;;        xml  (x/parse-str (str pre s post))]
;;;    (-> {}
;;;        (assoc :xml/ns-info (explicit-root-ns (x/element-nss xml)))
;;;        (assoc :xml/content (-> xml alienate-xml clean-whitespace detagify vector))))))
(defn dir-up
  "file is java.io file. Return the path of the nth parent directory, applied recursively to the ."
  [file n]
  (if (> n 0)
    (recur (.getParentFile file) (dec n))
    file))

(defn box
  "Wrap the argument (an atomic value) in a box.
   Note that unlike unbox, this only accepts atomic values."
  [obj]
  (cond (string?  obj) {:box_string-val  obj},
        (number?  obj) {:box_number-val  obj},
        (keyword? obj) {:box_keyword-val obj},
        (boolean? obj) {:box_boolean-val obj}))

(defn unbox
  "Walk through the form replacing boxed data with the data.
   In the reduce DB, for simplicity, all values are :db.type/ref."
  [data]
  (letfn [(box? [obj]
            (and (map? obj)
                 (#{:box_string-val :box_number-val :box_keyword-val :box_boolean-val}
                  (-> obj seq first first))))  ; There is just one key in a boxed object.
          (ub [obj]
            (if-let [box-typ (box? obj)]
              (box-typ obj)
              (cond (map? obj)      (reduce-kv (fn [m k v] (assoc m k (ub v))) {} obj)
                    (vector? obj)   (mapv ub obj)
                    :else           obj)))]
    (ub data)))

;;; This seems to cause problems in recursive resolution. (See resolve-db-id)"
(defn db-ref?
  "It looks to me that a datahike ref is a map with exactly one key: :db/id."
  [obj]
  (and (map? obj) (= [:db/id] (keys obj))))

;;; {:db/id 3779}
(defn resolve-db-id
  "Return the form resolved, removing properties in filter-set,
   a set of db attribute keys, for example, #{:db/id}."
  ([form conn-atm] (resolve-db-id form conn-atm #{}))
  ([form conn-atm filter-set]
   (letfn [(resolve-aux [obj]
             (cond
               (db-ref? obj) (let [res (dp/pull @conn-atm '[*] (:db/id obj))]
                               (if (= res obj) nil (resolve-aux res)))
               (map? obj) (reduce-kv (fn [m k v] (if (filter-set k) m (assoc m k (resolve-aux v))))
                                     {}
                                     obj)
               (vector? obj)      (mapv resolve-aux obj)
               (set? obj)    (set (mapv resolve-aux obj))
               (coll? obj)        (map  resolve-aux obj)
               :else  obj))]
     (resolve-aux form))))
