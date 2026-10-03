(ns notebox.feature.auth.events
  "The shell orchestrates sign-in and sign-out (notebox.shell.events); this
  feature only owns the session state."
  (:require [re-frame.core :as rf]
            [notebox.feature.auth.queries :as q]))

(rf/reg-event-db
 :auth/account-loaded
 (fn [db [_ account]] (q/set-account db account)))
