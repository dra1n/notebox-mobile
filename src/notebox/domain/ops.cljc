(ns notebox.domain.ops
  "Every change to Dropbox data is an op (plain data). The same pure function
  applies an op to local state and to freshly downloaded remote files, so a
  retry after a conflict re-applies it to whatever is there now.

    {:op :note/add    :book b :note n}    ; no-op if the slug exists
    {:op :note/update :book b :note n}    ; merge by slug; missing → re-add + warning
    {:op :note/remove :book b :slug s}    ; no-op if missing
    {:op :book/create :book b :title t}   ; empty book file + meta entries
    {:op :book/rename :book b :title t}   ; meta only
    {:op :book/delete :book b}            ; meta, then the book file is deleted

  Moving a note is two ops: :note/add to the target, then :note/remove from the
  source (on failure in between you get a duplicate, never a loss).

  Every op is idempotent: applying it twice gives the same files as once."
  (:require [notebox.domain.meta :as meta]
            [notebox.domain.note :as note]))

(def op-types #{:note/add :note/update :note/remove :book/create :book/rename :book/delete})

(defn- unknown-op [o]
  (throw (ex-info (str "Unknown op: " (pr-str (:op o))) {:type :op/unknown :op o})))

(defn book-file
  "What the op does to its book file: :write (download, change, upload),
  :create (upload [] unless it exists), :delete, or nil (untouched)."
  [{:keys [op] :as o}]
  (case op
    (:note/add :note/update :note/remove) :write
    :book/create :create
    :book/delete :delete
    :book/rename nil
    (unknown-op o)))

(defn apply-op
  "Applies `op` to the current files of its book: {:meta m :notes v}. `:notes`
  is the book file's content (ignored for ops that don't write it). Returns
  {:meta m' :notes v' :warnings [...]}; `:notes` is nil when the book file is
  deleted, and [] when it's created."
  [{:keys [op book] :as o} {meta-file :meta notes :notes}]
  (case op
    :note/add
    (let [notes' (note/add-note notes (:note o))]
      {:meta (meta/refresh-book meta-file book notes') :notes notes' :warnings []})

    :note/update
    (let [[notes' re-added?] (note/update-note notes (:note o))]
      {:meta     (meta/refresh-book meta-file book notes')
       :notes    notes'
       :warnings (if re-added?
                   [{:type :note/re-added :book book :slug (-> o :note :slug)}]
                   [])})

    :note/remove
    (let [notes' (note/remove-note notes (:slug o))]
      {:meta (meta/refresh-book meta-file book notes') :notes notes' :warnings []})

    :book/create
    (let [notes' (vec notes)]
      {:meta (-> meta-file
                 (meta/create-book book (:title o))
                 (meta/refresh-book book notes'))
       :notes notes'
       :warnings []})

    :book/rename
    {:meta (meta/rename-book meta-file book (:title o)) :notes notes :warnings []}

    :book/delete
    {:meta (meta/delete-book meta-file book) :notes nil :warnings []}

    (unknown-op o)))
