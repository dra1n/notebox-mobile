(ns notebox.ui.hiccup
  "Inspecting the hiccup of presentational components in node (React Native
  is stubbed by test/node/stub-native.js): expand nested CLJS components, then
  find texts, testIDs and props."
  (:require [clojure.walk :as walk]))

(defn expand
  "Fully expands `h`: [f & args] where f is a CLJS fn is replaced by its
  result (form-2 components: the inner fn is called with the same args)."
  [h]
  (cond
    (and (vector? h) (fn? (first h)))
    (let [[f & args] h
          r (apply f args)]
      (expand (if (fn? r) (apply r args) r)))

    (vector? h) (into [] (map expand) h)
    (seq? h) (doall (map expand h))
    :else h))

(defn- nodes [tree]
  (filter vector? (tree-seq coll? seq tree)))

(defn native-nodes
  "Every [:> component props & children] in the expanded tree."
  [tree]
  (filter #(= :> (first %)) (nodes tree)))

(defn texts
  "Every string in the tree (component names excluded), in order."
  [tree]
  (let [out (atom [])]
    (walk/prewalk (fn [x]
                    (when (and (vector? x) (= :> (first x)))
                      (doseq [c (drop 3 x) :when (string? c)] (swap! out conj c)))
                    x)
                  tree)
    @out))

(defn by-test-id
  "Props of the native node with testID `id` (nil if none)."
  [tree id]
  (some (fn [[_ _ props]] (when (= id (:testID props)) props)) (native-nodes tree)))

(defn node-by-test-id [tree id]
  (some (fn [[_ _ props :as n]] (when (= id (:testID props)) n)) (native-nodes tree)))

(defn test-ids [tree]
  (set (keep (fn [[_ _ props]] (:testID props)) (native-nodes tree))))
