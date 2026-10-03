(ns notebox.feature.books.queries
  (:require [notebox.feature.library.queries :as library]
            [notebox.feature.settings.queries :as settings]))

(defn book-list
  "The Books screen: every book with its count, and which one is the default."
  [db]
  (let [default (settings/default-book db)]
    (mapv #(assoc % :default? (= default (:slug %))) (library/books db))))
