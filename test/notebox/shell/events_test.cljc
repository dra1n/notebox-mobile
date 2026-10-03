(ns notebox.shell.events-test
  (:require [clojure.test :refer [deftest is]]
            [day8.re-frame.test :as rf-test]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [notebox.shell.events :as events]))

(deftest initialize-sets-up-app-db
  (rf-test/run-test-sync
   (rf/dispatch [:app/initialize :e2e])
   (is (= :ready @(rf/subscribe [:app/status])))
   (is (= :e2e @(rf/subscribe [:app/profile])))))

(deftest initialize-replaces-previous-state
  (rf-test/run-test-sync
   (rf/dispatch [:app/initialize :dev])
   (rf/dispatch [:app/initialize :test])
   (is (= (events/initial-db :test) @app-db))))
