(ns notebox.feature.search.queries
  "Search (§1, §1.1): scope {:type :all} (notes, tags and book titles across
  every book), {:type :book :book slug} (one book's notes), or {:type :books}
  (book titles only)."
  (:require [notebox.domain.search :as search]
            [notebox.feature.library.queries :as library]))

(defn query [db] (get-in db [:search :query] ""))
(defn scope [db] (get-in db [:search :scope] {:type :all}))

(defn set-query [db q scope]
  (assoc db :search {:query (or q "") :scope (or scope {:type :all})}))

(defn clear [db] (dissoc db :search))

(defn results
  "{:books [book-info] :notes [{:book slug :note n}] :pending n}: matches so far,
  and how many books still have to load (results stream in as they do)."
  [db]
  (let [q   (query db)
        {:keys [type book]} (scope db)
        loaded (library/loaded-books db)]
    (case type
      :books {:books (filterv #(search/matches-book? q %) (library/books db)) :notes [] :pending 0}
      :book  {:books []
              :notes (mapv (fn [n] {:book book :note n})
                           (search/search-notes q (get loaded book)))
              :pending (if (contains? loaded book) 0 1)}
      {:books (if (search/normalize-query q)
                (filterv #(search/matches-book? q %) (library/books db))
                [])
       :notes (vec (for [{:keys [slug]} (library/books db)
                         n (search/search-notes q (get loaded slug))]
                     {:book slug :note n}))
       :pending (count (remove #(contains? loaded (:slug %)) (library/books db)))})))
