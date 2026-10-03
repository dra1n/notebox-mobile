(ns notebox.feature.messaging.queries
  "Toasts (flash messages): {:id :type #{:notice :error} :text}.")

(def notice-ms 5000)
(def error-ms 10000)

(defn messages [db] (get-in db [:messaging :messages] []))

(defn show
  "`db` with the message appended. `id` must be unique across resets (a UUID),
  so an old dismiss timer can't remove a newer message."
  [db id {:keys [type text]}]
  (update-in db [:messaging :messages] (fnil conj []) {:id id :type (or type :notice) :text text}))

(defn dismiss [db id]
  (update-in db [:messaging :messages] #(into [] (remove (fn [m] (= id (:id m)))) %)))

(defn duration-ms [type] (if (= :error type) error-ms notice-ms))
