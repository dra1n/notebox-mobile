(ns notebox.feature.settings.queries
  "Per-device settings (persisted in the kv-store by the shell's effects).")

(def keys-stored [:default-book])

(defn default-book [db] (get-in db [:settings :default-book]))
(defn set-default-book [db slug] (assoc-in db [:settings :default-book] slug))
(defn merge-loaded [db m] (update db :settings merge (select-keys m keys-stored)))
