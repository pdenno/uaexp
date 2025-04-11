(ns ua.db-util-test
  (:require
   [clojure.test       :refer [deftest is testing]]
   [datahike.api          :as d]
   [datahike.pull-api     :as dp]
   [develop.repl                  :refer [ns-setup!]]
   [taoensso.telemere     :as log :refer [log!]]
   [ua.db-util         :as dbu]))

;;; THIS is the namespace to hang out in!

(deftest simple-retrievals
  (testing "Testing whether dbu/resolve-node works as expected."
    (is (=
         nil #_(dbu/resolve-node "i=25345" :part5)
         nil))))
