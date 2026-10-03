(ns notebox.dropbox.contract-test
  "One contract, two implementations: the HTTP client (against the fake HTTP
  server) and the in-memory fake must behave the same, so later phases can test
  against the fake and trust the result."
  (:require [cljs.test :refer [deftest is async testing]]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.auth :as auth]
            [notebox.dropbox.client :as client]
            [notebox.dropbox.fake :as fake]
            [notebox.dropbox.fake-server :as server]
            [notebox.infra.browser :as browser]
            [notebox.infra.http :as http]
            [notebox.infra.secure-store :as secure-store]
            [notebox.test.async :as a]))

(def meta-path "/notes/.meta.json")
(def book-path "/notes/k3J9aZ0qLx.json")
(def book [(array-map :slug "n1" :title "Новое поколение 😀" :text "a\nb" :tags ["классика"])])

(defn contract
  "Promise running the contract against `dbx`."
  [dbx]
  (let [revs (atom {})]
    (a/chain
     #(.then (api/download dbx book-path)
             (fn [r] (is (= {:data nil :rev nil} r) "a missing file")))
     #(.then (api/list-folder dbx "/notes")
             (fn [r] (is (= [] r) "a missing folder")))
     #(.then (api/upload dbx book-path book {})
             (fn [{:keys [rev]}] (is (string? rev)) (swap! revs assoc :r1 rev)))
     #(.then (api/download dbx book-path)
             (fn [r] (is (= {:data book :rev (:r1 @revs)} r) "Unicode content round-trips")))
     #(.then (api/download dbx "/NOTES/K3J9AZ0QLX.json")
             (fn [r] (is (= book (:data r)) "paths are case-insensitive")))
     #(.then (a/error-type (api/upload dbx book-path [] {}))
             (fn [t] (is (= :conflict t) "add never overwrites")))
     #(.then (api/upload dbx book-path book {})
             (fn [r] (is (= {:rev (:r1 @revs)} r) "identical content is not a conflict")))
     #(.then (api/upload dbx book-path [] {:rev (:r1 @revs)})
             (fn [{:keys [rev]}] (is (not= (:r1 @revs) rev)) (swap! revs assoc :r2 rev)))
     #(.then (a/error-type (api/upload dbx book-path book {:rev (:r1 @revs)}))
             (fn [t] (is (= :conflict t) "a stale rev loses")))
     #(.then (api/download dbx book-path)
             (fn [r] (is (= {:data [] :rev (:r2 @revs)} r) "the conflict changed nothing")))
     #(.then (api/upload dbx book-path book {:mode :overwrite})
             (fn [{:keys [rev]}]
               (is (not= (:r2 @revs) rev) "overwrite needs no rev")
               (swap! revs assoc :r2 rev)))
     #(.then (api/download dbx book-path)
             (fn [r] (is (= book (:data r)))))
     #(.then (api/upload dbx "/notes/fresh.json" [] {:mode :overwrite})
             (fn [{:keys [rev]}] (is (string? rev) "overwrite also creates")))
     #(api/delete dbx "/notes/fresh.json")
     #(api/upload dbx meta-path {:notesInfo []} {})
     #(api/upload dbx "/notes/b.json" [] {})
     #(.then (api/list-folder dbx "/notes")
             (fn [entries]
               (is (= [".meta.json" "b.json" "k3J9aZ0qLx.json"] (sort (map :name entries))))
               (is (= (:r2 @revs) (:rev (first (filter (fn [e] (= "k3J9aZ0qLx.json" (:name e))) entries)))))
               (is (every? :path entries))))
     #(.then (api/delete dbx "/notes/b.json") (fn [r] (is (nil? r))))
     #(.then (api/delete dbx "/notes/b.json") (fn [r] (is (nil? r) "deleting twice is fine")))
     #(.then (api/download dbx "/notes/b.json") (fn [r] (is (nil? (:data r)))))
     #(.then (api/current-account dbx)
             (fn [acct] (is (= #{:account-id :email :name} (set (keys acct)))))))))

(defn signed-in-client
  "A client over a fake server, already signed in. Returns [client server auth]."
  []
  (let [srv  (server/server)
        h    (http/fetch-http {:fetch-fn (server/fetch-fn srv)})
        tok  (server/issue-tokens! srv)
        au   (auth/auth {:config {:app-key "key" :redirect-uri "notebox://oauth"}
                         :http h
                         :secure-store (secure-store/memory-store
                                        {auth/refresh-token-key (:refresh tok)})
                         :browser (browser/fake-browser #(server/approve srv %))
                         :log (fn [& _])})]
    [(client/client {:http h :auth au :sleep-fn (fn [_] (js/Promise.resolve nil))}) srv au]))

(deftest the-fake-keeps-the-contract
  (async done (a/run done (contract (fake/fake)))))

(deftest the-http-client-keeps-the-contract
  (async done
    (let [[c srv] (signed-in-client)]
      (a/run done (.then (contract c)
                         (fn [_]
                           (testing "list_folder paging was exercised"
                             (is (some #(re-find #"list_folder/continue" (:url %))
                                       (server/requests srv))))))))))

(deftest a-seeded-fake
  (async done
    (a/run done
      (.then (api/download (fake/fake {meta-path {:notesInfo [{:slug "a"}]}}) meta-path)
             (fn [r] (is (= {:notesInfo [{:slug "a"}]} (:data r))))))))
