(ns notebox.core
  "Krell entry point: starts the Integrant system once and renders its root view."
  (:require ["react-native-get-random-values"] ; crypto.getRandomValues for nano-id; must load first
            [integrant.core :as ig]
            [reagent.core :as r]
            [notebox.config :as config]
            [notebox.shell.app]
            [notebox.ui.root]))

;; A ratom, so that replacing the system (dev/reset) re-renders the app.
(defonce system (r/atom nil))

(defn start!
  ([] (start! (config/build-profile)))
  ([profile]
   (reset! system (ig/init (config/config profile)))))

(defn stop! []
  (when-let [s @system]
    (ig/halt! s)
    (reset! system nil)))

(defn app []
  (if-let [root (:notebox/ui @system)]
    [root]
    [:<>]))

(defn ^:export -main [& _args]
  (when-not @system
    (start!))
  (r/as-element [app]))
