(ns notebox.test.coverage
  "Records which events and subscriptions the tests exercise (loaded first by
  the node test runner); notebox.event-coverage-test checks the record."
  (:require [re-frame.core :as rf]))

(defonce handled-events (atom #{}))
(defonce subscribed (atom #{}))

(defonce installed
  (do
    (rf/reg-global-interceptor
     (rf/->interceptor :id ::record
                       :before (fn [ctx]
                                 (swap! handled-events conj (first (get-in ctx [:coeffects :event])))
                                 ctx)))
    (let [orig rf/subscribe]
      (set! rf/subscribe (fn [q & more]
                           (swap! subscribed conj (first q))
                           (apply orig q more))))
    true))
