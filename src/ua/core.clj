(ns ua.core
  "Toplevel of uaexp."
  (:require
   [mount.core                  :refer [defstate]]
   [ua.build-part5              :refer [part5]])) ; For mount

;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
