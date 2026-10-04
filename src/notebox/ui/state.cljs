(ns notebox.ui.state
  "UI-only state that isn't app data: whether the side menu is open."
  (:require [reagent.core :as r]))

(defonce menu-open? (r/atom false))

(defn open-menu! [] (reset! menu-open? true))
(defn close-menu! [] (reset! menu-open? false))
