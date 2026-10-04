(ns notebox.test-runner
  "Entry point of the node test build (test.edn). Every .cljs/.cljc test
  namespace must be listed here; notebox.test-runner-test checks that."
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [notebox.test.coverage] ; first: records events/subs for the coverage test
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
            [notebox.feature.flows-test]
            [notebox.feature.subs-test]
            [notebox.shell.events-test]
            [notebox.storage.repository-test]
            [notebox.system-test]
            [notebox.ui.components-test]
            [notebox.event-coverage-test]))

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
    notebox.feature.flows-test
    notebox.feature.subs-test
    notebox.shell.events-test
    notebox.storage.repository-test
    notebox.system-test
    notebox.ui.components-test
    notebox.event-coverage-test])

;; Expected in tests, so not printed: subscribing outside a reactive context, and
;; re-registering effects when each test starts a fresh system.
(rf/set-loggers!
 {:warn (fn [& args]
          (let [msg (str (first args))]
            (when-not (or (str/includes? msg "outside of a reactive context")
                          (str/includes? msg "overwriting"))
              (apply js/console.warn args))))})

(defmethod t/report [::t/default :end-run-tests] [m]
  (set! (.-exitCode js/process) (if (t/successful? m) 0 1)))

(defn -main [& _]
  ;; run-tests is a macro, so the namespaces are spelled out again here;
  ;; notebox.event-coverage-test must stay last
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
               'notebox.feature.flows-test
               'notebox.feature.subs-test
               'notebox.shell.events-test
               'notebox.storage.repository-test
               'notebox.system-test
               'notebox.ui.components-test
               'notebox.event-coverage-test))

(set! *main-cli-fn* -main)
