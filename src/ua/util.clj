(ns ua.util
  "Do lowest level configuration (logging, etc.)."
  (:require
   [bling.core                      :as bling :refer [bling]]  ; print-pling is used (clj)!
   [clojure.pprint :refer [pprint]]
   [clojure.string       :as str]
   [mount.core           :as mount :refer [defstate]]
   [taoensso.telemere :as tel :refer [log!]]
   [taoensso.telemere.tools-logging :as tel-log]
   ;; datahike and konserve log through Timbre. We do not log through Timbre ourselves --
   ;; these two requires exist only to route Timbre's output into Telemere (see config-log!).
   [taoensso.telemere.timbre :as tel-timbre]
   [taoensso.timbre :as timbre]))

(defn now [] (new java.util.Date))

(defn custom-console-output-fn
  "I don't want to see hostname and time, etc. in console logging."
  ([] :can-be-a-no-op) ; for shutdown, at least.
  ([signal]
   (let [{:keys [kind level location msg_]} signal
         file (:file location)
         file (when (string? file)
                (let [[_ stbd-file] (re-matches  #"^.*(ua.*)$" file)]
                  (or stbd-file file)))
         line (:line location)
         msg (if-let [s (not-empty (force msg_))] s "\"\"")
         heading (-> (str "\n" (name kind) "/" (name level) " ") str/upper-case)]
     (cond (= :error level)      (bling [:bold.red.white-bg heading] " " [:red    (str file ":" line " - " msg)])
           (= :warn  level)      (bling [:bold.blue heading]         " " [:yellow (str file ":" line " - " msg)])
           :else                 (bling [:bold.blue heading]         " " [:olive  (str file ":" line " - " msg)])))))

(defn config-log!
  "Configure Telemere: set reporting levels and specify a custom :output-fn."
  []
  (tel/add-handler! :default/console (tel/handler:console {:output-fn custom-console-output-fn}))
  (tel-log/tools-logging->telemere!)  ;; Send tools.logging through telemere. Check this with (tel/check-interop)
  (tel/event! ::config-log {:level :info :msg (str "Logging configured:\n" (with-out-str (pprint (tel/check-interop))))})
  ;; datahike and konserve log through Timbre rather than tools.logging, so they do not arrive
  ;; by way of tools-logging->telemere!. Telemere 1.4 ships a Timbre shim, so instead of
  ;; silencing them inside Timbre we send Timbre INTO Telemere and let Telemere decide: the
  ;; signals arrive with :kind :timbre and carry their originating namespace, so the two rules
  ;; below still quieten them. Disabling Timbre's own :println appender stops it printing a
  ;; second, unformatted copy.
  (timbre/merge-config! {:appenders {:println  {:enabled? false}
                                     :telemere (tel-timbre/timbre->telemere-appender)}})
  (tel/set-min-level! :timbre "datahike.*" :error)
  (tel/set-min-level! :timbre "konserve.*" :error)
  (log! :info (str "======= Starting. config-log! executed " (now) " ==========")))

(defn ^:admin unconfig-log!
  "Set :default/console back to its default handler. Typically done at REPL."
  []
  (tel/remove-handler! :default/console)
  (tel/add-handler!    :default/console (tel/handler:console)))

;;; -------------- Starting and stopping ----------------------
(defn init-util []
  (config-log!))

(defstate util-state
  :start (init-util))
