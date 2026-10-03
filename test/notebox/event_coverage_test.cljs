(ns notebox.event-coverage-test
  "Every registered event was handled and every registered subscription was
  subscribed to, somewhere in the test run (roadmap Phase 4 gate). The runner
  runs this namespace last."
  (:require [cljs.test :refer [deftest is]]
            [clojure.set :as set]
            [re-frame.registrar :as registrar]
            [notebox.shell.app]
            [notebox.test.coverage :as coverage]))

(defn- registered [kind] (set (keys (get @registrar/kind->id->handler kind))))

(deftest every-event-was-handled
  (is (seq @coverage/handled-events))
  (is (= #{} (set/difference (registered :event) @coverage/handled-events))
      "events no test dispatched"))

(deftest every-subscription-was-used
  (is (= #{} (set/difference (registered :sub) @coverage/subscribed))
      "subscriptions no test used"))
