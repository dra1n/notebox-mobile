(ns notebox.domain.meta
  "The /notes/.meta.json file:

    {:collectionsList [\"slug\" ...]                  ; Luggage's list of book files
     :notesInfo [{:slug \"…\" :title \"…\" :count 3}] ; books, in display order
     :tagsInfo  {:<slug> [\"tag\" ...]}}             ; each book's distinct tags

  `:count` and `:tagsInfo` are derived from book content (`refresh-book`), never
  incremented, so they heal drift left by other clients. Keys we don't know
  are kept, and so is key order (see notebox.domain.ordered)."
  (:require [notebox.domain.note :as note]
            [notebox.domain.ordered :as ordered]))

(defn books
  "The books in display order: the notesInfo entries."
  [meta]
  (vec (:notesInfo meta)))

(defn book-info [meta slug]
  (some #(when (= slug (:slug %)) %) (:notesInfo meta)))

(defn book-tags
  "The book's tags as recorded in tagsInfo (available before the book is loaded)."
  [meta slug]
  (get (:tagsInfo meta) (keyword slug)))

(defn- update-book-info [meta slug f]
  (ordered/update meta :notesInfo
                  (fn [infos] (mapv #(if (= slug (:slug %)) (f %) %) infos))))

(defn refresh-book
  "Sets the book's count and tags from its `notes`. Only touches that book's
  entries; a book without a notesInfo entry only gets its tags refreshed."
  [meta slug notes]
  (-> meta
      (update-book-info slug #(ordered/assoc % :count (count notes)))
      (ordered/update :tagsInfo #(ordered/assoc (or % (array-map))
                                                (keyword slug)
                                                (note/distinct-tags notes)))))

(defn create-book
  "Adds a book: a notesInfo entry, empty tags and a collectionsList entry. A
  no-op for each part that's already there."
  [meta slug title]
  (cond-> (or meta (array-map))
    (not (book-info meta slug))
    (ordered/update :notesInfo (fnil conj [])
                    (array-map :slug slug :title title :count 0))

    (not (contains? (:tagsInfo meta) (keyword slug)))
    (ordered/update :tagsInfo #(ordered/assoc (or % (array-map)) (keyword slug) []))

    (not (some #{slug} (:collectionsList meta)))
    (ordered/update :collectionsList (fnil conj []) slug)))

(defn rename-book
  "Sets the book's title; a no-op if there is no such book."
  [meta slug title]
  (if (book-info meta slug)
    (update-book-info meta slug #(ordered/assoc % :title title))
    meta))

(defn delete-book
  "Removes the book from notesInfo, tagsInfo and collectionsList."
  [meta slug]
  (cond-> meta
    (contains? meta :notesInfo)
    (ordered/update :notesInfo #(into [] (remove (fn [b] (= slug (:slug b)))) %))

    (contains? meta :tagsInfo)
    (ordered/update :tagsInfo #(dissoc % (keyword slug)))

    (contains? meta :collectionsList)
    (ordered/update :collectionsList #(into [] (remove #{slug}) %))))

(defn totals
  "{:books n :notes m} as shown on the home screen (\"30 books (67 notes)\")."
  [meta]
  {:books (count (:notesInfo meta))
   :notes (reduce + 0 (keep :count (:notesInfo meta)))})
