(ns notebox.dropbox.auth
  "The Dropbox session: OAuth code flow with PKCE and an offline refresh token
  (spec/roadmap.md §7). The refresh token lives in the secure store; the access
  token and its expiry only in memory. `token!` refreshes when the access token
  is within 5 minutes of expiry, single-flight. A rejected refresh token
  (invalid_grant) signs the user out: it rejects with :unauthorized and the
  stored token is removed."
  (:require [integrant.core :as ig]
            [notebox.dropbox.errors :as errors]
            [notebox.dropbox.http :as dhttp]
            [notebox.dropbox.pkce :as pkce]
            [notebox.infra.browser :as browser]
            [notebox.infra.http :as http]
            [notebox.infra.secure-store :as secure-store]))

(def refresh-token-key "dropbox-refresh-token")
(def refresh-margin-ms (* 5 60 1000))

(defn- ok-or-throw [resp]
  (if (<= 200 (:status resp) 299)
    resp
    (throw (errors/->ex (errors/from-response resp)))))

(defn- unauthorized [summary]
  (errors/->ex {:type :unauthorized :summary summary}))

(defn- store-tokens! [{:keys [state]} result]
  (swap! state merge (select-keys result [:access-token :expires-at])))

(defn refresh!
  "Gets a new access token with the stored refresh token. Concurrent calls
  share one request. Promise of the access token."
  [{:keys [state config http secure-store now-fn log] :as auth}]
  (or (:pending @state)
      (let [p (-> (secure-store/read-secret secure-store refresh-token-key)
                  (.then (fn [refresh-token]
                           (when-not refresh-token (throw (unauthorized "not signed in")))
                           (log :info "refreshing the Dropbox access token")
                           (let [sent-at (now-fn)]
                             (-> (http/request http (dhttp/refresh-request
                                                     {:refresh-token refresh-token
                                                      :app-key (:app-key config)}))
                                 (.then ok-or-throw)
                                 (.then #(dhttp/token-result (:body %) sent-at))))))
                  (.then (fn [result]
                           (store-tokens! auth result)
                           (:access-token result)))
                  (.catch (fn [e]
                            (if (= :unauthorized (:type (ex-data e)))
                              (-> (secure-store/delete-secret! secure-store refresh-token-key)
                                  (.then (fn [] (reset! state {}) (throw e))))
                              (throw e))))
                  (.finally #(swap! state dissoc :pending)))]
        (swap! state assoc :pending p)
        p)))

(defn token!
  "Promise of a valid access token, refreshing first when needed."
  [{:keys [state now-fn] :as auth}]
  (let [{:keys [access-token expires-at]} @state]
    (if (and access-token (> (- expires-at (now-fn)) refresh-margin-ms))
      (js/Promise.resolve access-token)
      (refresh! auth))))

(defn signed-in?
  "Promise of whether a refresh token is stored."
  [{:keys [secure-store]}]
  (.then (secure-store/read-secret secure-store refresh-token-key) some?))

(defn login!
  "Runs the sign-in flow in the browser and stores the tokens. Promise of
  {:account-id ...}; rejects with :unauthorized if the user declines."
  [{:keys [config http browser secure-store now-fn log state] :as auth}]
  (let [verifier (pkce/new-verifier)
        st       (pkce/new-verifier)
        url      (dhttp/authorize-url {:app-key      (:app-key config)
                                       :redirect-uri (:redirect-uri config)
                                       :challenge    (pkce/challenge verifier)
                                       :state        st})]
    (-> (browser/open-auth! browser url (:redirect-uri config))
        (.then (fn [redirect-url]
                 (let [{:keys [code error description]} (dhttp/parse-redirect redirect-url st)]
                   (when error
                     (throw (unauthorized (str (name error) (when description (str ": " description))))))
                   (let [sent-at (now-fn)]
                     (-> (http/request http (dhttp/code-exchange-request
                                             {:code code :app-key (:app-key config)
                                              :redirect-uri (:redirect-uri config)
                                              :verifier verifier}))
                         (.then ok-or-throw)
                         (.then #(dhttp/token-result (:body %) sent-at)))))))
        (.then (fn [{:keys [refresh-token] :as result}]
                 (when-not refresh-token (throw (unauthorized "no refresh token in the response")))
                 (-> (secure-store/write-secret! secure-store refresh-token-key refresh-token)
                     (.then (fn []
                              (reset! state {})
                              (store-tokens! auth result)
                              (log :info "signed in to Dropbox")
                              (select-keys result [:account-id])))))))))

(defn logout!
  "Revokes the token (best effort) and forgets it. Promise of nil."
  [{:keys [http secure-store state log]}]
  (let [revoke (if-let [t (:access-token @state)]
                 (-> (http/request http (assoc-in (dhttp/revoke-request)
                                                  [:headers "Authorization"] (str "Bearer " t)))
                     (.catch (fn [e] (log :warn "token revoke failed" (ex-message e)))))
                 (js/Promise.resolve nil))]
    (-> revoke
        (.then #(secure-store/delete-secret! secure-store refresh-token-key))
        (.then (fn [] (reset! state {}) nil)))))

(defn expire!
  "Marks the access token as expired (for testing the refresh path)."
  [{:keys [state]}]
  (swap! state assoc :expires-at 0))

(defn- console-log [level & args]
  (apply (if (= level :warn) js/console.warn js/console.log) "[dropbox]" args))

(defn auth
  "An auth component. `config` {:app-key :redirect-uri}."
  [{:keys [config http secure-store browser now-fn log]}]
  {:config       config
   :http         http
   :secure-store secure-store
   :browser      browser
   :now-fn       (or now-fn #(js/Date.now))
   :log          (or log console-log)
   :state        (atom {})})

(defmethod ig/init-key :notebox.dropbox/auth [_ {:keys [app-key redirect-uri] :as opts}]
  (auth (assoc opts :config {:app-key app-key :redirect-uri redirect-uri})))
