(ns notebox.feature.settings.events
  (:require [re-frame.core :as rf]
            [notebox.feature.settings.queries :as q]))

(rf/reg-event-db
 :settings/loaded
 (fn [db [_ m]] (q/merge-loaded db m)))

(rf/reg-event-fx
 :settings/set-default-book
 (fn [{:keys [db]} [_ slug]]
   {:db (q/set-default-book db slug)
    :settings/save {:key :default-book :value slug}}))
