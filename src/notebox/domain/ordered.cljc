(ns notebox.domain.ordered
  "Map changes that keep key order, so JSON objects are written back in the order
  other clients wrote them (see notebox.domain.json).

  Clojure's `assoc` keeps order only while a map has at most 8 keys; adding a key
  to a bigger array map turns it into a hash map. These functions append new keys
  at the end instead, as JavaScript does for object properties."
  (:refer-clojure :exclude [assoc update merge]))

(defn assoc
  "`clojure.core/assoc` that appends a new key at the end, whatever the size."
  [m k v]
  (cond
    (contains? m k) (clojure.core/assoc m k v) ; replaces in place
    (< (count m) 8) (clojure.core/assoc m k v)
    :else (let [kvs (into [] cat m)]
            (apply array-map (conj kvs k v)))))

(defn update
  "`clojure.core/update` via `assoc` above."
  [m k f & args]
  (assoc m k (apply f (get m k) args)))

(defn merge
  "Like `clojure.core/merge` of two maps: `b`'s values win; keys new to `a` are
  appended in `b`'s order."
  [a b]
  (reduce-kv assoc (or a (array-map)) b))
