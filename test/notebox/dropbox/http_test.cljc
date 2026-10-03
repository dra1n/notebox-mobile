(ns notebox.dropbox.http-test
  "Exact requests for every endpoint, and parsing of the documented responses
  in test/resources/fixtures/http/."
  (:require [clojure.test :refer [deftest is testing]]
            [notebox.domain.json :as json]
            [notebox.dropbox.http :as h]
            [notebox.test.io :as io]))

(defn fixture [name] (io/slurp-utf8 (str "test/resources/fixtures/http/" name)))

(deftest requests
  (is (= {:method "POST" :url "https://content.dropboxapi.com/2/files/download"
          :headers {"Dropbox-API-Arg" "{\"path\":\"/notes/.meta.json\"}"}}
         (h/download-request "/notes/.meta.json")))
  (testing "upload: add without a rev, update with one"
    (is (= {:method "POST" :url "https://content.dropboxapi.com/2/files/upload"
            :headers {"Content-Type" "application/octet-stream"
                      "Dropbox-API-Arg" "{\"path\":\"/notes/b.json\",\"mode\":{\".tag\":\"add\"},\"autorename\":false,\"mute\":true}"}
            :body "[]"}
           (h/upload-request "/notes/b.json" "[]" {})))
    (is (= "{\"path\":\"/notes/b.json\",\"mode\":{\".tag\":\"update\",\"update\":\"a1c10ce0dd78\"},\"autorename\":false,\"mute\":true}"
           (get-in (h/upload-request "/notes/b.json" "[]" {:rev "a1c10ce0dd78"}) [:headers "Dropbox-API-Arg"])))
    (is (= "{\"path\":\"/notes/b.json\",\"mode\":{\".tag\":\"overwrite\"},\"autorename\":false,\"mute\":true}"
           (get-in (h/upload-request "/notes/b.json" "[]" {:mode :overwrite}) [:headers "Dropbox-API-Arg"]))))
  (is (= {:method "POST" :url "https://api.dropboxapi.com/2/files/delete_v2"
          :headers {"Content-Type" "application/json"} :body "{\"path\":\"/notes/b.json\"}"}
         (h/delete-request "/notes/b.json")))
  (is (= {:method "POST" :url "https://api.dropboxapi.com/2/files/list_folder"
          :headers {"Content-Type" "application/json"}
          :body "{\"path\":\"/notes\",\"recursive\":false,\"include_deleted\":false}"}
         (h/list-folder-request "/notes")))
  (is (= {:method "POST" :url "https://api.dropboxapi.com/2/files/list_folder/continue"
          :headers {"Content-Type" "application/json"} :body "{\"cursor\":\"c1\"}"}
         (h/list-folder-continue-request "c1")))
  (is (= {:method "POST" :url "https://api.dropboxapi.com/2/users/get_current_account"
          :headers {"Content-Type" "application/json"} :body "null"}
         (h/current-account-request)))
  (is (= "https://api.dropboxapi.com/2/auth/token/revoke" (:url (h/revoke-request)))))

(deftest api-arg-header-is-ascii
  (is (= "{\"path\":\"/notes/\\u041a\\u043d\\u0438\\u0433\\u0430 \\ud83d\\ude00.json\"}"
         (h/header-safe-json {:path "/notes/Книга 😀.json"})))
  (is (= "{\"path\":\"a\\\"b\\u007f\"}" (h/header-safe-json {:path "a\"b\u007f"}))))

(deftest oauth
  (testing "authorize URL"
    (is (= (str "https://www.dropbox.com/oauth2/authorize?client_id=2t7xyn3a902rv0z"
                "&response_type=code&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                "&code_challenge_method=S256&token_access_type=offline"
                "&redirect_uri=notebox%3A%2F%2Foauth&state=s%20t%2A")
           (h/authorize-url {:app-key "2t7xyn3a902rv0z" :redirect-uri "notebox://oauth"
                             :challenge "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                             :state "s t*"}))))
  (testing "redirects"
    (is (= {:code "abc+/="} (h/parse-redirect "notebox://oauth?code=abc%2B%2F%3D&state=S1" "S1")))
    (is (= {:error :state-mismatch} (h/parse-redirect "notebox://oauth?code=abc&state=S2" "S1")))
    (is (= {:error :no-code} (h/parse-redirect "notebox://oauth?state=S1" "S1")))
    (is (= {:error :access_denied :description "The user chose not to give your app access."}
           (h/parse-redirect (str "notebox://oauth?error=access_denied&state=S1"
                                  "&error_description=The+user+chose+not+to+give+your+app+access.")
                             "S1"))))
  (testing "token requests"
    (is (= {:method "POST" :url "https://api.dropboxapi.com/oauth2/token"
            :headers {"Content-Type" "application/x-www-form-urlencoded"}
            :body (str "grant_type=authorization_code&code=C0DE&client_id=key"
                       "&redirect_uri=notebox%3A%2F%2Foauth&code_verifier=v-1_x")}
           (h/code-exchange-request {:code "C0DE" :app-key "key" :redirect-uri "notebox://oauth"
                                     :verifier "v-1_x"})))
    (is (= "grant_type=refresh_token&refresh_token=r%2Ft&client_id=key"
           (:body (h/refresh-request {:refresh-token "r/t" :app-key "key"})))))
  (testing "token responses"
    (is (= {:access-token "sl.u.AbX9y6Fe3AuH5o66-gmJpR032jwAwQPIVVzWXZNkdzcYT02akC2de219dZi6gxYPVnYPrpvISRSf9lxKWJzYLjtMPH-d9fo_0gXex7X37VIvpty4-G8f4-WX45AcEPfRnJJDwzv-"
            :expires-at 14401000
            :refresh-token "nBiM85CZALsAAAAAAAAAAQXHBoNpNutK4ngsXHsqW4iGz9tisb3JyjGqikMJIYbd"
            :account-id "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc"}
           (h/token-result (fixture "token-code.json") 1000)))
    (is (= #{:access-token :expires-at} (set (keys (h/token-result (fixture "token-refresh.json") 0))))))
  (testing "query parsing"
    (is (= {} (h/query-params "notebox://oauth")))
    (is (= {:a "" :b "x y"} (h/query-params "notebox://oauth?a=&b=x+y#frag")))))

(deftest responses
  (is (= {:text "[]" :rev "a1c10ce0dd78"}
         (h/download-result {:status 200 :body "[]"
                             :headers {"dropbox-api-result" (fixture "download-result-header.json")}})))
  (is (= {:rev "a1c10ce0dd79"} (h/upload-result {:status 200 :body (fixture "upload.json")})))
  (is (= {:entries [{:name ".meta.json" :path "/notes/.meta.json" :rev "a1c10ce0dd70"}
                    {:name "k3J9aZ0qLx.json" :path "/notes/k3j9az0qlx.json" :rev "a1c10ce0dd78"}]
          :cursor "ZtkX9_EHj3x7PMkVuFIhwKYXEpwpLwyxp9vMKomUhllil9q7eWiAu"
          :has-more true}
         (h/list-folder-result {:status 200 :body (fixture "list-folder.json")}))
      "folders are skipped")
  (is (= {:account-id "dbid:AAH4f99T0taONIb-OurWxbNQ6ywGRopQngc" :email "franz@example.com"
          :name "Franz Ferdinand (Personal)"}
         (h/account-result {:status 200 :body (fixture "current-account.json")})))
  (testing "the fixtures are valid JSON"
    (doseq [f (io/list-dir "test/resources/fixtures/http") :when (re-find #"\.json$" f)]
      (is (some? (json/decode (fixture f))) f))))
