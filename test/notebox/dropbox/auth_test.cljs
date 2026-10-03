(ns notebox.dropbox.auth-test
  "Sign-in (code + PKCE), refresh, sign-out (roadmap §7)."
  (:require [cljs.test :refer [deftest is async]]
            [notebox.dropbox.auth :as auth]
            [notebox.dropbox.fake-server :as server]
            [notebox.dropbox.http :as h]
            [notebox.dropbox.pkce :as pkce]
            [notebox.infra.browser :as browser]
            [notebox.infra.http :as http]
            [notebox.infra.secure-store :as secure-store]
            [notebox.test.async :as a]))

(defn- setup
  ([] (setup {}))
  ([{:keys [respond stored]}]
   (let [now  (atom 1000000)
         srv  (server/server {:now-fn #(deref now)})
         store (secure-store/memory-store (or stored {}))
         br   (browser/fake-browser (or respond #(server/approve srv %)))
         logs (atom [])
         au   (auth/auth {:config {:app-key "2t7xyn3a902rv0z" :redirect-uri "notebox://oauth"}
                          :http (http/fetch-http {:fetch-fn (server/fetch-fn srv)})
                          :secure-store store :browser br :now-fn #(deref now)
                          :log (fn [& args] (swap! logs conj (vec args)))})]
     {:now now :srv srv :store store :browser br :auth au :logs logs})))

(defn- token-requests [srv]
  (filter #(re-find #"oauth2/token" (:url %)) (server/requests srv)))

(deftest sign-in-with-pkce
  (async done
    (let [{:keys [srv store browser auth]} (setup)]
      (a/run done
        (a/chain
         #(.then (auth/signed-in? auth) (fn [s] (is (false? s))))
         #(.then (auth/login! auth) (fn [r] (is (= {:account-id "dbid:fake-account"} r))))
         (fn []
           (let [url    (first @(:opened browser))
                 params (h/query-params url)
                 body   (h/query-params (str "?" (:body (first (token-requests srv)))))]
             (is (re-find #"^https://www\.dropbox\.com/oauth2/authorize\?" url))
             (is (= {:client_id "2t7xyn3a902rv0z" :response_type "code" :code_challenge_method "S256"
                     :token_access_type "offline" :redirect_uri "notebox://oauth"}
                    (select-keys params [:client_id :response_type :code_challenge_method
                                         :token_access_type :redirect_uri])))
             (is (= (:code_challenge params) (pkce/challenge (:code_verifier body)))
                 "the verifier sent matches the challenge shown")
             (is (= "authorization_code" (:grant_type body)))))
         #(.then (secure-store/read-secret store auth/refresh-token-key)
                 (fn [t] (is (re-matches #"refresh-\d+" t) "the refresh token is stored")))
         #(.then (auth/signed-in? auth) (fn [s] (is (true? s))))
         #(.then (auth/token! auth) (fn [t] (is (re-matches #"access-\d+" t))))
         #(is (= 1 (count (token-requests srv))) "the fresh access token is reused"))))))

(deftest declined-or-forged-redirects-fail
  (async done
    (let [declined (setup {:respond (fn [_] "notebox://oauth?error=access_denied&state=x")})
          forged   (setup {:respond (fn [_] "notebox://oauth?code=stolen&state=not-ours")})]
      (a/run done
        (a/chain
         #(.then (a/error-type (auth/login! (:auth declined))) (fn [t] (is (= :unauthorized t))))
         #(.then (a/error-type (auth/login! (:auth forged))) (fn [t] (is (= :unauthorized t))))
         #(is (empty? (token-requests (:srv forged))) "a state mismatch never exchanges the code")
         #(.then (auth/signed-in? (:auth forged)) (fn [s] (is (false? s)))))))))

(deftest refresh-near-expiry-single-flight
  (async done
    (let [{:keys [now srv auth logs]} (setup)]
      (a/run done
        (a/chain
         #(auth/login! auth)
         #(swap! now + (* 4 3600 1000) (- (* 4 60 1000)))      ; 4 minutes before expiry
         #(js/Promise.all #js [(auth/token! auth) (auth/token! auth) (auth/token! auth)])
         (fn []
           (is (= 2 (count (token-requests srv))) "three callers, one refresh")
           (is (= "refresh_token" (:grant_type (h/query-params (str "?" (:body (last (token-requests srv))))))))
           (is (some #(= [:info "refreshing the Dropbox access token"] %) @logs)))
         #(auth/expire! auth)
         #(auth/token! auth)
         #(is (= 3 (count (token-requests srv))) "expire! forces the next refresh"))))))

(deftest a-rejected-refresh-token-signs-out
  (async done
    (let [{:keys [store auth]} (setup {:stored {auth/refresh-token-key "revoked"}})]
      (a/run done
        (a/chain
         #(.then (a/error-type (auth/token! auth)) (fn [t] (is (= :unauthorized t))))
         #(.then (secure-store/read-secret store auth/refresh-token-key)
                 (fn [t] (is (nil? t) "the dead token is removed")))
         #(.then (a/error-type (auth/token! auth))
                 (fn [t] (is (= :unauthorized t) "not signed in"))))))))

(deftest sign-out-revokes-and-forgets
  (async done
    (let [{:keys [srv store auth]} (setup)]
      (a/run done
        (a/chain
         #(auth/login! auth)
         #(auth/token! auth)
         #(auth/logout! auth)
         (fn []
           (let [revoke (last (server/requests srv))]
             (is (= "https://api.dropboxapi.com/2/auth/token/revoke" (:url revoke)))
             (is (re-matches #"Bearer access-\d+" (get-in revoke [:headers "Authorization"])))))
         #(.then (secure-store/read-secret store auth/refresh-token-key) (fn [t] (is (nil? t))))
         #(is (= {} @(:state auth)))
         #(.then (auth/logout! auth) (fn [r] (is (nil? r) "signing out twice is fine"))))))))

(deftest sign-out-survives-a-failed-revoke
  (async done
    (let [{:keys [srv store auth]} (setup)]
      (a/run done
        (a/chain
         #(auth/login! auth)
         #(server/script! srv {:status 500 :body "down"})
         #(swap! (:state auth) assoc :access-token "x")
         #(auth/logout! auth)
         #(.then (secure-store/read-secret store auth/refresh-token-key) (fn [t] (is (nil? t)))))))))
