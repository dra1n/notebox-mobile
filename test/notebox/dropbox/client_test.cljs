(ns notebox.dropbox.client-test
  "What the client sends, and how it handles 401, 429/503 and failures."
  (:require [cljs.test :refer [deftest is async]]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.auth :as auth]
            [notebox.dropbox.client :as client]
            [notebox.dropbox.contract-test :refer [signed-in-client]]
            [notebox.dropbox.fake-server :as server]
            [notebox.infra.http :as http]
            [notebox.infra.secure-store :as secure-store]
            [notebox.test.async :as a]))

(deftest sends-the-exact-requests-with-a-bearer-token
  (async done
    (let [[c srv] (signed-in-client)]
      (a/run done
        (a/chain
         #(api/upload c "/notes/Книга.json" [1] {})
         #(api/download c "/notes/Книга.json")
         (fn []
           (let [[up down] (filter #(re-find #"/2/files/" (:url %)) (server/requests srv))]
             (is (= "https://api.dropboxapi.com/oauth2/token" (:url (first (server/requests srv))))
                 "the first call refreshed the access token")
             (is (= {:method "POST" :url "https://content.dropboxapi.com/2/files/upload"
                     :body "[1]"
                     :headers {"Content-Type" "application/octet-stream"
                               "Dropbox-API-Arg" "{\"path\":\"/notes/\\u041a\\u043d\\u0438\\u0433\\u0430.json\",\"mode\":{\".tag\":\"add\"},\"autorename\":false,\"mute\":true}"
                               "Authorization" (get-in up [:headers "Authorization"])}}
                    up))
             (is (re-matches #"Bearer access-\d+" (get-in up [:headers "Authorization"])))
             (is (= "https://content.dropboxapi.com/2/files/download" (:url down))))))))))

(deftest a-401-refreshes-once-and-retries
  (async done
    (let [[c srv au] (signed-in-client)]
      (a/run done
        (a/chain
         #(api/current-account c)
         #(swap! (:state au) assoc :access-token "revoked-elsewhere")   ; still "valid" locally
         #(api/current-account c)
         (fn []
           (let [urls (map :url (server/requests srv))]
             (is (= ["https://api.dropboxapi.com/oauth2/token"
                     "https://api.dropboxapi.com/2/users/get_current_account"
                     "https://api.dropboxapi.com/2/users/get_current_account"   ; 401
                     "https://api.dropboxapi.com/oauth2/token"                 ; one refresh
                     "https://api.dropboxapi.com/2/users/get_current_account"] ; one retry
                    urls))))
         ;; A second 401 right after the refresh is final.
         #(server/script! srv {:match #"get_current_account" :status 401
                               :body "{\"error_summary\":\"invalid_access_token/\"}"}
                          {:match #"get_current_account" :status 401
                           :body "{\"error_summary\":\"invalid_access_token/\"}"})
         #(.then (a/error-type (api/current-account c))
                 (fn [t] (is (= :unauthorized t))))
         #(is (= "https://api.dropboxapi.com/oauth2/token" (:url (nth (server/requests srv) 6)))
              "the refresh in between succeeded: it's the retry's 401 that is final"))))))

(deftest rate-limits-are-retried-after-retry-after
  (async done
    (let [srv    (server/server)
          tok    (server/issue-tokens! srv)
          slept  (atom [])
          h      (http/fetch-http {:fetch-fn (server/fetch-fn srv)})
          au     (auth/auth {:config {:app-key "k" :redirect-uri "notebox://oauth"} :http h
                             :secure-store (secure-store/memory-store
                                            {auth/refresh-token-key (:refresh tok)})
                             :log (fn [& _])})
          c      (client/client {:http h :auth au
                                 :sleep-fn (fn [ms] (swap! slept conj ms) (js/Promise.resolve nil))})]
      (a/run done
        (a/chain
         #(auth/token! au)
         #(server/script! srv {:status 429 :headers {"retry-after" "7"} :body ""}
                          {:status 503 :headers {} :body ""})
         #(.then (api/current-account c) (fn [acct] (is (:email acct))))
         #(is (= [7000 1000] @slept) "Retry-After, else 1 s")
         #(server/script! srv {:status 429 :headers {"retry-after" "999"} :body ""}
                          {:status 429 :headers {} :body ""}
                          {:status 429 :headers {} :body ""})
         #(.then (a/error-type (api/current-account c))
                 (fn [t] (is (= :rate-limited t) "gives up after 3 attempts")))
         #(is (= 60000 (nth @slept 2)) "waits at most 60 s")
         #(server/script! srv {:status 500 :body "boom"})
         #(.then (a/error-type (api/current-account c))
                 (fn [t] (is (= :server t) "a 500 isn't retried here; the sync engine decides"))))))))

(deftest failures
  (async done
    (let [[c srv] (signed-in-client)
          offline (client/client {:http (http/fetch-http {:fetch-fn (fn [_ _] (js/Promise.reject (js/Error. "offline")))})
                                  :auth (:auth c)})]
      (a/run done
        (a/chain
         #(.then (a/error-type (api/current-account offline))
                 (fn [t] (is (= :network t))))
         #(server/script! srv {:status 200 :headers {"dropbox-api-result" "{\"rev\":\"1\"}"} :body "{not json"})
         #(.then (a/error-type (api/download c "/notes/x.json"))
                 (fn [t] (is (= :other t) "a file that isn't JSON")))
         #(server/script! srv {:status 409 :body "{\"error_summary\":\"path/conflict/folder/\"}"})
         #(.then (a/error-type (api/delete c "/notes/x.json"))
                 (fn [t] (is (= :conflict t) "only not-found is swallowed by delete")))
         #(server/script! srv {:status 400 :body "bad"})
         #(.then (a/error-type (api/list-folder c "/notes"))
                 (fn [t] (is (= :other t)))))))))

(deftest a-request-that-hangs-times-out
  (async done
    (let [h (http/fetch-http {:timeout-ms 20
                              :fetch-fn (fn [_ ^js opts]
                                          (js/Promise.
                                           (fn [_ reject]
                                             (.addEventListener (.-signal opts) "abort"
                                                                #(reject (js/Error. "aborted"))))))})]
      (a/run done
        (.then (a/error-type (http/request h {:url "https://example.com"}))
               (fn [t] (is (= :network t))))))))
