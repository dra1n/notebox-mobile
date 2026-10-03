(ns notebox.domain.model-test
  "Notes, meta, tags, schema, time: examples beyond what the ops properties cover."
  (:require [clojure.test :refer [deftest is testing]]
            [notebox.domain.meta :as meta]
            [notebox.domain.note :as note]
            [notebox.domain.schema :as schema]
            [notebox.domain.tags :as tags]
            [notebox.domain.time :as time]))

(deftest slugs
  (let [slugs (repeatedly 2000 note/new-slug)]
    (is (every? #(re-matches #"[A-Za-z0-9_-]{10}" %) slugs))
    (is (apply distinct? slugs))))

(deftest new-and-edited-notes
  (let [n (note/new-note {:slug "s" :title "T" :tags '("a") :created-at "t0"})]
    (is (= [:slug :title :text :tags :created-at] (keys n)))
    (is (= {:slug "s" :title "T" :text "" :tags ["a"] :created-at "t0"} n))
    (is (vector? (:tags n)))
    (let [e (note/edit n {:title "T2" :pinned true} "t1")]
      (is (= [:slug :title :text :tags :created-at :pinned :updated-at] (keys e)))
      (is (= "T2" (:title e))))))

(deftest book-file-helpers
  (let [a {:slug "a"} b {:slug "b"}]
    (is (= 1 (note/index-of [a b] "b")))
    (is (nil? (note/index-of [a b] "c")))
    (is (= b (note/find-note [a b] "b")))
    (is (= [a b] (note/add-note (list a) b)))
    (is (= [a] (note/add-note [a] {:slug "a" :title "dup"})))
    (is (= [[{:slug "a" :title "x"}] false] (note/update-note [a] {:slug "a" :title "x"})))
    (is (= [b] (note/remove-note [a b] "a")))
    (is (= ["x" "y" "z"] (note/distinct-tags [{:tags ["x" "y"]} {} {:tags ["y" "z"]}])))))

(deftest meta-helpers
  (let [m {:notesInfo [{:slug "a" :title "A" :count 1}] :tagsInfo {:a ["t"]}}]
    (is (= [{:slug "a" :title "A" :count 1}] (meta/books m)))
    (is (= ["t"] (meta/book-tags m "a")))
    (testing "refresh of a book without a notesInfo entry only sets its tags"
      (let [m' (meta/refresh-book m "z" [{:tags ["q"]}])]
        (is (= (:notesInfo m) (:notesInfo m')))
        (is (= ["q"] (meta/book-tags m' "z")))))
    (testing "refresh creates tagsInfo when it's missing"
      (is (= {:z []} (:tagsInfo (meta/refresh-book {} "z" [])))))
    (testing "create on an empty or missing meta"
      (is (= {:notesInfo [{:slug "b" :title "B" :count 0}] :tagsInfo {:b []} :collectionsList ["b"]}
             (meta/create-book nil "b" "B"))))
    (testing "delete when some collections are missing"
      (is (= {:x 1} (meta/delete-book {:x 1} "a"))))))

(deftest tag-index
  (let [m {:notesInfo [{:slug "a"} {:slug "b"} {:slug "c"}]
           :tagsInfo {:a ["work" "Новое"] :b ["work" "later"] :c []}}
        loaded {"a" [{:tags ["work" "Новое"]} {:tags ["work"]} {:tags ["work" "work"]}]
                "c" [{:tags [""]}]}]
    (is (= [{:tag "later" :count 0 :complete? false}
            {:tag "work" :count 3 :complete? false}
            {:tag "Новое" :count 1 :complete? true}]
           (tags/tag-index m loaded)))
    (is (every? :complete? (tags/tag-index m (assoc loaded "b" [{:tags ["work" "later"]}]))))
    (is (= [] (tags/tag-index {} {})))
    (is (= ["a" "B" "c"] (map :tag (tags/tag-index {} {"x" [{:tags ["c" "B" "a"]}]}))))))

(deftest schema-checks
  (testing "valid"
    (is (= [] (schema/book-problems [{:slug "a"} {:slug "b" :title nil :tags ["x"] :extra 1}])))
    (is (= [] (schema/meta-problems {})))
    (is (= [] (schema/meta-problems {:collectionsList ["a"] :notesInfo [{:slug "a" :count 0}]
                                     :tagsInfo {:a []} :other {:x 1}}))))
  (testing "invalid books"
    (is (= ["the book file is not a list"] (schema/book-problems {})))
    (is (= ["a note is not an object"] (schema/book-problems [1])))
    (is (= ["a note has no slug"] (schema/book-problems [{:title "x"}])))
    (is (= ["a note has no slug"] (schema/book-problems [{:slug ""}])))
    (is (= ["note a: title is not a string" "note a: text is not a string"
            "note a: tags is not a list of strings"]
           (schema/book-problems [{:slug "a" :title 1 :text [] :tags "x"}])))
    (is (= ["duplicate note slugs: (\"a\")"] (schema/book-problems [{:slug "a"} {:slug "a"}]))))
  (testing "invalid meta"
    (is (= ["the meta file is not an object"] (schema/meta-problems [])))
    (is (= ["collectionsList is not a list of strings"] (schema/meta-problems {:collectionsList [1]})))
    (is (= ["notesInfo is not a list"] (schema/meta-problems {:notesInfo {}})))
    (is (= ["a notesInfo entry is not an object"] (schema/meta-problems {:notesInfo [1]})))
    (is (= ["a notesInfo entry has no slug" "book a: count is not a non-negative integer"]
           (schema/meta-problems {:notesInfo [{:title "x"} {:slug "a" :count -1}]})))
    (is (= ["tagsInfo is not a map of tag lists"] (schema/meta-problems {:tagsInfo {:a "x"}})))))

(deftest iso-strings-match-toISOString
  (is (= "1970-01-01T00:00:00.000Z" (time/iso-string 0)))
  (is (= "2026-10-03T12:34:56.789Z" (time/iso-string 1791030896789)))
  (is (= "2021-03-03T03:03:03.033Z" (time/iso-string 1614740583033))))
