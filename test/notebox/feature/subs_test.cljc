(ns notebox.feature.subs-test
  "Derived subscriptions against example app-dbs: books with counts and the
  default flag, the tag index, search results, the default-book fallback."
  (:require [clojure.test :refer [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [notebox.feature.auth.queries :as auth]
            [notebox.feature.library.queries :as library]
            [notebox.feature.search.queries :as search]
            [notebox.feature.settings.queries :as settings]
            [notebox.feature.sync.queries :as sync]
            [notebox.shell.app]))

(def meta-file
  {:collectionsList ["a" "b" "c"]
   :notesInfo [{:slug "a" :title "Anna Karenina" :count 2}
               {:slug "b" :title "Comics" :count 1}
               {:slug "c" :title "Мой роман" :count 0}]
   :tagsInfo {:a ["classic"] :b ["favorite" "new"] :c []}})

(def db
  (-> {}
      (library/set-meta meta-file)
      (library/set-book "a" [{:slug "a1" :title "Новое поколение" :tags ["classic"]}
                             {:slug "a2" :title "Если добро" :text "новое" :tags ["classic" "favorite"]}])
      (library/set-book-loading "c")))

(defn- sub [d q] (reset! app-db d) @(rf/subscribe q))

(deftest library
  (is (= :ready (sub db [:library/meta-status])))
  (is (= meta-file (sub db [:library/meta])))
  (is (= {:books 3 :notes 3} (sub db [:library/totals])))
  (is (= "Comics" (:title (sub db [:library/book-info "b"]))))
  (is (= :loading (sub db [:library/book-status "c"])))
  (is (= :unloaded (sub db [:library/book-status "b"])))
  (is (= ["a"] (keys (sub db [:library/loaded-books]))))
  (is (= ["b"] (library/unloaded-slugs db)))
  (is (= "a2" (:slug (sub db [:library/note "a" "a2"])))))

(deftest books-with-counts-and-default
  (is (= [{:slug "a" :title "Anna Karenina" :count 2 :default? false}
          {:slug "b" :title "Comics" :count 1 :default? true}
          {:slug "c" :title "Мой роман" :count 0 :default? false}]
         (sub (settings/set-default-book db "b") [:books/list]))))

(deftest default-book-fallback
  (testing "the default book, if it exists"
    (is (= "b" (sub (settings/set-default-book db "b") [:editor/default-book]))))
  (testing "a default that was deleted falls back to the last book opened"
    (is (= "c" (sub (-> db (settings/set-default-book "gone") (library/set-last-active "c"))
                    [:editor/default-book]))))
  (testing "then the first book"
    (is (= "a" (sub db [:editor/default-book]))))
  (testing "no books"
    (is (nil? (sub {} [:editor/default-book])))))

(deftest tag-index
  (is (= [{:tag "classic" :count 2 :complete? true}
          {:tag "favorite" :count 1 :complete? false}
          {:tag "new" :count 0 :complete? false}]
         (sub db [:tags/index]))
      "b isn't loaded: its tags show, counts are incomplete"))

(deftest search-results
  (testing "everywhere: notes of loaded books, book titles; books still to load"
    (is (= {:books [] :notes [{:book "a" :note {:slug "a1" :title "Новое поколение" :tags ["classic"]}}
                              {:book "a" :note {:slug "a2" :title "Если добро" :text "новое"
                                                :tags ["classic" "favorite"]}}]
            :pending 2}
           (sub (search/set-query db "НОВОЕ" {:type :all}) [:search/results])))
    (is (= ["Comics"] (map :title (:books (sub (search/set-query db "com" nil) [:search/results]))))))
  (testing "a blank query lists no books"
    (is (= [] (:books (sub (search/set-query db "  " {:type :all}) [:search/results])))))
  (testing "in one book"
    (is (= ["a2"] (map (comp :slug :note)
                       (:notes (sub (search/set-query db "добро" {:type :book :book "a"}) [:search/results])))))
    (is (= 1 (:pending (sub (search/set-query db "x" {:type :book :book "b"}) [:search/results])))))
  (testing "the search field's input key changes when the query is set from outside or cleared"
    (is (= 0 (sub db [:search/input-key])))
    (is (= 1 (sub (search/clear (search/set-query db "x" nil)) [:search/input-key]))))
  (testing "book titles only"
    (is (= ["Мой роман"] (map :title (:books (sub (search/set-query db "роман" {:type :books})
                                                  [:search/results])))))))

(deftest small-state
  (is (false? (sub db [:sync/saving?])))
  (is (= 1 (sub (sync/started db) [:sync/pending])))
  (is (true? (sub (sync/started db) [:sync/saving?])))
  (is (= 0 (sync/pending (sync/finished (sync/finished db)))) "never below zero")
  (is (= :unknown (sub db [:auth/status])))
  (is (true? (sub (auth/set-status db :signed-in) [:auth/signed-in?])))
  (is (= {:email "x"} (sub (auth/set-account db {:email "x"}) [:auth/account])))
  (is (= {:type :network} (sub (auth/set-error db {:type :network}) [:auth/error])))
  (is (nil? (auth/error (auth/set-status (auth/set-error db {:type :x}) :signing-in))))
  (is (= :ready (sub {:app/status :ready} [:app/status])))
  (is (= :test (sub {:app/profile :test} [:app/profile]))))
