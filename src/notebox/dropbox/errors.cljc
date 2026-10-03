(ns notebox.dropbox.errors
  "Dropbox failures normalized to

    {:type   #{:not-found :conflict :unauthorized :rate-limited :network :server :other}
     :status http-status  :summary dropbox-error-summary  :retry-after seconds}

  so callers branch on :type only. Errors travel as ex-info with this map as
  ex-data."
  (:require [clojure.string :as str]
            [notebox.domain.json :as json]))

(defn- try-decode [body]
  (try (json/decode body) (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- parse-seconds [s]
  (when (string? s) (parse-long (str/trim s))))

(defn summary-type
  "The :type for a 409 error_summary, e.g. \"path/not_found/..\"."
  [summary]
  (let [s (str summary)]
    (cond
      (re-find #"(^|/)not_found(/|$)" s) :not-found
      (re-find #"(^|/)conflict(/|$)" s)  :conflict
      :else                              :other)))

(defn from-response
  "The normalized error for a non-2xx response {:status :headers :body}."
  [{:keys [status headers body]}]
  (let [data        (try-decode body)
        summary     (or (:error_summary data)
                        (when (string? (:error data)) (:error data))
                        (some-> (get-in data [:error (keyword ".tag")]) str))
        retry-after (or (parse-seconds (get headers "retry-after"))
                        (get-in data [:error :retry_after]))
        base        (cond-> {:status status}
                      summary (assoc :summary summary)
                      (and (not summary) (seq body)) (assoc :summary (subs body 0 (min 200 (count body))))
                      retry-after (assoc :retry-after retry-after))]
    (assoc base :type
           (cond
             (= 401 status)                     :unauthorized
             (= 409 status)                     (summary-type summary)
             (= 429 status)                     :rate-limited
             (and (= 400 status) (= "invalid_grant" summary)) :unauthorized
             (>= status 500)                    :server
             :else                              :other))))

(defn network [message]
  {:type :network :summary message})

(defn retryable?
  "Worth retrying later: rate limits, server trouble, no network."
  [err]
  (contains? #{:rate-limited :server :network} (:type err)))

(defn ->ex [err]
  (ex-info (str "Dropbox: " (name (:type err)) (when (:summary err) (str " (" (:summary err) ")")))
           err))
