(ns notebox.domain.ordered-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [notebox.domain.ordered :as ordered]))

(def gen-ordered-map
  (gen/fmap #(apply array-map (into [] cat %))
            (gen/vector-distinct-by first (gen/tuple gen/keyword gen/small-integer)
                                    {:max-elements 20})))

(defspec assoc-appends-new-keys-and-keeps-old-positions 300
  (prop/for-all [m gen-ordered-map
                 k gen/keyword
                 v gen/small-integer]
    (let [m' (ordered/assoc m k v)]
      (and (= v (get m' k))
           (= (assoc m k v) m')
           (if (contains? m k)
             (= (keys m) (keys m'))
             (= (concat (keys m) [k]) (keys m')))))))

(deftest update-and-merge
  (let [m (apply array-map (interleave (map #(keyword (str "k" %)) (range 10)) (range 10)))]
    (is (= (concat (keys m) [:new]) (keys (ordered/update m :new (fnil inc 0)))))
    (is (= 1 (:new (ordered/update m :new (fnil inc 0)))))
    (is (= (keys m) (keys (ordered/update m :k3 inc))))
    (is (= [:a :b :c :d] (keys (ordered/merge (array-map :a 1 :b 2) (array-map :c 3 :a 9 :d 4)))))
    (is (= {:a 9 :b 2 :c 3 :d 4} (ordered/merge (array-map :a 1 :b 2) (array-map :c 3 :a 9 :d 4))))
    (is (= {:a 1} (ordered/merge nil {:a 1})))))
