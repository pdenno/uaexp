(ns ua.db-util-test
  (:require
   [clojure.test       :refer [deftest is testing]]
   [develop.repl                  :refer [ns-setup!]]
   [taoensso.telemere     :as log :refer [log!]]
   [ua.db-util         :as dbu]))

;;; THIS is the namespace to hang out in!
(ns-setup!)

(deftest simple-retrievals
  (testing "Testing whether dbu/resolve-node works as expected."
    (testing "Testing simple dbu/resolve-node call."
      (is (= #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part14/8.6.6",
                    :type :UAReferenceType,
                    :references
                    [#:P5StdRefType{:subtype-of
                                    #:Node{:documentation "https://reference.opcfoundation.org/v105/Core/docs/Part5/11.2",
                                           :type :UAReferenceType,
                                           :references
                                           [#:P5StdRefType{:subtype-of
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
             (dbu/resolve-node "i=25345" :part5))))))
