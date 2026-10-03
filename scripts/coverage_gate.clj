(ns coverage-gate
  "Per-namespace coverage minimums from the phase gates (spec/roadmap.md §9).
  Cloverage's own --fail-threshold only checks the total; this checks every
  namespace matching a prefix. Wired in tests.edn as :custom-report."
  (:require [cloverage.report :as report]
            [clojure.string :as str]))

(def minimums
  "Namespace prefix → minimum % of forms covered."
  {"notebox.domain." 95.0})

(defn- pct [covered total] (if (zero? total) 100.0 (* 100.0 (/ covered total))))

(defn failures
  "[{:ns n :forms% p :minimum m}] for namespaces below their minimum."
  [forms]
  (for [{:keys [lib forms covered-forms]} (report/file-stats forms)
        :let [ns-name (str lib)
              minimum (some (fn [[prefix m]] (when (str/starts-with? ns-name prefix) m))
                            minimums)
              p (pct covered-forms forms)]
        :when (and minimum (< p minimum))]
    {:ns ns-name :forms% p :minimum minimum}))

(defn report
  "Cloverage custom report: prints the gate result; exits 3 if it fails."
  [{:keys [forms]}]
  (let [fs (failures forms)]
    (if (seq fs)
      (do (doseq [{:keys [ns forms% minimum]} fs]
            (println (format "✗ coverage: %s %.2f%% of forms < %.1f%%" ns forms% minimum)))
          (flush)
          (System/exit 3))
      (println "✓ coverage gate:"
               (str/join ", " (map (fn [[p m]] (str p "* ≥ " m "%")) minimums))))))
