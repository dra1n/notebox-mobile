(ns notebox.feature.auth.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.auth.queries :as q]))

(rf/reg-sub :auth/status (fn [db _] (q/status db)))
(rf/reg-sub :auth/signed-in? :<- [:auth/status] (fn [s _] (= :signed-in s)))
(rf/reg-sub :auth/account (fn [db _] (q/account db)))
(rf/reg-sub :auth/error (fn [db _] (q/error db)))
