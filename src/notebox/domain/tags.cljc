(ns notebox.domain.tags
  "The tag index for the Tags screen: every tag with its number of notes.

  Counts need the books' notes. Books that aren't loaded yet still contribute
  their tag names (from tagsInfo), and such tags are marked incomplete until
  every book that has them is loaded."
  (:require [clojure.string :as str]
            [notebox.domain.meta :as meta]))

(defn- sort-key [tag]
  #?(:clj  (.toLowerCase ^String tag java.util.Locale/ROOT)
     :cljs (.toLowerCase tag)))

(defn tag-index
  "[{:tag t :count n :complete? bool} ...] sorted by tag, case-insensitively.
  `loaded` maps book slug → notes for the books that are loaded."
  [meta-file loaded]
  (let [counts   (frequencies (for [[_ notes] loaded
                                    n notes
                                    t (distinct (:tags n))]
                                t))
        pending  (for [b (meta/books meta-file)
                       :when (not (contains? loaded (:slug b)))
                       t (meta/book-tags meta-file (:slug b))]
                   t)
        partial? (set pending)
        all-tags (distinct (concat (keys counts) pending))]
    (->> all-tags
         (remove str/blank?)
         (map (fn [t] {:tag t
                       :count (get counts t 0)
                       :complete? (not (partial? t))}))
         (sort-by (juxt (comp sort-key :tag) :tag))
         vec)))
