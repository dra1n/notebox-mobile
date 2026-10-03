(ns notebox.domain.desktop-oracle-test
  "The desktop client (../notebox-desktop, src/luggage/collections.clj) reads and
  writes files with cheshire 5.10.2: `(json/parse-string s true)` and
  `(json/generate-string v)`. Its code needs JavaFX and the Dropbox SDK, so
  instead of loading it we run exactly those two calls (same library and
  version) against what mobile reads and writes."
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [notebox.domain.golden-test :as golden]
            [notebox.domain.json :as json]
            [notebox.test.gen :as g]))

(defn desktop-read [s] (cheshire/parse-string s true))
(defn desktop-write [v] (cheshire/generate-string v))

(deftest desktop-reads-what-mobile-writes
  (testing "every scenario's files"
    (doseq [name (golden/scenario-names)
            :let [{:keys [meta books]} (golden/scenario-result name)]]
      (testing name
        (is (= meta (desktop-read (json/encode meta))))
        (doseq [[slug notes] books]
          (is (= notes (desktop-read (json/encode notes))) slug))))))

(deftest mobile-reads-what-desktop-writes
  (let [{:keys [meta books]} (golden/read-folder golden/export-dir)]
    (is (= meta (json/decode (desktop-write (desktop-read (json/encode meta))))))
    (doseq [[slug notes] books]
      (is (= notes (json/decode (desktop-write (desktop-read (json/encode notes))))) slug))))

(deftest desktop-escaping-differs-only-in-hex-case
  (testing "Jackson writes \\u001B where JSON.stringify writes \\u001b; both decode the same"
    (let [s "esc \u001b bell \u0007"]
      (is (= "\"esc \\u001B bell \\u0007\"" (desktop-write s)))
      (is (= "\"esc \\u001b bell \\u0007\"" (json/encode s)))
      (is (= s (json/decode (desktop-write s)))))))

(defspec desktop-and-mobile-agree-on-generated-values 300
  (prop/for-all [x g/gen-json]
    (and (= x (desktop-read (json/encode x)))
         (= x (json/decode (desktop-write x))))))
