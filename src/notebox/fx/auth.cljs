(ns notebox.fx.auth
  "Effects over the Dropbox session (notebox.dropbox.auth):

    {:auth/check-session {:on-ok [...]}}               ; + signed-in? boolean
    {:auth/login {:on-ok [...] :on-fail [...]}}         ; the browser sign-in flow
    {:auth/logout {:on-ok [...]}}
    {:auth/load-account {:on-ok [...] :on-fail [...]}}  ; + {:email :name :account-id}"
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.auth :as auth]
            [notebox.fx.util :as util]))

(def fx-ids [:auth/check-session :auth/login :auth/logout :auth/load-account])

(defmethod ig/init-key :notebox.fx/auth [_ {auth-c :auth client :client}]
  (let [alive (util/alive)
        done  #(util/dispatch-result alive %1 %2)]
    (rf/reg-fx :auth/check-session #(done (auth/signed-in? auth-c) %))
    (rf/reg-fx :auth/login #(done (auth/login! auth-c) %))
    (rf/reg-fx :auth/logout #(done (auth/logout! auth-c) %))
    (rf/reg-fx :auth/load-account #(done (api/current-account client) %))
    {:auth auth-c :alive alive}))

(defmethod ig/halt-key! :notebox.fx/auth [_ {:keys [alive]}]
  (reset! alive false)
  (util/clear-fx! fx-ids))
