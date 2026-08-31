(ns ua.nsuri
  "Node addresses that mean the same thing in every database.

   A NodeId's ns=<index> is an index into the file's own <NamespaceUris> table, so the same
   literal string denotes different nodes in different files: ns=1;i=1002 is ObligationType in
   OPC 40501-1 and CNC04MachineOperationMonitoringType in CNC04's extension. Anything that keys
   nodes on the literal NodeId merges the two.

   The address that does not have that problem is OPC UA's own ExpandedNodeId (Part 6, Annex A),
   which carries the namespace URI in place of the index:

     nsu=http://opcfoundation.org/UA/;i=25345
     nsu=http://opcfoundation.org/UA/MachineTool/;i=26

   Within one store the namespace is implied, so a node's :Node/id is the bare identifier -- the
   Part 6 form without a namespace: i=25345. The four identifier types are i= numeric, s= string,
   g= guid and b= opaque; only i= occurs in any nodeset we have read, but a server's instance data
   is normally s=, so nothing here assumes numeric."
  (:require
   [clojure.string    :as str]
   [taoensso.telemere :as log :refer [log!]]))

(def base-uri
  "Namespace index 0 is always the OPC UA base namespace and is never listed in <NamespaceUris>."
  "http://opcfoundation.org/UA/")

(def ^:private local-id-re
  "An identifier with its type letter and no namespace: i=25345, s=Channel1.Speed."
  #"^[isgb]=.+$")

(defn local-id?
  "Truthy when s is an identifier scoped to some store, e.g. \"i=25345\"."
  [s]
  (and (string? s) (re-matches local-id-re s)))

(defn node-id?
  "Truthy when s is a NodeId as written in a nodeset file: an identifier, optionally preceded by
   the file-local ns=<index>."
  [s]
  (and (string? s) (re-matches #"^(ns=\d+;)?[isgb]=.+$" s)))

(defn address?
  "Truthy when s is an ExpandedNodeId naming its namespace by URI."
  [s]
  (and (string? s) (str/starts-with? s "nsu=")))

(defn address
  "Return the ExpandedNodeId for local-id in namespace uri."
  [uri local-id]
  (str "nsu=" uri ";" local-id))

(defn parse-address
  "Return {:uri <namespace uri> :id <local identifier>} for an ExpandedNodeId.

   Splits at the FIRST ';' after the URI, not the last: a String identifier may itself contain
   semicolons (nsu=http://x/;s=Channel1;Spindle is legal and its identifier runs to the end)."
  [s]
  (when (address? s)
    (let [rest- (subs s 4)
          i (str/index-of rest- ";")]
      (when i
        {:uri (subs rest- 0 i)
         :id  (subs rest- (inc i))}))))

(defn split-node-id
  "Return [ns-index local-id] for a NodeId as written in a file. Index 0 when ns= is absent."
  [node-id]
  (let [[_ idx ident] (re-matches #"^(?:ns=(\d+);)?(.+)$" node-id)]
    [(if idx (parse-long idx) 0) ident]))

(defn index->uri
  "Return a map from namespace index to namespace URI for one nodeset.
   Index 0 is implicit; the <NamespaceUris> table supplies 1..n in order."
  [nodeset]
  (let [uris (->> nodeset :NodeSet/content (some :NodeSet/namespace-uris))]
    (into {0 base-uri}
          (map-indexed (fn [i uri] [(inc i) uri]) uris))))

(defn nodeset-uri
  "Return the namespace URI a nodeset defines, from its <Models> entry. A nodeset declares exactly
   one model of its own; Part 5's core model declares the base namespace."
  [nodeset]
  (or (->> nodeset :NodeSet/content (some :NodeSet/models) first :Model/uri)
      (throw (ex-info "Nodeset declares no <Models>; cannot tell which namespace it defines." {}))))

(defn nodeset-version
  "Return the version string of the model a nodeset defines."
  [nodeset]
  (->> nodeset :NodeSet/content (some :NodeSet/models) first :Model/version))

;;; ------------------------------- store ids -------------------------------------------
;;; A node is addressed by ExpandedNodeId; a STORE is identified by the pair <namespace, version>.
;;; The system DB needs one string to key a catalog entry on, and a consumer -- Tessell's
;;; orchestrator, say -- needs to get from that string back to the pair that connect-atm takes.
;;; Hence these two, here rather than in ua.db-util so that they travel with the rest of the
;;; addressing vocabulary.

(def ^:private store-id-sep
  "'|' cannot occur unencoded in a URI (RFC 3986), so splitting on it is unambiguous even though
   the URI itself is full of ':' and '/'."
  "|")

(defn store-id
  "Return the catalog identifier for a store, from {:prefix .. :version ..} or from the two parts.
     \"http://opcfoundation.org/UA/|1.05.04\""
  ([{:keys [prefix version]}] (store-id prefix version))
  ([prefix version]
   (assert (and prefix version) "A store is identified by a namespace URI and a version.")
   (str prefix store-id-sep version)))

(defn parse-store-id
  "Return {:prefix .. :version ..} for a store id, or nil if s is not one. This is what turns a
   system-DB answer back into something connect-atm accepts."
  [s]
  (when (string? s)
    (let [i (str/last-index-of s store-id-sep)]
      (when i
        {:prefix (subs s 0 i)
         :version (subs s (inc i))}))))

;;; ------------------------------- versions -------------------------------------------
(defn version-vec
  "Return a version string as a vector of integers for comparison. \"1.05.04\" -> [1 5 4].
   Segments that are not integers sort as -1, which keeps compare total without pretending
   to understand them."
  [v]
  (mapv #(or (parse-long %) -1) (str/split (or v "") #"\.")))

(defn newest
  "Return the newest of the argument version strings."
  [versions]
  (->> versions (sort-by version-vec) last))

;;; ------------------------- canonicalizing a parsed nodeset ---------------------------
(defn canonicalize-nodeset
  "Return nodeset with every NodeId resolved against its <NamespaceUris> table:

     :Node/id      becomes the bare local identifier (i=1002), since a store holds one namespace.
     {:IMPL/ref x} becomes {:IMPL/ref \"i=63\"} when x is in this nodeset's own namespace, and
                   {:IMPL/foreign \"nsu=...;i=63\"} when it is not.

   The caller loads the first as a reference to a local node and the second as a foreign key, so
   nothing outside this namespace has to be present -- or even to exist -- for the load to work."
  [nodeset]
  (let [idx->uri (index->uri nodeset)
        own (nodeset-uri nodeset)]
    (when (< (count idx->uri) 2)
      (log! :info (str "Nodeset declares no <NamespaceUris>; all NodeIds taken as " base-uri ".")))
    (letfn [(resolve-ref [node-id]
              (let [[idx ident] (split-node-id node-id)
                    uri (or (get idx->uri idx)
                            (throw (ex-info "NodeId names a namespace index the nodeset does not declare."
                                            {:node-id node-id :index idx :known (sort (keys idx->uri))})))]
                (if (= uri own)
                  {:IMPL/ref ident}
                  {:IMPL/foreign (address uri ident)})))
            (cn [obj]
              (cond (and (map? obj) (contains? obj :IMPL/ref))
                    (resolve-ref (:IMPL/ref obj))

                    ;; A QualifiedName carries a bare namespace index, for the same reason and with
                    ;; the same hazard as a NodeId. ua.profiles parks it on :IMPL/namespace-index.
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
                      (cond-> m
                        (:Node/id m)
                        (update :Node/id #(second (split-node-id %)))

                        ;; A BrowseName is a QualifiedName, so it carries a namespace index with
                        ;; the same hazard as a NodeId's. Handled here rather than in the
                        ;; :IMPL/namespace-index branch above because that one returns without
                        ;; recursing -- right for a leaf QualifiedName, wrong for a node, which is
                        ;; where a BrowseName is merged. The URI is recorded only when it is not
                        ;; this nodeset's own; inside the store it is implied, exactly as for refs.
                        (contains? m :IMPL/browse-name-index)
                        (as-> $m
                            (let [idx (:IMPL/browse-name-index $m)
                                  uri (or (get idx->uri idx)
                                          (throw (ex-info "BrowseName names a namespace index the nodeset does not declare."
                                                          {:index idx :known (sort (keys idx->uri))})))]
                              (cond-> (dissoc $m :IMPL/browse-name-index)
                                (not= uri own) (assoc :Node/browse-name-uri uri))))))

                    (vector? obj) (mapv cn obj)
                    :else obj))]
      (cn nodeset))))

(defn ^:diag foreign-addresses
  "Return the distinct foreign addresses a canonicalized nodeset references. These become the
   store's foreign keys, and they are the whole of its dependence on other nodesets."
  [nodeset]
  (let [found (atom #{})]
    (letfn [(f [obj]
              (cond (and (map? obj) (contains? obj :IMPL/foreign)) (swap! found conj (:IMPL/foreign obj))
                    (map? obj)    (doseq [[k v] obj] (f k) (f v))
                    (vector? obj) (doseq [x obj] (f x))))]
      (f nodeset)
      @found)))
