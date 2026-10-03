(ns notebox.feature.sync.queries
  "Saves in flight (submitted to Dropbox, not yet answered).")

(defn pending [db] (get-in db [:sync :pending] 0))
(defn started [db] (update-in db [:sync :pending] (fnil inc 0)))
(defn finished [db] (update-in db [:sync :pending] #(max 0 (dec (or % 0)))))
