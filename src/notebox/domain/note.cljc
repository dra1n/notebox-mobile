(ns notebox.domain.note
  "Notes and book files. A book file (/notes/<book-slug>.json) is a vector of
  notes; a note is a map with at least :slug, usually :title :text :tags
  :created-at and, after an edit, :updated-at. Keys other clients add are kept.

  Key order follows the web client: existing keys keep their position, new keys
  are appended (it merges edits with Object.assign)."
  (:require [nano-id.core :as nano-id]
            [notebox.domain.ordered :as ordered]))

(def slug-length 10)

(defn new-slug
  "A random note/book slug, as the web client makes them: nano-id of 10 chars."
  []
  (nano-id/nano-id slug-length))

(defn new-note
  "A note to add. `created-at` is an ISO-8601 string (see notebox.domain.time)."
  [{:keys [slug title text tags created-at]}]
  (array-map :slug slug
             :title (or title "")
             :text (or text "")
             :tags (vec tags)
             :created-at created-at))

(defn edit
  "`note` with `changes` (a map of changed fields) and :updated-at."
  [note changes updated-at]
  (-> (ordered/merge note changes)
      (ordered/assoc :updated-at updated-at)))

;; --- book files (vectors of notes) --------------------------------------------

(defn index-of
  "The index of the note with `slug` in `notes`, or nil."
  [notes slug]
  (first (keep-indexed (fn [i n] (when (= slug (:slug n)) i)) notes)))

(defn find-note [notes slug]
  (some #(when (= slug (:slug %)) %) notes))

(defn add-note
  "Appends `note` unless a note with its slug is already there."
  [notes note]
  (if (index-of notes (:slug note))
    (vec notes)
    (conj (vec notes) note)))

(defn update-note
  "Merges `note` into the note with the same slug (as the web client does).
  Returns [notes' re-added?]: a missing note is appended again (edit wins)."
  [notes note]
  (if-let [i (index-of notes (:slug note))]
    [(clojure.core/update (vec notes) i ordered/merge note) false]
    [(conj (vec notes) note) true]))

(defn remove-note
  "Removes the note with `slug`; a no-op if there is none."
  [notes slug]
  (into [] (remove #(= slug (:slug %))) notes))

(defn distinct-tags
  "The book's tags: every note's tags in note order, without repeats."
  [notes]
  (into [] (comp (mapcat :tags) (distinct)) notes))
