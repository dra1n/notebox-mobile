(ns notebox.feature.messaging.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.messaging.queries :as q]))

(rf/reg-sub :messaging/messages (fn [db _] (q/messages db)))
