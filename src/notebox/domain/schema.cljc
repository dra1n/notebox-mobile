(ns notebox.domain.schema
  "Checks for decoded Dropbox files, run before we change and upload them.
  Lenient about what other clients may leave out (titles, tags, timestamps)
  and about extra keys; strict about what we rely on (note slugs, the shapes
  of the meta collections). Each check returns a vector of problems; empty
  means valid.")

(defn- string-vec? [x] (and (vector? x) (every? string? x)))

(defn note-problems
  "Problems with one note map."
  [n]
  (if-not (map? n)
    ["a note is not an object"]
    (cond-> []
      (not (and (string? (:slug n)) (seq (:slug n))))
      (conj "a note has no slug")

      (and (contains? n :title) (not (or (nil? (:title n)) (string? (:title n)))))
      (conj (str "note " (:slug n) ": title is not a string"))

      (and (contains? n :text) (not (or (nil? (:text n)) (string? (:text n)))))
      (conj (str "note " (:slug n) ": text is not a string"))

      (and (contains? n :tags) (not (or (nil? (:tags n)) (string-vec? (:tags n)))))
      (conj (str "note " (:slug n) ": tags is not a list of strings")))))

(defn book-problems
  "Problems with a decoded book file (a vector of notes)."
  [notes]
  (if-not (vector? notes)
    ["the book file is not a list"]
    (let [slugs (keep :slug (filter map? notes))
          dupes (for [[s c] (frequencies slugs) :when (> c 1)] s)]
      (cond-> (into [] (mapcat note-problems) notes)
        (seq dupes) (conj (str "duplicate note slugs: " (pr-str (sort dupes))))))))

(defn- book-info-problems [b]
  (if-not (map? b)
    ["a notesInfo entry is not an object"]
    (cond-> []
      (not (and (string? (:slug b)) (seq (:slug b))))
      (conj "a notesInfo entry has no slug")
      (and (contains? b :count) (not (and (integer? (:count b)) (>= (:count b) 0))))
      (conj (str "book " (:slug b) ": count is not a non-negative integer")))))

(defn meta-problems
  "Problems with a decoded .meta.json."
  [m]
  (if-not (map? m)
    ["the meta file is not an object"]
    (cond-> []
      (and (contains? m :collectionsList) (not (string-vec? (:collectionsList m))))
      (conj "collectionsList is not a list of strings")

      (and (contains? m :notesInfo) (not (vector? (:notesInfo m))))
      (conj "notesInfo is not a list")

      (vector? (:notesInfo m))
      (into (mapcat book-info-problems) (:notesInfo m))

      (and (contains? m :tagsInfo)
           (not (and (map? (:tagsInfo m)) (every? string-vec? (vals (:tagsInfo m))))))
      (conj "tagsInfo is not a map of tag lists"))))
