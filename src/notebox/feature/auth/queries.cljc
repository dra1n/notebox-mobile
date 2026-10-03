(ns notebox.feature.auth.queries
  "The session: :status #{:unknown :signed-out :signing-in :signed-in}, the
  account, and the last sign-in error.")

(defn status [db] (get-in db [:auth :status] :unknown))
(defn account [db] (get-in db [:auth :account]))
(defn error [db] (get-in db [:auth :error]))

(defn set-status [db status]
  (cond-> (assoc-in db [:auth :status] status)
    (not= :signed-out status) (update :auth dissoc :error)))

(defn set-error [db err]
  (-> db (assoc-in [:auth :status] :signed-out) (assoc-in [:auth :error] err)))

(defn set-account [db account] (assoc-in db [:auth :account] account))
