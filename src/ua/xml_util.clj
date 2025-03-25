(ns ua.xml-util
  (:require
   [cemerick.url          :as url]
   [clojure.java.io       :as io]
   [clojure.data.xml      :as x]
   [clojure.walk          :as walk]
   [taoensso.telemere     :as log :refer [log!]]   ))

(def ^:diag diag (atom nil))

(defn xml-group-by
  "Return a map of the :xml/content of the argument grouped by the keys (without the ns) and
   :other if the :xml/tag is not one of the provided keys."
  [xmap & tag-keys]
  (let [tkey? (set tag-keys)]
    (cond-> (group-by
             #(if-let [k (tkey? (:xml/tag %))] (-> k name keyword) :other)
             (:xml/content xmap))
      (:xml/attrs xmap) (assoc :attrs (:xml/attrs xmap)))))


(defn xpath-internal
  [content props path-in]
  (loop [result content
         path path-in]
    (cond (empty? path) result,
          (not (map? content)) (when (:warn? props) (log! :warn (str "xpath failed at: " path " in " path-in))),
          :else
          (let [search (first path)]
            (recur
             (if (number? search)
               (nth (:xml/content result) search)
               (some #(when (= search (:xml/tag %)) %) (:xml/content result)))
             (rest path))))))

;;; ToDo: Could enhance this to be REAL XPath.
(defn xpath
  "Argument 'content' is a map with :xml/content. Follow the path, each step of
   which selects something from argument's :xml/content
   either an :xml/tag element, in which the first such is chosen, or an index,
   in which case that one is chosen."
  [content & path-in]
  (xpath-internal content {:warn? true} path-in))

(defn xpath-
  "Like xpath but without warning on no content."
  [content & path-in]
  (xpath-internal content {} path-in))

(defn xml-type?
  "Return true if the content has :xml/tag = the argument."
  [xml xtype]
  (if (map? xtype)
    (contains? xtype (:xml/tag xml))
    (= (:xml/tag xml) xtype)))

(defn child-types
  "Return a map that has an entry collecting the instances of every child type found.
   Argument is a vector of content."
  [content]
  (let [typs #{:xsd*/annotation :xsd*/any :xsd*/anyAttribute :xsd*/attribute :xsd*/complexContent :xsd*/complexType :xsd*/documentation
               :xsd*/element :xsd*/group :xsd*/include :xsd*/restriction :xsd*/schema :xsd*/sequence :xsd*/simpleContent :xsd*/simpleType}
        found (->> content (map :xml/tag) (reduce (fn [res tag] (if (typs tag) (conj res tag) res)) #{}))]
    (reduce (fn [res tag] (assoc res tag (filterv #(xml-type? % tag) content))) {} found)))

(defn clean-whitespace
  "Remove whitespace in element :content."
  [xml]
  (walk/postwalk
   (fn [obj]
     (if (and (map? obj) (contains? obj :content))
       (if (= 1 (count (:content obj))) ;; ToDo: Maybe don't remove it if is the only content???
         obj
         (update obj :content (fn [ct] (remove #(and (string? %) (re-matches #"^\s*$" %)) ct))))
       obj))
   xml))

(defn update-xml-namespaces
  "Return argument x/element-nss map modified so that that the empty-string namespace is 'ROOT' or whatever
   If the schema uses 'xs' for 'http://www.w3.org/2001/XMLSchema', change it to xsd"
  [nspaces & {:keys [root-name more-maps] :or {root-name "ROOT"} :as opts}]
  (reset! diag {:nspaces nspaces :opts opts})
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
    (if (not-empty more-maps)
      (-> ?ns
          (update :p->u  #(merge % (:p->u more-maps)))
          (update :u->ps #(merge % (:u->ps more-maps))))
      ?ns)
    ;; Now change "xs" to "xsd*" if it exists.
    (if (= "http://www.w3.org/2001/XMLSchema" (get (:p->u ?ns) "xs"))
      (as-> ?ns ?ns1
        (assoc-in ?ns1 [:p->u "xsd"] "http://www.w3.org/2001/XMLSchema") ; 4th
        (update ?ns1 :p->u  #(dissoc % "xs"))
        (update ?ns1 :u->ps #(dissoc % "http://www.w3.org/2001/XMLSchema"))
        (assoc-in ?ns1 [:u->ps "http://www.w3.org/2001/XMLSchema"] ["xsd"]))
      (assoc-in ?ns [:p->u "xsd"] "http://www.w3.org/2001/XMLSchema")))) ; 4th


(def more-maps
  "These are 'extra' aliases for OPC UA."
  {:p->u
   {"uaTypes" "http://opcfoundation.org/UA/2008/02/Types.xsd"}
   :u->ps
   {"http://opcfoundation.org/UA/2008/02/Types.xsd" ["uaTypes"]}})

;;; ToDo: Currently this isn't looking for redefined aliases. It calls x/element-nss just once!
;;; (-> sample-ubl-message io/reader x/parse alienate-xml)
(defn alienate-xml ; Silly, but I like it!
  "Replace namespaced xml map keywords with their aliases."
  [xml  & {:keys [root-name] :or {root-name "ROOT"}}]
  (let [ns-info (-> xml x/element-nss (update-xml-namespaces {:root-name root-name
                                                              :more-maps more-maps}))]
    (letfn [(equivalent-tag [tag]
              (let [[success? ns-name local-name] (->> tag str (re-matches #"^:xmlns\.(.*)/(.*)$"))]
                (if success?
                  (let [ns-name (url/url-decode ns-name)]
                    (if-let [alias-name (-> ns-info :u->ps (get ns-name) first)]
                      (keyword alias-name  local-name)
                      (keyword ns-name     local-name)))
                  tag)))]
      (walk/postwalk
       (fn [obj]
         (if (and (map? obj) (contains? obj :tag))
           (update obj :tag equivalent-tag)
           obj))
       xml))))

;;; (detagify '{:tag :cbc_InvoiceTypeCode, :attrs {:listID "UN/ECE 1001 Subset", :listAgencyID "6"}, :content ("380")})
(defn detagify
  "Argument in content from clojure.data.xml/parse. Return a map where
    (1) :tag is :schema_type,
    (2) :content, if present, is a simple value or recursively detagified.
    (3) :attrs, if present, are :xml/attrs.
   The result is that
     (a) returns a string or a map that if it has :xml/content, it is a string or a vector.
     (b) if a map, and the argument had attrs, has an :xml/attrs key."
  [obj]
  (cond (map? obj)
        (as-> obj ?m
          (assoc ?m :xml/tag (:tag ?m))
          (if (not-empty (:attrs   ?m)) (assoc ?m :xml/attrs (:attrs ?m)) ?m)
          (if (not-empty (:content ?m)) (assoc ?m :xml/content (detagify (:content ?m))) ?m)
          (dissoc ?m :tag :attrs :content))
        (seq? obj) (if (and (== (count obj) 1) (-> obj first string?))
                     (first obj)
                     (mapv detagify obj))
        (string? obj) obj ; It looks like nothing will be number? Need schema to fix things.
        :else (throw (ex-info "Unknown type in detagify" {:obj obj}))))

(defn read-xml
  "Return a map of the XML file read."
  [pathname & {:keys [root-name] :or {root-name "ROOT"}}]
  (let [xml (-> pathname io/reader x/parse)]
    ;(reset! diag xml)
    {:xml/ns-info (update-xml-namespaces (x/element-nss xml) {:root-name root-name
                                                              :more-maps more-maps})
      :xml/content (-> xml
                       (alienate-xml {:root-name root-name})
                       clean-whitespace
                       detagify
                       vector)
      :schema_pathname pathname}))
