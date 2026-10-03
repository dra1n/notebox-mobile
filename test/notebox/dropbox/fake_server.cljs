(ns notebox.dropbox.fake-server
  "A fake Dropbox HTTP server for the client and auth tests: a `fetch` function
  that answers the real endpoints from notebox.dropbox.fake-store, checks
  bearer tokens and their expiry, runs the OAuth code flow with PKCE, and
  records every request. Scripted responses (`script!`) are returned first,
  e.g. a 429 to test retries."
  (:require [clojure.string :as str]
            [notebox.domain.json :as json]
            [notebox.dropbox.fake-store :as fs]
            [notebox.dropbox.http :as h]
            [notebox.dropbox.pkce :as pkce]))

(defn server
  "A server at time `(now-fn)`; access tokens live `token-ttl-ms`."
  ([] (server {}))
  ([{:keys [now-fn token-ttl-ms page-size]
     :or {token-ttl-ms (* 4 3600 1000) page-size 2}}]
   {:now-fn       (or now-fn #(js/Date.now))
    :token-ttl-ms token-ttl-ms
    :page-size    page-size
    :state        (atom {:store fs/empty-store
                         :access-tokens {}     ; token → expires-at
                         :refresh-tokens #{}
                         :codes {}             ; code → {:challenge :redirect-uri}
                         :cursors {}           ; cursor → remaining entries
                         :requests []
                         :script []
                         :counter 0})}))

(defn requests [srv] (:requests @(:state srv)))
(defn store [srv] (:store @(:state srv)))

(defn script!
  "Queues canned responses {:status :headers :body :match re}, returned before
  routing to the first request whose URL matches :match (any URL if absent)."
  [srv & responses]
  (swap! (:state srv) update :script into responses))

(defn- next-id! [srv prefix]
  (str prefix (:counter (swap! (:state srv) update :counter inc))))

(defn issue-tokens!
  "A fresh access + refresh token pair, as after a sign-in."
  [srv]
  (let [access  (next-id! srv "access-")
        refresh (next-id! srv "refresh-")]
    (swap! (:state srv) #(-> %
                             (assoc-in [:access-tokens access] (+ ((:now-fn srv)) (:token-ttl-ms srv)))
                             (update :refresh-tokens conj refresh)))
    {:access access :refresh refresh}))

(defn approve
  "What a user signing in does on the authorize page: Dropbox records the
  challenge and redirects back with a code. Use as a fake browser's `respond`."
  [srv authorize-url]
  (let [{:keys [code_challenge redirect_uri state]} (h/query-params authorize-url)
        code (next-id! srv "code-")]
    (swap! (:state srv) assoc-in [:codes code] {:challenge code_challenge :redirect-uri redirect_uri})
    (str redirect_uri "?code=" code "&state=" (h/url-encode state))))

(defn- resp
  ([status body] (resp status {} body))
  ([status headers body] {:status status :headers headers :body body}))

(defn- json-resp [status data] (resp status {"content-type" "application/json"} (json/encode data)))
(defn- error-409 [summary] (json-resp 409 {:error_summary summary}))

(defn- metadata [path rev]
  (array-map :name (subs path (inc (str/last-index-of path "/")))
             :path_lower (str/lower-case path) :path_display path :rev rev))

(defn- token-endpoint [srv body]
  (let [params (h/query-params (str "?" body))]
    (case (:grant_type params)
      "authorization_code"
      (let [{:keys [challenge redirect-uri]} (get-in @(:state srv) [:codes (:code params)])]
        (swap! (:state srv) update :codes dissoc (:code params))
        (if (and challenge
                 (= challenge (pkce/challenge (:code_verifier params)))
                 (= redirect-uri (:redirect_uri params)))
          (let [{:keys [access refresh]} (issue-tokens! srv)]
            (json-resp 200 {:access_token access :expires_in (quot (:token-ttl-ms srv) 1000)
                            :token_type "bearer" :refresh_token refresh
                            :account_id "dbid:fake-account"}))
          (json-resp 400 {:error "invalid_grant" :error_description "code doesn't match"})))

      "refresh_token"
      (if (contains? (:refresh-tokens @(:state srv)) (:refresh_token params))
        (let [access (next-id! srv "access-")]
          (swap! (:state srv) assoc-in [:access-tokens access]
                 (+ ((:now-fn srv)) (:token-ttl-ms srv)))
          (json-resp 200 {:access_token access :expires_in (quot (:token-ttl-ms srv) 1000)
                          :token_type "bearer"}))
        (json-resp 400 {:error "invalid_grant" :error_description "refresh token is invalid"}))

      (json-resp 400 {:error "unsupported_grant_type"}))))

