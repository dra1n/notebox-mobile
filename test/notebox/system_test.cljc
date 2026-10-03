(ns notebox.system-test
  "The Integrant system (test profile) starts in dependency order and halts
  cleanly. Runs on the JVM and in node; the React Native root view
  (:notebox/ui) is left out on both."
  (:require [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [re-frame.core :as rf]
            [weavejester.dependency :as dep]
            [notebox.config :as config]
            [notebox.shell.app]))

(def rn-only-keys #{:notebox/ui})

(defn- test-config []
  (apply dissoc (config/config :test) rn-only-keys))

(deftest every-profile-resolves
  (doseq [p config/profiles]
    (testing (str p)
      (let [c (config/config p)]
        (is (map? c))
        (is (= p (get-in c [:notebox/app :profile])))))))

(deftest config-graph-is-acyclic
  (doseq [p config/profiles]
    (is (some? (ig/dependency-graph (config/config p))))))

(deftest init-follows-dependencies-and-halts
  (let [cfg    (test-config)
        order  (atom [])
        system (ig/build cfg (keys cfg)
                         (fn [k v] (swap! order conj k) (ig/init-key k v)))
        pos    (zipmap @order (range))
        graph  (ig/dependency-graph cfg)]
    (testing "every key is initiated, after the keys it refers to"
      (is (= (set (keys cfg)) (set @order)))
      (doseq [k (keys cfg) d (dep/immediate-dependencies graph k)]
        (is (< (pos d) (pos k)) (str d " is initiated before " k))))
    (testing "the app initialized app-db"
      (is (= :ready @(rf/subscribe [:app/status])))
      (is (= :test @(rf/subscribe [:app/profile]))))
    (testing "halt"
      (is (nil? (ig/halt! system))))))
