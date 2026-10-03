(ns notebox.dropbox.errors-test
  "Every status / Dropbox error maps to one :type (roadmap §6.1)."
  (:require [clojure.test :refer [deftest is testing]]
            [notebox.dropbox.errors :as errors]
            [notebox.dropbox.http-test :refer [fixture]]))

(deftest normalization-table
  (doseq [[resp expected]
          [[{:status 409 :body (fixture "error-not-found.json")}
            {:type :not-found :status 409 :summary "path/not_found/.."}]
           [{:status 409 :body (fixture "error-lookup-not-found.json")}
            {:type :not-found :status 409 :summary "path_lookup/not_found/"}]
           [{:status 409 :body (fixture "error-conflict.json")}
            {:type :conflict :status 409 :summary "path/conflict/file/..."}]
           [{:status 409 :body "{\"error_summary\":\"path/insufficient_space/..\"}"}
            {:type :other :status 409 :summary "path/insufficient_space/.."}]
           [{:status 401 :body (fixture "error-expired-token.json")}
            {:type :unauthorized :status 401 :summary "expired_access_token/"}]
           [{:status 401 :body "{\"error\":{\".tag\":\"invalid_access_token\"}}"}
            {:type :unauthorized :status 401 :summary "invalid_access_token"}]
           [{:status 400 :body (fixture "error-invalid-grant.json")}
            {:type :unauthorized :status 400 :summary "invalid_grant"}]
           [{:status 400 :body "{\"error\":\"invalid_request\"}"}
            {:type :other :status 400 :summary "invalid_request"}]
           [{:status 400 :body "Error in call to API function \"files/upload\": bad arg"}
            {:type :other :status 400 :summary "Error in call to API function \"files/upload\": bad arg"}]
           [{:status 429 :headers {"retry-after" "15"} :body (fixture "error-too-many-requests.json")}
            {:type :rate-limited :status 429 :summary "too_many_requests/.." :retry-after 15}]
           [{:status 429 :body (fixture "error-too-many-requests.json")}
            {:type :rate-limited :status 429 :summary "too_many_requests/.." :retry-after 300}]
           [{:status 503 :headers {"retry-after" "2"} :body ""}
            {:type :server :status 503 :retry-after 2}]
           [{:status 500 :body "Internal Server Error"}
            {:type :server :status 500 :summary "Internal Server Error"}]
           [{:status 404 :body nil}
            {:type :other :status 404}]]]
    (testing (pr-str (:status resp) (:body resp))
      (is (= expected (errors/from-response resp))))))

(deftest helpers
  (is (= {:type :network :summary "offline"} (errors/network "offline")))
  (is (every? errors/retryable? [{:type :rate-limited} {:type :server} {:type :network}]))
  (is (not-any? errors/retryable? [{:type :conflict} {:type :unauthorized} {:type :other}
                                   {:type :not-found}]))
  (is (= :conflict (:type (ex-data (errors/->ex {:type :conflict :summary "x"})))))
  (is (= "Dropbox: conflict (x)" (ex-message (errors/->ex {:type :conflict :summary "x"}))))
  (is (= "Dropbox: network" (ex-message (errors/->ex {:type :network}))))
  (is (= :other (errors/summary-type nil))))
