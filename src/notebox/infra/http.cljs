(ns notebox.infra.http
  "HTTP over `fetch`, as data: request {:method :url :headers :body} →
  Promise of {:status :headers :body}, header names lower-cased, body as text.
  A failed connection or a timeout rejects with ex-data {:type :network}."
  (:require [integrant.core :as ig]
            [notebox.dropbox.errors :as errors]))

(defprotocol Http
  (request [this req] "Promise of the response to `req`."))

(defn- headers->map [^js headers]
  (let [m (volatile! {})]
    (.forEach headers (fn [v k] (vswap! m assoc (.toLowerCase k) v)))
    @m))

(defrecord FetchHttp [fetch-fn timeout-ms]
  Http
  (request [_ {:keys [method url headers body]}]
    (let [ctrl  (js/AbortController.)
          timer (js/setTimeout #(.abort ctrl) timeout-ms)
          opts  (cond-> #js {:method (or method "GET")
                             :headers (clj->js (or headers {}))
                             :signal (.-signal ctrl)}
                  (some? body) (doto (unchecked-set "body" body)))]
      (-> (fetch-fn url opts)
          (.then (fn [^js resp]
                   (.then (.text resp)
                          (fn [text]
                            {:status  (.-status resp)
                             :headers (headers->map (.-headers resp))
                             :body    text}))))
          (.catch (fn [e]
                    (throw (if (ex-data e)
                             e
                             (errors/->ex (errors/network (str (.-message e) " " url)))))))
          (.finally #(js/clearTimeout timer))))))

(defn fetch-http
  ([] (fetch-http {}))
  ([{:keys [fetch-fn timeout-ms] :or {timeout-ms 20000}}]
   (->FetchHttp (or fetch-fn (fn [url opts] (js/fetch url opts))) timeout-ms)))

(defmethod ig/init-key :notebox.infra/http [_ opts]
  (fetch-http opts))
