(ns notebox.feature.library.queries
  "The library in app-db: the meta and the books loaded so far.

    {:library {:meta-status #{:unloaded :loading :ready :error}
               :meta {...}                                   ; .meta.json
               :books {slug {:status #{:loading :ready :error} :notes [...]}}
               :last-active slug}}

  \"Loaded\" is explicit (roadmap §3.4 hazard 7)."
  (:require [notebox.domain.meta :as meta]
            [notebox.domain.note :as note]
            [notebox.domain.ops :as ops]))

(defn meta-status [db] (get-in db [:library :meta-status] :unloaded))
(defn meta-file [db] (get-in db [:library :meta]))
(defn books [db] (meta/books (meta-file db)))
(defn book-info [db slug] (meta/book-info (meta-file db) slug))
(defn book-status [db slug] (get-in db [:library :books slug :status] :unloaded))
(defn book-notes [db slug] (get-in db [:library :books slug :notes]))
(defn last-active [db] (get-in db [:library :last-active]))

(defn find-note [db book slug] (note/find-note (book-notes db book) slug))

(defn loaded-books
  "{slug notes} of the books that are loaded."
  [db]
  (into {} (for [[slug {:keys [status notes]}] (get-in db [:library :books])
                 :when (= :ready status)]
             [slug notes])))

(defn unloaded-slugs
  "The slugs of books that aren't loaded or loading."
  [db]
  (vec (for [{:keys [slug]} (books db)
             :when (#{:unloaded :error} (book-status db slug))]
         slug)))

(defn set-meta-loading [db] (assoc-in db [:library :meta-status] :loading))

(defn set-meta [db m]
  (-> db
      (assoc-in [:library :meta] m)
      (assoc-in [:library :meta-status] :ready)))

(defn set-meta-error [db err]
  (-> db
      (assoc-in [:library :meta-status] :error)
      (assoc-in [:library :meta-error] err)))

(defn set-book-loading [db slug] (assoc-in db [:library :books slug :status] :loading))

(defn set-book [db slug notes]
  (assoc-in db [:library :books slug] {:status :ready :notes (vec notes)}))

(defn set-book-error [db slug err]
  (assoc-in db [:library :books slug] {:status :error :error err}))

(defn set-last-active [db slug] (assoc-in db [:library :last-active] slug))

(defn reset [db] (dissoc db :library))

(defn apply-op
  "`db` with `op` applied optimistically, by the same function Dropbox's copy
  gets (notebox.domain.ops). A note op on a book that isn't loaded leaves the
  db alone; the save's result fills it in."
  [db op]
  (let [kind  (ops/book-file op)
        slug  (:book op)
        notes (book-notes db slug)]
    (if (and (= :write kind) (nil? notes))
      db
      (let [{m :meta notes' :notes} (ops/apply-op op {:meta (meta-file db) :notes notes})]
        (cond-> (assoc-in db [:library :meta] m)
          (#{:write :create} kind) (set-book slug notes')
          (= :delete kind) (update-in [:library :books] dissoc slug))))))

(defn adopt-results
  "`db` with what Dropbox holds after the ops: the last meta, and each
  written book's notes."
  [db ops results]
  (reduce (fn [db [op {m :meta notes :notes}]]
            (cond-> (assoc-in db [:library :meta] m)
              (#{:write :create} (ops/book-file op)) (set-book (:book op) notes)))
          db
          (map vector ops results)))
