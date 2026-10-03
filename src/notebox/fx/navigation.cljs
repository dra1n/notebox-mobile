(ns notebox.fx.navigation
  "Navigation as data (roadmap §5.2 rule 4):

    {:nav/navigate [route params]}  {:nav/back nil}

  Handlers call the navigator component (notebox.infra.navigator) that the UI
  fills; until then navigation is a no-op. Plain functions;
  notebox.shell.effects registers them."
  (:require [notebox.infra.navigator :as navigator]))

(defn effects
  "{fx-id handler} over the `navigator` component."
  [{:keys [navigator]}]
  {:nav/navigate (fn [[route params]] (navigator/navigate! navigator :navigate route params))
   :nav/back     (fn [_] (navigator/navigate! navigator :back nil nil))})
