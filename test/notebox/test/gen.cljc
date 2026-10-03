(ns notebox.test.gen
  "test.check generators shared by the property tests: JSON values with awkward
  strings, notes, books, meta files and ops. Slugs and tags come from small
  pools so that collisions (existing slugs, shared tags) are common."
  (:require [clojure.test.check.generators :as gen]
            [notebox.domain.note :as note]))

(defn- char-of [code]
  #?(:clj (str (char code)) :cljs (.fromCharCode js/String code)))

(def gen-code-unit
  "UTF-16 code units, weighted towards the cases JSON escaping cares about."
  (gen/frequency
   [[30 (gen/choose 0x20 0x7E)]                       ; printable ASCII
    [5  (gen/elements [0x22 0x5C 0x2F])]              ; " \ /
    [5  (gen/choose 0x00 0x1F)]                       ; control characters
    [10 (gen/choose 0x0400 0x04FF)]                   ; Cyrillic
    [3  (gen/elements [0x2028 0x2029 0x00E9 0x2014 0x6F22])]
    [3  (gen/choose 0xD800 0xDFFF)]]))                ; lone surrogates

(def gen-surrogate-pair
  (gen/fmap (fn [[hi lo]] (str (char-of hi) (char-of lo)))
            (gen/tuple (gen/choose 0xD800 0xDBFF) (gen/choose 0xDC00 0xDFFF))))

(def gen-string
  (gen/fmap (fn [parts] (apply str parts))
            (gen/vector (gen/frequency [[9 (gen/fmap char-of gen-code-unit)]
                                        [1 gen-surrogate-pair]])
                        0 12)))

(def gen-int (gen/large-integer* {:min -9007199254740991 :max 9007199254740991}))

(defn- ordered-map-of [gen-k gen-v]
  (gen/fmap (fn [kvs] (apply array-map (into [] cat kvs)))
            (gen/vector-distinct-by first (gen/tuple gen-k gen-v) {:max-elements 12})))

(def gen-key
  "Object keys, minus integer-like ones (\"0\", \"42\"): JavaScript always
  enumerates those first, so no JS client can write them after other keys.
  notebox.domain.json-test covers their ordering separately."
  (gen/fmap keyword (gen/such-that #(not (re-matches #"0|[1-9][0-9]*" %)) gen-string 100)))

(def gen-json
  "Any JSON value as notebox.domain.json/decode returns it."
  (gen/recursive-gen
   (fn [inner]
     (gen/one-of [(gen/vector inner 0 5)
                  (ordered-map-of gen-key inner)]))
   (gen/one-of [(gen/return nil) gen/boolean gen-int gen-string])))

;; --- the domain ---------------------------------------------------------------

(def slug-pool (mapv #(str "slug" (char-of (+ 65 %)) "xxxxx") (range 8)))
(def tag-pool ["work" "todo" "классика" "5+" "new" "😀" "Work" ""])

(def gen-slug (gen/elements slug-pool))
(def gen-tags (gen/vector-distinct (gen/elements tag-pool) {:max-elements 4}))

(def gen-note
  (gen/fmap (fn [[slug title text tags created-at]]
              (note/new-note {:slug slug :title title :text text :tags tags
                              :created-at created-at}))
            (gen/tuple gen-slug gen-string gen-string gen-tags
                       (gen/elements ["2021-01-01T00:00:00.000Z" "2026-10-03T12:00:00.000Z"]))))

(def gen-notes
  "A book file: notes with unique slugs."
  (gen/fmap (fn [ns] (vec (vals (into (array-map) (map (juxt :slug identity)) ns))))
            (gen/vector gen-note 0 8)))

(def book-pool ["bookAxxxxx" "bookBxxxxx" "bookCxxxxx" "bookDxxxxx"])
(def gen-book (gen/elements book-pool))

(def gen-meta
  "A meta file over some of the books; counts and tags may have drifted."
  (gen/fmap (fn [[books counts tags extra?]]
              (cond-> (array-map
                       :collectionsList (vec books)
                       :notesInfo (mapv (fn [b c] (array-map :slug b :title (str "Title " b) :count c))
                                        books counts)
                       :tagsInfo (apply array-map (interleave (map keyword books) (repeat tags))))
                extra? (assoc :syncedBy "desktop")))
            (gen/tuple (gen/fmap distinct (gen/vector gen-book 0 4))
                       (gen/vector (gen/choose 0 9) 4)
                       gen-tags
                       gen/boolean)))

(def gen-note-op
  (gen/one-of
   [(gen/fmap (fn [[b n]] {:op :note/add :book b :note n}) (gen/tuple gen-book gen-note))
    (gen/fmap (fn [[b slug title tags]]
                {:op :note/update :book b
                 :note (array-map :slug slug :title title :tags tags
                                  :updated-at "2026-10-03T12:30:00.000Z")})
              (gen/tuple gen-book gen-slug gen-string gen-tags))
    (gen/fmap (fn [[b s]] {:op :note/remove :book b :slug s}) (gen/tuple gen-book gen-slug))]))

(def gen-book-op
  (gen/one-of
   [(gen/fmap (fn [[b t]] {:op :book/create :book b :title t}) (gen/tuple gen-book gen-string))
    (gen/fmap (fn [[b t]] {:op :book/rename :book b :title t}) (gen/tuple gen-book gen-string))
    (gen/fmap (fn [b] {:op :book/delete :book b}) gen-book)]))

(def gen-op (gen/one-of [gen-note-op gen-book-op]))
