(ns notebox.infra.navigator
  "The navigator slot: an atom holding a function (fn [action route params]).
  The UI component (:notebox/ui) resets it when its navigation container
  exists, and back to nil on halt; navigation effects call it. Until then
  navigating is a no-op. The contract is the atom itself, so the UI doesn't
  need to require this namespace."
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
