(ns notebox.fx.auth
  "Effect handlers over the Dropbox session (notebox.dropbox.auth):

    {:auth/check-session {:on-ok [...]}}               ; + signed-in? boolean
    {:auth/login {:on-ok [...] :on-fail [...]}}         ; the browser sign-in flow
    {:auth/logout {:on-ok [...]}}
    {:auth/load-account {:on-ok [...] :on-fail [...]}}  ; + {:email :name :account-id}

  Plain functions; notebox.shell.effects registers them."
  (:require [notebox.dropbox.api :as api]
            [notebox.dropbox.auth :as auth]
            [notebox.fx.util :as util]))

(defn effects
  "{fx-id handler} over the `auth` and `client` components."
  [{auth-c :auth client :client alive :alive}]
  (let [done #(util/dispatch-result alive %1 %2)]
    {:auth/check-session #(done (auth/signed-in? auth-c) %)
     :auth/login         #(done (auth/login! auth-c) %)
     :auth/logout        #(done (auth/logout! auth-c) %)
     :auth/load-account  #(done (api/current-account client) %)}))
