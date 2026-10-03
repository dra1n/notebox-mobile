(ns check-deps
  "Enforces the dependency rules of spec/roadmap.md §5.2 on namespace requires:

  - no namespace cycles (in the cljs view of .cljs/.cljc, and the clj view of .cljc);
  - each layer may require only what its row in `allowed` lists;
  - a feature may require another feature only through its `queries`/`subs`
    namespaces, and only if that feature is lower in `feature-rank`;
    `events` of another feature are never allowed;
  - JS modules (string requires) only in the interop namespaces (§5.2 item 7).

  Run: clojure -M:check-deps [dir ...]   (default: src dev)"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.parse :as parse]))

;; --- classification ---------------------------------------------------------

(def feature-rank
  "messaging, nav, sync ← auth ← library ← {editor, books, tags, search}"
  {"messaging" 0, "nav" 0, "sync" 0
   "auth" 1
   "library" 2
   "editor" 3, "books" 3, "tags" 3, "search" 3})

(defn- starts? [s & prefixes] (some #(str/starts-with? s %) prefixes))

(defn classify
  "The layer (or library group) of a namespace symbol or JS module string."
  [dep]
  (let [s (str dep)]
    (cond
      (string? dep)                                   :js
      (starts? s "notebox.domain.")                   :domain
      (starts? s "notebox.infra." "notebox.dropbox."
               "notebox.storage.")                    :infra
      (starts? s "notebox.fx.")                       :fx
      (starts? s "notebox.feature.")                  :feature
      (starts? s "notebox.ui.")                       :ui
      (or (#{"notebox.core" "notebox.config" "notebox.dev"} s)
          (starts? s "notebox.shell."))               :shell
      (starts? s "notebox.")                          :unclassified
      (starts? s "clojure." "cljs." "goog.")          :clojure
      (starts? s "re-frame." "day8.re-frame.")        :re-frame
      (starts? s "reagent.")                          :reagent
      (starts? s "integrant.")                        :integrant
      :else                                           :lib)))

(def allowed
  "Layer → the layers/library groups it may require."
  {:domain  #{:domain :clojure :lib}
   :infra   #{:domain :infra :clojure :integrant :js :lib}
   :fx      #{:domain :infra :fx :clojure :integrant :re-frame :js :lib}
   :feature #{:domain :feature :clojure :re-frame :lib}
   :ui      #{:domain :ui :feature :clojure :re-frame :reagent :integrant :js :lib}
   :shell   #{:domain :infra :fx :feature :ui :shell :clojure :re-frame :reagent
              :integrant :js :lib}})

(defn js-interop-ns?
  "May `ns-sym` require JS modules? (roadmap §5.2 item 7)"
  [ns-sym]
  (let [s (str ns-sym)]
    (or (= "notebox.core" s)
        (starts? s "notebox.infra.js." "notebox.infra.rn." "notebox.ui."))))

(defn- feature-parts
  "\"notebox.feature.library.subs\" → [\"library\" \"subs\"]"
  [ns-sym]
  (let [[_ f part] (re-matches #"notebox\.feature\.([^.]+)\.?(.*)" (str ns-sym))]
    [f part]))

;; --- rules ------------------------------------------------------------------

(defn violation
  "A violation message if `from` may not require `to`, else nil."
  [from to]
  (let [lf (classify from)
        lt (classify to)]
    (cond
      (= lf :unclassified)
      (str from " is not in a known layer (see check-deps/classify)")

      (= lt :unclassified)
      (str to " (required by " from ") is not in a known layer")

      (not (contains? (allowed lf) lt))
      (str from " (" (name lf) ") may not require " to " (" (name lt) ")")

      (and (= lt :js) (not (js-interop-ns? from)))
      (str from " may not require the JS module " to
           ": only notebox.infra.js.*, notebox.infra.rn.*, notebox.ui.* and notebox.core may")

      (and (= lt :feature) (#{:feature :ui} lf))
      (let [[ff] (when (= lf :feature) (feature-parts from))
            [tf tpart] (feature-parts to)]
        (cond
          (= ff tf) nil
          (not (#{"queries" "subs"} tpart))
          (str from " may not require " to
               ": across features only queries/subs namespaces are allowed")

          (and (= lf :feature)
               (not (< (feature-rank tf 99) (feature-rank ff -1))))
          (str from " may not require " to ": feature '" tf
               "' is not lower than '" ff "' in the feature order"))))))

;; --- graph ------------------------------------------------------------------

(defn js-deps
  "JS module strings in the ns form's :require clauses, e.g. [\"react-native\" :as rn].
  tools.namespace ignores them."
  [decl]
  (set (for [clause decl
             :when (and (seq? clause) (= :require (first clause)))
             spec  (rest clause)
             :let  [lib (if (sequential? spec) (first spec) spec)]
             :when (string? lib)]
         lib)))

(defn ns-graph
  "{ns-sym #{dep ...}} for the namespaces in `dirs` read for `platform`."
  [dirs platform]
  (->> (find/find-ns-decls (map io/file dirs) platform)
       (map (fn [decl] [(parse/name-from-ns-decl decl)
                        (into (set (parse/deps-from-ns-decl decl)) (js-deps decl))]))
       (into {})))

(defn cycles
  "Each dependency cycle among the graph's own namespaces, as a vector of syms."
  [graph]
  (let [state (atom {}) found (atom [])]
    (letfn [(visit [n path]
              (case (@state n)
                :done nil
                :active (let [cyc (drop-while #(not= % n) path)]
                          (swap! found conj (vec (concat cyc [n]))))
                (do (swap! state assoc n :active)
                    (doseq [d (graph n) :when (contains? graph d)]
                      (visit d (conj path n)))
                    (swap! state assoc n :done))))]
      (doseq [n (sort (keys graph))] (visit n []))
      @found)))

(defn problems
  "All rule violations and cycles found in `dirs`."
  [dirs]
  (let [cljs (ns-graph dirs find/cljs)
        clj  (->> (ns-graph dirs find/clj)
                  ;; .clj files (scripts, the JVM-only bits) aren't part of the app
                  (filter (fn [[n _]] (contains? cljs n)))
                  (into {}))]
    (vec (distinct
          (concat
           (for [n (sort (keys cljs)) :when (= :unclassified (classify n))]
             (str n " is not in a known layer (see check-deps/classify)"))
           (for [g [cljs clj] cyc (cycles g)]
             (str "cycle: " (str/join " → " cyc)))
           (for [g [cljs clj] [from deps] g dep deps
                 :let [v (violation from dep)] :when v]
             v))))))

(defn -main [& dirs]
  (let [dirs (or (seq dirs) ["src" "dev"])
        ps   (problems dirs)]
    (if (seq ps)
      (do (doseq [p ps] (println "✗" p))
          (println (count ps) "dependency problem(s)")
          (System/exit 1))
      (println "✓ dependency rules OK:" (str/join ", " dirs)))))
