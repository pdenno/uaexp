(ns ua.nsuri
  "Canonicalize NodeIds so that nodesets from different files can share one DB.

   A NodeId's ns=<index> is an index into the file's own <NamespaceUris> table, so the same
   literal string denotes different nodes in different files: ns=1;i=1002 is ObligationType in
   OPC 40501-1 and CNC04MachineOperationMonitoringType in CNC04's extension. Since :Node/id is
   :db/unique :db.unique/identity, loading both without rewriting merges them into one entity,
   silently.

   Canonical form is \"<namespace-uri>;<identifier>\", e.g.
     \"i=25345\"     -> \"http://opcfoundation.org/UA/;i=25345\"
     \"ns=2;i=26\"   -> \"http://opcfoundation.org/UA/MachineTool/;i=26\"   (in CNC04's file)
     \"ns=1;i=26\"   -> \"http://opcfoundation.org/UA/MachineTool/;i=26\"   (in MachineTool's own file)

   The last two lines are the point: the same node, written two ways, arrives as one id."
  (:require
   [clojure.string    :as str]
   [taoensso.telemere :as log :refer [log!]]))

(def base-uri
  "Namespace index 0 is always the OPC UA base namespace and is never listed in <NamespaceUris>."
  "http://opcfoundation.org/UA/")

(defn node-id?
  "Truthy when s is a NodeId string. Identifier types are i= (numeric), s= (string),
   g= (guid) and b= (opaque); only i= occurs in the nodesets we read so far."
  [s]
  (and (string? s) (re-matches #"^(ns=\d+;)?[isgb]=.+$" s)))

(defn canonical?
  "Truthy when s is already in canonical <uri>;<identifier> form."
  [s]
  (and (string? s) (re-matches #"^https?://.*;[isgb]=.+$" s)))

(defn index->uri
  "Return a map from namespace index to namespace URI for one nodeset.
   Index 0 is implicit; the <NamespaceUris> table supplies 1..n in order."
  [nodeset]
  (let [uris (->> nodeset :NodeSet/content (some :NodeSet/namespace-uris))]
    (into {0 base-uri}
          (map-indexed (fn [i uri] [(inc i) uri]) uris))))

(defn canonicalize-id
  "Rewrite one NodeId string to <uri>;<identifier> using the nodeset's index->uri map."
  [id idx->uri]
  (cond (canonical? id)  id
        (node-id? id)    (let [[_ ns-part ident] (re-matches #"^(?:ns=(\d+);)?(.+)$" id)
                               idx (if ns-part (parse-long ns-part) 0)
                               uri (get idx->uri idx)]
                           (if uri
                             (str uri ";" ident)
                             (throw (ex-info "NodeId names a namespace index the nodeset does not declare."
                                             {:id id :index idx :known (sort (keys idx->uri))}))))
        :else            id))

(defn split-id
  "Return [namespace-uri local-id] for a canonical id, or nil if it isn't one."
  [id]
  (when (canonical? id)
    (let [i (str/last-index-of id ";")]
      [(subs id 0 i) (subs id (inc i))])))

(defn canonicalize-nodeset
  "Return nodeset with every :Node/id and every {:IMPL/ref <id>} rewritten to canonical form,
   and every node given :Node/namespace-uri and :Node/local-id.

   Both places a NodeId can appear are covered: :Node/id on a node, and :IMPL/ref in either the
   key or the value position of a reference (build-part5's :p5/Reference emits both)."
  [nodeset]
  (let [idx->uri (index->uri nodeset)]
    (when (< (count idx->uri) 2)
      (log! :info (str "Nodeset declares no <NamespaceUris>; all NodeIds taken as " base-uri ".")))
    (letfn [(cn [obj]
              (cond (and (map? obj) (contains? obj :IMPL/ref))
                    (update obj :IMPL/ref #(canonicalize-id % idx->uri))

                    ;; A QualifiedName carries a bare namespace index, for the same reason and
                    ;; with the same hazard as a NodeId. ua.profiles parks it on :IMPL/namespace-index.
                    (and (map? obj) (contains? obj :IMPL/namespace-index))
                    (let [idx (:IMPL/namespace-index obj)]
                      (-> obj
                          (dissoc :IMPL/namespace-index)
                          (assoc :P3QualifiedName/namespace-uri
                                 (or (get idx->uri idx)
                                     (throw (ex-info "QualifiedName names a namespace index the nodeset does not declare."
                                                     {:index idx :known (sort (keys idx->uri))}))))))

                    (map? obj)
                    (let [m (reduce-kv (fn [m k v] (assoc m (cn k) (cn v))) {} obj)]
                      (if-let [id (:Node/id m)]
                        (let [id* (canonicalize-id id idx->uri)
                              [uri local] (split-id id*)]
                          (assoc m :Node/id id* :Node/namespace-uri uri :Node/local-id local))
                        m))

                    (vector? obj)
                    (mapv cn obj)

                    :else obj))]
      (cn nodeset))))

(defn ^:diag namespace-census
  "Return {namespace-uri count} over a canonicalized nodeset. Useful for confirming that a
   companion nodeset's own nodes landed in its own namespace and not the base one."
  [nodeset]
  (->> nodeset :NodeSet/content (keep :Node/namespace-uri) frequencies))
