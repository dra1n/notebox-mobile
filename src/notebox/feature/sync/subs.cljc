(ns notebox.feature.sync.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.sync.queries :as q]))

(rf/reg-sub :sync/pending (fn [db _] (q/pending db)))
(rf/reg-sub :sync/saving? :<- [:sync/pending] (fn [n _] (pos? n)))
