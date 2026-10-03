(ns notebox.test-runner
  "Entry point of the node test build (test.edn). Every .cljs/.cljc test
  namespace must be listed here; notebox.test-runner-test checks that."
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [notebox.domain.golden-test]
            [notebox.domain.json-test]
            [notebox.domain.model-test]
            [notebox.domain.ops-test]
            [notebox.domain.ordered-test]
            [notebox.domain.search-test]
            [notebox.dropbox.auth-test]
            [notebox.dropbox.client-test]
            [notebox.dropbox.contract-test]
            [notebox.dropbox.errors-test]
            [notebox.dropbox.fake-store-test]
            [notebox.dropbox.http-test]
            [notebox.dropbox.pkce-test]
            [notebox.shell.events-test]
            [notebox.system-test]))

(def test-namespaces
  '[notebox.domain.golden-test
    notebox.domain.json-test
    notebox.domain.model-test
    notebox.domain.ops-test
    notebox.domain.ordered-test
    notebox.domain.search-test
    notebox.dropbox.auth-test
    notebox.dropbox.client-test
    notebox.dropbox.contract-test
    notebox.dropbox.errors-test
    notebox.dropbox.fake-store-test
    notebox.dropbox.http-test
    notebox.dropbox.pkce-test
    notebox.shell.events-test
    notebox.system-test])

;; Subscribing outside a reactive context is what tests do; don't drown the output.
(rf/set-loggers!
 {:warn (fn [& args]
          (when-not (str/includes? (str (first args)) "outside of a reactive context")
            (apply js/console.warn args)))})

(defmethod t/report [::t/default :end-run-tests] [m]
  (set! (.-exitCode js/process) (if (t/successful? m) 0 1)))

(defn -main [& _]
  ;; run-tests is a macro, so the namespaces are spelled out again here
  (t/run-tests 'notebox.domain.golden-test
               'notebox.domain.json-test
               'notebox.domain.model-test
               'notebox.domain.ops-test
               'notebox.domain.ordered-test
               'notebox.domain.search-test
               'notebox.dropbox.auth-test
               'notebox.dropbox.client-test
               'notebox.dropbox.contract-test
               'notebox.dropbox.errors-test
               'notebox.dropbox.fake-store-test
               'notebox.dropbox.http-test
               'notebox.dropbox.pkce-test
               'notebox.shell.events-test
               'notebox.system-test))

(set! *main-cli-fn* -main)
