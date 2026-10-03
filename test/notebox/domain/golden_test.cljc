(ns notebox.domain.golden-test
  "Byte compatibility with the web client, on the fixtures from
  test/resources/fixtures/generate.mjs (JSON.stringify output):

  - every file decodes and encodes back to the identical bytes;
  - every scenario's ops, applied by notebox.domain.ops, produce exactly the files
    (and warnings) that the independent JS implementation produced."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [notebox.domain.json :as json]
            [notebox.domain.ops :as ops]
            [notebox.domain.schema :as schema]
            [notebox.test.io :as io]))

(def root "test/resources/fixtures")
(def export-dir (str root "/notes-export"))
(def scenarios-dir (str root "/scenarios"))

(defn- book-files [dir]
  (filter #(and (str/ends-with? % ".json") (not= % ".meta.json")) (io/list-dir dir)))

(defn- slug-of [file] (subs file 0 (- (count file) (count ".json"))))

(defn read-folder
  "{:meta m :books {slug notes}} of a /notes folder."
  [dir]
  {:meta  (json/decode (io/slurp-utf8 (str dir "/.meta.json")))
   :books (into {} (for [f (book-files dir)]
                     [(slug-of f) (json/decode (io/slurp-utf8 (str dir "/" f)))]))})

(deftest every-file-round-trips-byte-for-byte
  (let [files (cons ".meta.json" (book-files export-dir))]
    (is (= 11 (count files)))
    (doseq [f files
            :let [text (io/slurp-utf8 (str export-dir "/" f))]]
      (testing f
        (is (= text (json/encode (json/decode text))))))))

(deftest the-export-passes-the-schema-checks
  (let [{:keys [meta books]} (read-folder export-dir)]
    (is (= [] (schema/meta-problems meta)))
    (doseq [[slug notes] books]
      (is (= [] (schema/book-problems notes)) slug))))

(defn- op-from-json [o]
  (update o :op keyword))

(defn- warning-from-json [w]
  (update w :type keyword))

(defn run-ops
  "Applies `ops` in order to a folder state, as the sync engine will: each op
  gets the meta and its book's notes, and its results replace them."
  [state ops]
  (reduce (fn [{:keys [meta books warnings]} op]
            (let [r (ops/apply-op op {:meta meta :notes (get books (:book op))})]
              {:meta     (:meta r)
               :books    (case (ops/book-file op)
                           (:write :create) (assoc books (:book op) (:notes r))
                           :delete (dissoc books (:book op))
                           nil books)
               :warnings (into (or warnings []) (:warnings r))}))
          state
          ops))

(defn scenario-names [] (io/list-dir scenarios-dir))

(defn- scenario [name]
  (json/decode (io/slurp-utf8 (str scenarios-dir "/" name "/scenario.json"))))

(defn scenario-result
  "The folder state after running scenario `name`'s ops on the export."
  [name]
  (run-ops (read-folder export-dir) (map op-from-json (:ops (scenario name)))))

(deftest scenarios-match-the-js-reference
  (let [names (scenario-names)]
    (is (= 12 (count names)))
    (doseq [name names
            :let [scenario (scenario name)
                  result   (scenario-result name)
                  expected (str scenarios-dir "/" name "/expected")]]
      (testing name
        (is (= (mapv warning-from-json (:warnings scenario)) (:warnings result))
            "warnings")
        (is (= (io/slurp-utf8 (str expected "/.meta.json")) (json/encode (:meta result)))
            ".meta.json bytes")
        (is (= (set (map slug-of (book-files expected))) (set (keys (:books result))))
            "the set of book files")
        (doseq [f (book-files expected)]
          (is (= (io/slurp-utf8 (str expected "/" f))
                 (json/encode (get-in result [:books (slug-of f)])))
              (str f " bytes")))))))
