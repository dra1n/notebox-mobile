(ns notebox.dropbox.fake
  "An in-memory Dropbox with real rev semantics (notebox.dropbox.fake-store),
  for tests and the :e2e profile. Passes the same contract tests as the HTTP
  client. Seed it with {path data}."
  (:require [notebox.domain.json :as json]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.errors :as errors]
            [notebox.dropbox.fake-store :as store]))

(defn- fail [summary]
  (js/Promise.reject (errors/->ex {:type (errors/summary-type summary)
                                   :status 409
                                   :summary summary})))

(defn- apply-store! [store-atom f & args]
  (let [[st' result] (apply f @store-atom args)]
    (reset! store-atom st')
    result))

(defrecord FakeDropbox [store]
  api/DropboxApi
  (download [_ path]
    (let [{:keys [ok error]} (store/download @store path)]
      (if error
        (if (= :not-found (errors/summary-type error))
          (js/Promise.resolve {:data nil :rev nil})
          (fail error))
        (js/Promise.resolve {:data (json/decode (:text ok)) :rev (:rev ok)}))))

  (upload [_ path data {:keys [rev]}]
    (let [{:keys [ok error]} (apply-store! store store/upload path (json/encode data) rev)]
      (if error (fail error) (js/Promise.resolve {:rev (:rev ok)}))))

  (delete [_ path]
    (apply-store! store store/delete path)
    (js/Promise.resolve nil))

  (list-folder [_ path]
    (let [{:keys [ok]} (store/list-folder @store path)]
      (js/Promise.resolve (or ok []))))

  (current-account [_]
    (let [{:keys [account_id email name]} (:ok (store/account @store))]
      (js/Promise.resolve {:account-id account_id :email email :name (:display_name name)}))))

(defn seeded-store
  "A store holding `files` {path data}, written as JSON."
  [files]
  (reduce (fn [st [path data]] (first (store/upload st path (json/encode data) nil)))
          store/empty-store
          files))

(defn fake
  ([] (fake {}))
  ([files] (->FakeDropbox (atom (seeded-store files)))))
