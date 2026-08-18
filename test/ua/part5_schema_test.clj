(ns ua.part5-schema-test
  (:require
   [clojure.edn               :as edn]
   [ua.db-util         :as dbu :refer [connect-atm]]))

(def p5 (-> "data/part5/p5.edn" slurp edn/read-string))
