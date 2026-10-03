(ns notebox.system-test
  "The Integrant system (test profile) starts in dependency order and halts
  cleanly. In node: every component but the React Native root view
  (:notebox/ui). On the JVM: only the cljc components."
  (:require [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [re-frame.core :as rf]
            [weavejester.dependency :as dep]
            [notebox.config :as config]
            [notebox.infra.navigator]
            #?@(:cljs [[notebox.dropbox.auth]
                       [notebox.dropbox.client]
                       [notebox.infra.browser]
                       [notebox.infra.http]
                       [notebox.infra.js.luggage]
                       [notebox.infra.kv-store]
                       [notebox.shell.effects]
                       [notebox.infra.secure-store]
                       [notebox.storage.repository]])
            [notebox.shell.app]))

(def excluded-keys
  #?(:cljs #{:notebox/ui}
     :clj  #{:notebox/ui :notebox.infra/http :notebox.infra/secure-store :notebox.infra/browser
             :notebox.dropbox/auth :notebox.dropbox/client
             :notebox.infra.js/luggage :notebox.storage/repository :notebox.infra/kv-store
             :notebox.shell/effects}))

;; On the JVM the cljs components aren't there: the effects component becomes a
;; stub that registers no-ops for the effects :app/initialize uses.
#?(:clj
   (defmethod ig/init-key ::stub-effects [_ _]
     (doseq [id [:settings/load :auth/check-session]] (rf/reg-fx id (fn [_])))
     {}))

(defn- test-config []
  (let [cfg (apply dissoc (config/config :test) excluded-keys)]
    #?(:cljs cfg
       :clj  (-> cfg
                 (assoc ::stub-effects {})
                 (assoc-in [:notebox/app :effects] (ig/ref ::stub-effects))))))

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
