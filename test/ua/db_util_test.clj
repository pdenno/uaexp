(ns ua.db-util-test
  (:require
   [clojure.test       :refer [deftest is testing]]
   [develop.repl                  :refer [ns-setup!]]
   [taoensso.telemere     :as log :refer [log!]]
   [datahike.api       :as d]
   [ua.db-util         :as dbu]
   [ua.nsuri           :as nsuri]))

(def base-ns
  "Stores are named by the namespace they hold; a bare URI names its newest registered version."
  nsuri/base-uri)

;;; THIS is the namespace to hang out in!
(ns-setup!)

(deftest simple-retrievals
  (testing "Testing whether dbu/resolve-node works as expected."
    (testing "Testing simple dbu/resolve-node call."
      (is (= #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
                    :type :UAReferenceType,
                    :references
                    [#:P5StdRefType{:SubtypeOf
                                    #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
                                           :type :UAReferenceType,
                                           :references
                                           [#:P5StdRefType{:SubtypeOf
                                                           #:Node{:symmetric? true,
                                                                  :documentation "https://reference.opcfoundation.org/v105/Core/docs/Part11/5.3.1",
                                                                  :type :UAReferenceType,
                                                                  :id "i=31",
                                                                  :category "Base Info Base Types",
                                                                  :display-name "References",
                                                                  :is-abstract? true,
                                                                  :browse-name "References"}}],
                                           :inverse-name "InverseHierarchicalReferences",
                                           :id "i=33",
                                           :category "Base Info Base Types",
                                           :display-name "HierarchicalReferences",
                                           :is-abstract? true,
                                           :browse-name "HierarchicalReferences"}}],
                    :inverse-name "HasPushTarget",
                    :id "i=25345",
                    :category "PubSub Model SKS Push",
                    :display-name "HasPushedSecurityGroup",
                    :browse-name "HasPushedSecurityGroup"}
             (dbu/resolve-node "i=25345"))))))

;;; i=2041 (BaseEventType) is used below because its resolution truncates -- it has properties
;;; that cycle back -- whereas i=25345 above resolves completely and so exercises none of this.
(deftest resolution-depth
  (testing "Testing how far resolution goes and how it reports where it stopped."
    (testing "Testing that deeper advances exactly one level."
      ;; It used to call resolve-db-id, which resolves all the way down. Then every :depth gave
      ;; the same answer, and resolve-node on a richly referenced node became unusable.
      (let [base (dbu/resolve-db-id {:db/id (dbu/get-node-eid "i=2041" base-ns)} base-ns)
            once (dbu/deeper base base-ns)
            twice (dbu/deeper once base-ns)]
        (is (not= base once))
        (is (not= once twice))))

    (testing "Testing that resolution names where it stopped, and splices nothing in."
      (let [before (dbu/deeper (dbu/resolve-db-id {:db/id (dbu/get-node-eid "i=2041" base-ns)} base-ns) base-ns)
            after  (dbu/walk-final before base-ns)
            colls  (fn [obj] (let [n (atom 0)]
                               (letfn [(c [x] (cond (map? x)    (do (swap! n inc) (doseq [[_ v] x] (c v)))
                                                    (vector? x) (do (swap! n inc) (doseq [y x] (c y)))))]
                                 (c obj))
                               @n))
            leftover (let [a (atom #{})]
                       (letfn [(c [x] (cond (dbu/db-ref? x) (swap! a conj (:db/id x))
                                            (map? x)        (doseq [[_ v] x] (c v))
                                            (vector? x)     (doseq [y x] (c y))))]
                         (c after))
                       @a)]
        ;; walk-final REPLACES markers with addresses; it must never resolve one and splice its
        ;; content in. Doing that once turned a 4MB answer into 81MB, because a 436KB ByteString
        ;; sat behind 170 markers. Substituting addresses can only reduce the collection count.
        (is (<= (colls after) (colls before)))
        ;; Whatever is left unnamed must be something with no address to give it: the value and
        ;; reference entities nodes are built from, never a node.
        (is (every? #(nil? (dbu/get-node-i= % base-ns)) leftover))))))

;;; A node in another namespace lives in another store, where an entity id would mean nothing.
(deftest namespace-boundary
  (testing "Testing that resolution stops at the store's boundary."
    (testing "Testing that an address round-trips, semicolons in the identifier included."
      (is (= {:uri "http://x/" :id "s=Chan1;Spindle"}
             (nsuri/parse-address (nsuri/address "http://x/" "s=Chan1;Spindle")))))

    (testing "Testing that a bare identifier means the base namespace."
      (is (= (dbu/resolve-node "i=25345")
             (dbu/resolve-node (nsuri/address nsuri/base-uri "i=25345")))))

    (testing "Testing that Part 5 defines the base namespace and depends on nothing."
      (let [db @(dbu/connect-atm base-ns)]
        (is (zero? (count (d/q '[:find ?e :where [?e :Node/ref]] db))))))))