(defn- authorized? [srv headers]
  (let [token (some-> (get headers "Authorization") (str/replace #"^Bearer " ""))
        exp   (get-in @(:state srv) [:access-tokens token])]
    (and exp (< ((:now-fn srv)) exp))))

(defn- run-store! [srv f & args]
  (let [[st' result] (apply f (store srv) args)]
    (swap! (:state srv) assoc :store st')
    result))

(defn- list-page [srv entries]
  (let [[page more] (split-at (:page-size srv) entries)
        cursor (when (seq more) (next-id! srv "cursor-"))]
    (when cursor (swap! (:state srv) assoc-in [:cursors cursor] more))
    (json-resp 200 {:entries (for [e page]
                               (array-map (keyword ".tag") "file" :name (:name e)
                                          :path_lower (:path e) :rev (:rev e)))
                    :cursor (or cursor "cursor-end") :has_more (some? cursor)})))

(defn- api [srv url headers body]
  (let [arg  (some-> (get headers "Dropbox-API-Arg") json/decode)
        data (when (and body (= "application/json" (get headers "Content-Type")))
               (json/decode body))
        path (or (:path arg) (:path data))]
    (condp = url
      (str h/content-host "/2/files/download")
      (let [{:keys [ok error]} (fs/download (store srv) path)]
        (if error
          (error-409 error)
          (resp 200 {"dropbox-api-result" (h/header-safe-json (metadata path (:rev ok)))} (:text ok))))

      (str h/content-host "/2/files/upload")
      (let [mode (case (get-in arg [:mode (keyword ".tag")])
                   "update" (get-in arg [:mode :update])
                   "overwrite" :overwrite
                   :add)
            {:keys [ok error]} (run-store! srv fs/upload path body mode)]
        (if error (error-409 error) (json-resp 200 (metadata path (:rev ok)))))

      (str h/api-host "/2/files/delete_v2")
      (let [{:keys [error]} (run-store! srv fs/delete path)]
        (if error (error-409 error) (json-resp 200 {:metadata (metadata path "0")})))

      (str h/api-host "/2/files/list_folder")
      (let [{:keys [ok error]} (fs/list-folder (store srv) path)]
        (if error (error-409 error) (list-page srv ok)))

      (str h/api-host "/2/files/list_folder/continue")
      (if-let [more (get-in @(:state srv) [:cursors (:cursor data)])]
        (list-page srv more)
        (error-409 "reset/.."))

      (str h/api-host "/2/users/get_current_account")
      (json-resp 200 (:account (store srv)))

      (str h/api-host "/2/auth/token/revoke")
      (let [token (str/replace (get headers "Authorization") #"^Bearer " "")]
        (swap! (:state srv) update :access-tokens dissoc token)
        (resp 200 "null"))

      (resp 404 "Unknown endpoint"))))

(defn- js-response [{:keys [status headers body]}]
  #js {:status  status
       :headers #js {:forEach (fn [f] (doseq [[k v] headers] (f v k)))}
       :text    (fn [] (js/Promise.resolve (or body "")))})

(defn fetch-fn
  "A `fetch` for notebox.infra.http/fetch-http."
  [srv]
  (fn [url ^js opts]
    (let [headers (js->clj (.-headers opts))
          body    (.-body opts)
          request {:method (.-method opts) :url url :headers headers :body body}]
      (swap! (:state srv) update :requests conj request)
      (let [script   (:script @(:state srv))
            i        (first (keep-indexed (fn [i r] (when (or (nil? (:match r)) (re-find (:match r) url)) i))
                                          script))
            scripted (when i (nth script i))]
        (js/Promise.resolve
         (js-response
          (cond
            scripted (do (swap! (:state srv) assoc :script (into (subvec script 0 i) (subvec script (inc i))))
                         (dissoc scripted :match))
            (= url (str h/api-host "/oauth2/token")) (token-endpoint srv body)
            (not (authorized? srv headers))
            (json-resp 401 {:error_summary "expired_access_token/"
                            :error {(keyword ".tag") "expired_access_token"}})
            :else (api srv url headers body))))))))
