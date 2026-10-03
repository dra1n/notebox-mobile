(ns notebox.infra.navigator
  "The navigator slot: the UI puts a function (fn [action route params]) here
  once its navigation container exists (Phase 5); navigation effects call it.
  Until then navigating is a no-op."
  (:require [integrant.core :as ig]))

(defn navigator [] (atom nil))

(defn set-navigator!
  "Installs `f` (nil uninstalls)."
  [nav f]
  (reset! nav f))

(defn navigate!
  [nav action route params]
  (when-let [f @nav] (f action route params)))

(defmethod ig/init-key :notebox.infra/navigator [_ _]
  (navigator))
