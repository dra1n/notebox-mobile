(ns notebox.shell.events
  "Events and subs owned by the shell: app start and session reset."
  (:require [re-frame.core :as rf]))

(defn initial-db [profile]
  {:app/status  :ready
   :app/profile profile})

(rf/reg-event-db
 :app/initialize
 (fn [_ [_ profile]]
   (initial-db profile)))

(rf/reg-sub
 :app/status
 (fn [db _]
   (:app/status db)))

(rf/reg-sub
 :app/profile
 (fn [db _]
   (:app/profile db)))
