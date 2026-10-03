(ns notebox.test.async
  "Helpers for Promise-based tests."
  (:require [cljs.test :refer [is]]))

(defn run
  "Finishes an async test when Promise `p` settles; an unexpected rejection
  fails the test."
  [done p]
  (-> p
      (.catch (fn [e] (is (nil? e) (str "unexpected failure: " (ex-message e) " " (pr-str (ex-data e))))))
      (.finally done)))

(defn error-type
  "Promise of the :type a rejected `p` failed with (nil if it resolved)."
  [p]
  (-> p
      (.then (constantly nil))
      (.catch (fn [e] (or (:type (ex-data e)) e)))))

(defn chain
  "Runs the functions (each returning a Promise or value) one after another."
  [& fs]
  (reduce (fn [p f] (.then p (fn [_] (f)))) (js/Promise.resolve nil) fs))
