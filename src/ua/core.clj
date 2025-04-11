(ns ua.core
  "Toplevel of uaexp."
  (:require
   [mount.core                  :as mount :refer [defstate]]
   [develop.repl                :refer [ns-setup!]]  ; <============== This is temporary!
   [taoensso.telemere           :as log :refer [log!]]
   [ua.part5-schema]))                                 ; For mount

;;;-------------------- Start and stop
(defn start-server [] :started)

(defstate server
  :start (start-server))
