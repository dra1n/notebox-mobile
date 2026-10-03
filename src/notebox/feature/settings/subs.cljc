(ns notebox.feature.settings.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.settings.queries :as q]))

(rf/reg-sub :settings/default-book (fn [db _] (q/default-book db)))
