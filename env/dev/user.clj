(ns user
  "For REPL-based start/stop of the server.
   This file isn't used in cljs and is a problem for shadow-cljs without the
   :clj compiler directives."
  (:require
   [clojure.pprint :refer [pprint]] ; for README.md, which uses it.
   [clojure.string]
   [clojure.spec.alpha :as s]
   [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
   [expound.alpha :as expound]
   [mount.core :as mount]
   [lambdaisland.classpath.watch-deps :as watch-deps]      ; hot loading for deps.
   [ua.core :refer [server]]                   ; for mount.
   [taoensso.timbre :as log]))

;;; If you get stuck do: (clojure.tools.namespace.repl/refresh)

;; uncomment to enable hot loading for deps
(watch-deps/start! {:aliases [:dev :test]})

(alter-var-root #'s/*explain-out* (constantly expound/printer))
(add-tap (bound-fn* clojure.pprint/pprint))
(set-refresh-dirs "src/server/scheduling_tbd" #_"test/scheduling_tbd")  ; Put here as many as you need. test messes with ns-setup!

(defn ^:admin start
  "Start the web server"
  []
  (let [res (mount/start)
        info (str "   " (clojure.string/join ",\n    " (:started res)))]
    (log/info "started:\n" info)))

(defn stop
  "Stop the web server"
  []
  (mount/stop))

(defn ^:admin restart
  "Stop, reload code, and restart the server. If there is a compile error, use:

  ```
  (tools-ns/refresh)
  ```

  to recompile, and then use `start` once things are good."
  []
  (stop)
  (tools-ns/refresh :after 'user/start))
