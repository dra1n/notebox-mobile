(ns notebox.shell.events
  "Events owned by the shell: start-up and the session lifecycle. They
  orchestrate across features (sign-in → load the library; sign-out → reset),
  so no feature needs to know about another's events (roadmap §5.2)."
  (:require [re-frame.core :as rf]
            [notebox.feature.auth.queries :as auth]
            [notebox.feature.library.queries :as library]
            [notebox.feature.settings.queries :as settings]))

(defn initial-db [profile]
  {:app/status  :ready
   :app/profile profile})

(rf/reg-event-fx
 :app/initialize
 (fn [_ [_ profile]]
   {:db (initial-db profile)
    :fx [[:settings/load {:keys settings/keys-stored :on-ok [:settings/loaded]}]
         [:auth/check-session {:on-ok [:app/session-checked]}]]}))

(rf/reg-event-fx
 :app/session-checked
 (fn [{:keys [db]} [_ signed-in?]]
   (if signed-in?
     {:db (auth/set-status db :signed-in)
      :fx [[:dispatch [:library/load]]
           [:auth/load-account {:on-ok [:auth/account-loaded]}]]}
     {:db (auth/set-status db :signed-out)})))

(rf/reg-event-fx
 :app/login
 (fn [{:keys [db]} _]
   {:db (auth/set-status db :signing-in)
    :auth/login {:on-ok [:app/signed-in] :on-fail [:app/login-failed]}}))

(rf/reg-event-fx
 :app/signed-in
 (fn [{:keys [db]} _]
   {:db (auth/set-status db :signed-in)
    :fx [[:dispatch [:library/load]]
         [:auth/load-account {:on-ok [:auth/account-loaded]}]]}))

(rf/reg-event-fx
 :app/login-failed
 (fn [{:keys [db]} [_ err]]
   {:db (auth/set-error db err)
    :dispatch [:messaging/show {:type :error :text "Couldn't sign in to Dropbox."}]}))

(rf/reg-event-fx
 :app/logout
 (fn [_ _]
   {:auth/logout {:on-ok [:app/reset-session]}}))

(defn- signed-out-db
  "A fresh db for a signed-out user; per-device settings stay."
  [db]
  (-> (initial-db (:app/profile db))
      (assoc :settings (:settings db))
      (library/reset)
      (auth/set-status :signed-out)))

(rf/reg-event-db
 :app/reset-session
 (fn [db _] (signed-out-db db)))

(rf/reg-event-fx
 :app/session-expired
 (fn [{:keys [db]} _]
   {:db (signed-out-db db)
    :dispatch [:messaging/show {:type :error
                                :text "Your Dropbox session has expired. Please sign in again."}]}))

(rf/reg-sub :app/status (fn [db _] (:app/status db)))
(rf/reg-sub :app/profile (fn [db _] (:app/profile db)))
