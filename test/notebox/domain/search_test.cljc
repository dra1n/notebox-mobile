(ns notebox.domain.search-test
  "Parity with the web client's `matches-text` (notebox/module/app/utils.cljs)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [notebox.domain.search :as search]
            [notebox.test.gen :as g]))

(defn web-matches-text
  "The web client's matches-text, verbatim apart from the namespace alias."
  [text]
  (fn [note]
    (or (empty? text)
        (let [filter-text (-> text (str/trim) (str/lower-case))]
          (or (and (:title note)
                   (str/includes? (str/lower-case (:title note)) filter-text))
              (and (:tags note)
                   (str/includes? (str/lower-case (str/join " " (:tags note))) filter-text))
              (and (:text note)
                   (str/includes? (str/lower-case (:text note)) filter-text)))))))

(def note {:slug "n" :title "Новое поколение Дворянства" :text "Pastéis de NATA ☕"
           :tags ["классика" "5+"]})

(deftest parity-table
  (doseq [[query expected] [["" true]
                            ["   " true]
                            ["дворянства" true]
                            ["ДВОРЯНСТВА" true]
                            ["  поколение  " true]
                            ["pastéis" true]
                            ["nata ☕" true]
                            ["классика 5+" true]          ; across two tags, as on the web
                            ["5+" true]
                            ["missing" false]
                            ["классика дворянства" false]]]
    (testing (pr-str query)
      (is (= expected (search/matches-note? query note)))
      (is (= expected (boolean ((web-matches-text query) note))) "the web agrees"))))

(deftest notes-with-missing-fields
  (doseq [n [{:slug "a"} {:slug "b" :title nil :tags nil :text nil} {:slug "c" :tags []}]]
    (is (false? (search/matches-note? "x" n)))
    (is (true? (search/matches-note? "" n)))))

(defspec agrees-with-the-web-on-generated-notes 300
  (prop/for-all [n g/gen-note
                 q g/gen-string]
    (= (boolean ((web-matches-text q) n)) (search/matches-note? q n))))

(deftest books-and-lists
  (is (true? (search/matches-book? "анна" {:title "Л. Н. Толстой. Анна Каренина"})))
  (is (false? (search/matches-book? "comics" {:title "Мой роман"})))
  (is (false? (search/matches-book? "x" {:slug "no-title"})))
  (is (true? (search/matches-book? nil {:title "Any"})))
  (is (= ["b"] (map :slug (search/search-notes "two" [{:slug "a" :title "one"} {:slug "b" :title "Two"}]))))
  (is (nil? (search/normalize-query "  "))))
