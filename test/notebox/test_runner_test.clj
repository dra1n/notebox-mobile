(ns notebox.test-runner-test
  "CLJS has no test discovery: the node runner lists its namespaces by hand.
  This keeps that list in sync with the .cljs/.cljc test files."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [clojure.tools.namespace.find :as find]))

(def runner-file "test/notebox/test_runner.cljs")

(defn- discovered []
  (->> (find/find-namespaces-in-dir (io/file "test/notebox") find/cljs)
       (filter #(re-find #"-test$" (str %)))
       set))

(def ^:private any-alias
  "Lets the JVM reader read ::alias/kw in a cljs file without loading its ns."
  (reify clojure.lang.LispReader$Resolver
    (currentNS [_] 'notebox.test-runner)
    (resolveClass [_ s] s)
    (resolveAlias [_ s] s)
    (resolveVar [_ s] s)))

(defn- runner-forms []
  (with-open [r (java.io.PushbackReader. (io/reader runner-file))]
    (binding [*reader-resolver* any-alias]
      (let [opts {:eof ::eof :read-cond :allow :features #{:cljs}}]
        (doall (take-while #(not= ::eof %) (repeatedly #(read opts r))))))))

(defn- required [forms]
  (let [[_ _ & clauses] (first forms)]
    (set (for [c clauses :when (and (seq? c) (= :require (first c)))
               spec (rest c)
               :let [lib (if (vector? spec) (first spec) spec)]
               :when (re-find #"-test$" (str lib))]
           lib))))

(defn- listed [forms]
  (->> forms
       (some #(when (and (seq? %) (= 'def (first %)) (= 'test-namespaces (second %)))
                (nth % 2)))
       second   ; (quote [...])
       set))

(defn- run-tests-args [forms]
  (->> forms
       (tree-seq coll? seq)
       (filter #(and (seq? %) (= 't/run-tests (first %))))
       first
       rest
       (map second)   ; 'ns → (quote ns)
       set))

(deftest runner-includes-every-cljs-test-namespace
  (let [forms (runner-forms)
        found (discovered)]
    (is (seq found))
    (is (= found (required forms)) "the runner's :require list")
    (is (= found (listed forms)) "test-namespaces")
    (is (= found (run-tests-args forms)) "the run-tests call")))
