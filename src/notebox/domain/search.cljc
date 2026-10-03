(ns notebox.domain.search
  "Search, ported from the web client's `matches-text`: case-insensitive
  substring match on the trimmed query, over title, tags (joined with spaces)
  and text. Mobile adds matching book titles on the home screen.

  Lower-casing is locale-independent on both platforms (the JVM's default
  locale would otherwise change the result, e.g. Turkish dotless i)."
  (:require [clojure.string :as str]))

(defn- lower [s]
  #?(:clj  (.toLowerCase ^String s java.util.Locale/ROOT)
     :cljs (.toLowerCase s)))

(defn normalize-query
  "The query as matched: trimmed and lower-cased; nil when blank."
  [query]
  (let [q (some-> query str/trim lower)]
    (when (seq q) q)))

(defn- contains-q? [s q]
  (and (string? s) (str/includes? (lower s) q)))

(defn matches-note?
  "Does the note match `query`? A blank query matches every note.

  As on the web, tags are joined with spaces before matching, so \"a b\"
  matches a note tagged [\"xa\" \"bx\"]."
  [query note]
  (if-let [q (normalize-query query)]
    (boolean (or (contains-q? (:title note) q)
                 (and (seq (:tags note)) (contains-q? (str/join " " (:tags note)) q))
                 (contains-q? (:text note) q)))
    true))

(defn matches-book?
  "Does the book's title match `query`? A blank query matches every book."
  [query book-info]
  (if-let [q (normalize-query query)]
    (contains-q? (:title book-info) q)
    true))

(defn search-notes
  "The notes in `notes` that match `query`, in order."
  [query notes]
  (filterv #(matches-note? query %) notes))
