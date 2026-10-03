(ns notebox.dropbox.fake
  "An in-memory Dropbox with real rev semantics (notebox.dropbox.fake-store),
  for tests and the :e2e profile. Passes the same contract tests as the HTTP
  client. Seed it with {path data}. `fail-next!` makes upcoming calls fail, to
  test error handling (and to show error states in e2e runs)."
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

(defn fail-next!
  "The next call whose op (:download :upload :delete :list-folder
  :current-account) is in `ops` and whose path matches `re` (any, if nil)
  rejects with `error` (a notebox.dropbox.errors map)."
  [dbx {:keys [ops re error]}]
  (swap! (:failures dbx) conj {:ops (set ops) :re re :error error}))

(defn- injected-failure [{:keys [failures]} op path]
  (let [i (first (keep-indexed (fn [i {:keys [ops re]}]
                                 (when (and (contains? ops op)
                                            (or (nil? re) (and path (re-find re path))))
                                   i))
                               @failures))]
    (when i
      (let [f (nth @failures i)]
        (swap! failures #(into (subvec % 0 i) (subvec % (inc i))))
        (js/Promise.reject (errors/->ex (:error f)))))))

(defrecord FakeDropbox [store failures]
  api/DropboxApi
  (download [this path]
    (or (injected-failure this :download path)
        (let [{:keys [ok error]} (store/download @store path)]
          (if error
            (if (= :not-found (errors/summary-type error))
              (js/Promise.resolve {:data nil :rev nil})
              (fail error))
            (js/Promise.resolve {:data (json/decode (:text ok)) :rev (:rev ok)})))))

  (upload [this path data {:keys [rev mode]}]
    (or (injected-failure this :upload path)
        (let [{:keys [ok error]} (apply-store! store store/upload path (json/encode data)
                                               (or rev (if (= :overwrite mode) :overwrite :add)))]
          (if error (fail error) (js/Promise.resolve {:rev (:rev ok)})))))

  (delete [this path]
    (or (injected-failure this :delete path)
        (do (apply-store! store store/delete path)
            (js/Promise.resolve nil))))

  (list-folder [this path]
    (or (injected-failure this :list-folder path)
        (let [{:keys [ok]} (store/list-folder @store path)]
          (js/Promise.resolve (or ok [])))))

  (current-account [this]
    (or (injected-failure this :current-account nil)
        (let [{:keys [account_id email name]} (:ok (store/account @store))]
          (js/Promise.resolve {:account-id account_id :email email
                               :name (:display_name name)})))))

(defn seeded-store
  "A store holding `files` {path data}, written as JSON."
  [files]
  (reduce (fn [st [path data]] (first (store/upload st path (json/encode data) :add)))
          store/empty-store
          files))

(defn fake
  ([] (fake {}))
  ([files] (->FakeDropbox (atom (seeded-store files)) (atom []))))
