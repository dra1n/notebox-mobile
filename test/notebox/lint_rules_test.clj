(ns notebox.lint-rules-test
  "The dependency rules (spec/roadmap.md §5.2) must actually bite: check-deps
  has to report every deliberate violation in test/resources/lint-violations,
  and nothing in the real source tree."
  (:require [clojure.test :refer [deftest is testing]]
            [check-deps]))

(def violations-dir "test/resources/lint-violations")

(deftest violations-are-reported
  (is (= #{"notebox.misc is not in a known layer (see check-deps/classify)"
           "cycle: notebox.domain.a → notebox.domain.b → notebox.domain.a"
           "notebox.domain.note (domain) may not require react-native (js)"
           "notebox.infra.kv-store (infra) may not require re-frame.core (re-frame)"
           "notebox.ui.screen (ui) may not require notebox.infra.kv-store (infra)"
           "notebox.feature.books.events may not require notebox.feature.library.events: across features only queries/subs namespaces are allowed"
           "notebox.feature.auth.events may not require notebox.feature.library.subs: feature 'library' is not lower than 'auth' in the feature order"
           "notebox.storage.cache may not require the JS module @react-native-async-storage/async-storage: only notebox.infra.js.*, notebox.infra.rn.*, notebox.ui.* and notebox.core may"}
         (set (check-deps/problems [violations-dir])))))

(deftest allowed-requires-are-not-reported
  (testing "a feature may use the subs of a lower feature"
    (is (nil? (check-deps/violation 'notebox.feature.books.events
                                    'notebox.feature.library.subs))))
  (testing "a feature may use its own events"
    (is (nil? (check-deps/violation 'notebox.feature.books.subs
                                    'notebox.feature.books.events))))
  (testing "ui may read a feature's subs, but not its events"
    (is (nil? (check-deps/violation 'notebox.ui.screens.home
                                    'notebox.feature.library.subs)))
    (is (some? (check-deps/violation 'notebox.ui.screens.home
                                     'notebox.feature.library.events))))
  (testing "the shell may require anything"
    (is (nil? (check-deps/violation 'notebox.core 'notebox.infra.http))))
  (testing "JS modules only in the interop namespaces"
    (is (nil? (check-deps/violation 'notebox.infra.js.luggage "@luggage/core/build/Luggage")))
    (is (nil? (check-deps/violation 'notebox.infra.rn.keychain "react-native-keychain")))
    (is (nil? (check-deps/violation 'notebox.ui.root "react-native")))
    (is (nil? (check-deps/violation 'notebox.core "react-native-get-random-values")))
    (is (some? (check-deps/violation 'notebox.dropbox.client "react-native")))
    (is (some? (check-deps/violation 'notebox.infra.http "whatwg-fetch")))))

(deftest project-source-follows-the-rules
  (is (= [] (check-deps/problems ["src" "dev"]))))
