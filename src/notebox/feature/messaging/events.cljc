(ns notebox.feature.messaging.events
  (:require [re-frame.core :as rf]
            [notebox.feature.messaging.queries :as q]))

(rf/reg-event-fx
 :messaging/show
 [(rf/inject-cofx :notebox/uuid)]
 (fn [{:keys [db uuid]} [_ msg]]
   {:db (q/show db uuid msg)
    :dispatch-later {:ms (q/duration-ms (:type msg)) :dispatch [:messaging/dismiss uuid]}}))

(rf/reg-event-db
 :messaging/dismiss
 (fn [db [_ id]] (q/dismiss db id)))
