(ns notebox.dropbox.client
  "The Dropbox API over HTTP (spec/roadmap.md §6.1). Each call gets an access
  token from the auth component; a 401 forces one refresh and one retry; 429
  and 503 are retried after Retry-After (at most `max-attempts` in all)."
  (:require [integrant.core :as ig]
            [notebox.domain.json :as json]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.auth :as auth]
            [notebox.dropbox.errors :as errors]
            [notebox.dropbox.fake :as fake]
            [notebox.dropbox.http :as dhttp]
            [notebox.infra.http :as http]))

(def max-retry-after-s 60)

(defn- sleep [ms] (js/Promise. (fn [resolve] (js/setTimeout resolve ms))))

(defn- send!
  "Promise of the 2xx response to `req`, or rejects with the normalized error."
  [{:keys [http auth sleep-fn max-attempts]} req]
  (letfn [(attempt [n refreshed?]
            (-> (if refreshed? (auth/refresh! auth) (auth/token! auth))
                (.then (fn [token]
                         (http/request http (assoc-in req [:headers "Authorization"]
                                                      (str "Bearer " token)))))
                (.then (fn [resp]
                         (if (<= 200 (:status resp) 299)
                           resp
                           (let [err (errors/from-response resp)]
                             (cond
                               (and (= :unauthorized (:type err)) (not refreshed?))
                               (attempt n true)

                               (and (#{429 503} (:status resp)) (< n max-attempts))
                               (.then (sleep-fn (* 1000 (min max-retry-after-s
                                                             (or (:retry-after err) 1))))
                                      #(attempt (inc n) refreshed?))

                               :else (throw (errors/->ex err)))))))))]
    (attempt 1 false)))

(defn- not-found? [e] (= :not-found (:type (ex-data e))))

(defrecord Client [http auth sleep-fn max-attempts]
  api/DropboxApi
  (download [this path]
    (-> (send! this (dhttp/download-request path))
        (.then (fn [resp]
                 (let [{:keys [text rev]} (dhttp/download-result resp)]
                   {:data (try (json/decode text)
                               (catch :default _
                                 (throw (errors/->ex {:type :other
                                                      :summary (str "not valid JSON: " path)}))))
                    :rev  rev})))
        (.catch (fn [e] (if (not-found? e) {:data nil :rev nil} (throw e))))))

  (upload [this path data {:keys [rev]}]
    (.then (send! this (dhttp/upload-request path (json/encode data) rev))
           dhttp/upload-result))

  (delete [this path]
    (-> (send! this (dhttp/delete-request path))
        (.then (constantly nil))
        (.catch (fn [e] (if (not-found? e) nil (throw e))))))

  (list-folder [this path]
    (letfn [(page [req acc]
              (.then (send! this req)
                     (fn [resp]
                       (let [{:keys [entries cursor has-more]} (dhttp/list-folder-result resp)
                             acc (into acc entries)]
                         (if has-more
                           (page (dhttp/list-folder-continue-request cursor) acc)
                           acc)))))]
      (-> (page (dhttp/list-folder-request path) [])
          (.catch (fn [e] (if (not-found? e) [] (throw e)))))))

  (current-account [this]
    (.then (send! this (dhttp/current-account-request)) dhttp/account-result)))

(defn client [{:keys [http auth sleep-fn max-attempts]}]
  (->Client http auth (or sleep-fn sleep) (or max-attempts 3)))

(defmethod ig/init-key :notebox.dropbox/client [_ {:keys [impl seed] :as opts}]
  (case impl
    :http (client opts)
    :fake (fake/fake seed)))
