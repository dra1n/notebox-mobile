(ns notebox.feature.search.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.search.queries :as q]))

(rf/reg-sub :search/query (fn [db _] (q/query db)))
(rf/reg-sub :search/results (fn [db _] (q/results db)))
(rf/reg-sub :search/input-key (fn [db _] (q/input-key db)))
