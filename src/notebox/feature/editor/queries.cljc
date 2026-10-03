(ns notebox.feature.editor.queries
  "Turning editor actions into ops (roadmap §3.3: every web save flow)."
  (:require [notebox.domain.note :as note]
            [notebox.feature.library.queries :as library]
            [notebox.feature.settings.queries :as settings]))

(defn default-book
  "The book a new note goes to: the default book (§1.1) if it still exists,
  else the last book opened, else the first book."
  [db]
  (let [exists? #(and % (library/book-info db %))]
    (or (some #(when (exists? %) %) [(settings/default-book db) (library/last-active db)])
        (:slug (first (library/books db))))))

(defn create-ops
  "Ops for a new note: into `book`, or into a new book titled `new-book-title`."
  [{:keys [book new-book-title title text tags]} {:keys [now new-slugs]}]
  (let [[note-slug book-slug] new-slugs
        n (note/new-note {:slug note-slug :title title :text text :tags tags :created-at now})]
    (if (seq new-book-title)
      [{:op :book/create :book book-slug :title new-book-title}
       {:op :note/add :book book-slug :note n}]
      [{:op :note/add :book book :note n}])))

(defn update-ops
  "Ops for an edited note: in place, moved to `target-book`, or moved to a new
  book titled `new-book-title`. A move adds first and removes second, so a
  failure in between duplicates the note instead of losing it."
  [current book changes {:keys [target-book new-book-title]} {:keys [now new-slugs]}]
  (let [edited (note/edit current changes now)
        slug   (:slug current)
        new-b  (second new-slugs)]
    (cond
      (seq new-book-title)
      [{:op :book/create :book new-b :title new-book-title}
       {:op :note/add :book new-b :note edited}
       {:op :note/remove :book book :slug slug}]

      (and target-book (not= target-book book))
      [{:op :note/add :book target-book :note edited}
       {:op :note/remove :book book :slug slug}]

      :else
      [{:op :note/update :book book :note edited}])))

(defn final-book
  "Where the note lives after `ops`."
  [ops]
  (:book (last (filter #(#{:note/add :note/update} (:op %)) ops))))
