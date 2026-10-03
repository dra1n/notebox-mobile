(ns notebox.dropbox.http
  "Pure request building and response parsing for the Dropbox HTTP API and its
  OAuth endpoints. A request is {:method :url :headers :body}; a response is
  {:status :headers :body} with lower-cased header names and the body as text.
  The client adds the Authorization header and does the I/O."
  (:require [clojure.string :as str]
            [notebox.domain.json :as json]))

(def api-host "https://api.dropboxapi.com")
(def content-host "https://content.dropboxapi.com")
(def authorize-endpoint "https://www.dropbox.com/oauth2/authorize")

(def tag
  "The \".tag\" key of Dropbox unions."
  (keyword ".tag"))

;; --- encoding helpers ---------------------------------------------------------

(defn header-safe-json
  "JSON for the Dropbox-API-Arg header: like JSON.stringify, but every character
  outside printable ASCII is escaped as \\uXXXX (as Dropbox requires)."
  [x]
  (let [s (json/encode x)]
    (apply str (map (fn [ch]
                      (let [c #?(:clj (int ch) :cljs (.charCodeAt ch 0))]
                        (if (or (< c 0x20) (> c 0x7E))
                          (let [h #?(:clj (Integer/toHexString c) :cljs (.toString c 16))]
                            (str "\\u" (subs "0000" (count h)) h))
                          (str ch))))
                    s))))

(defn url-encode
  "RFC 3986 percent-encoding of `s` (UTF-8): everything but A-Z a-z 0-9 - . _ ~."
  [s]
  #?(:clj  (-> (java.net.URLEncoder/encode (str s) "UTF-8")
               (str/replace "+" "%20")
               (str/replace "*" "%2A")
               (str/replace "%7E" "~"))
     :cljs (str/replace (js/encodeURIComponent (str s)) #"[!'()*]"
                        #(str "%" (.toUpperCase (.toString (.charCodeAt % 0) 16))))))

(defn url-decode [s]
  #?(:clj  (java.net.URLDecoder/decode (str s) "UTF-8")
     :cljs (js/decodeURIComponent (str/replace (str s) "+" " "))))

(defn form-encode
  "application/x-www-form-urlencoded (and query) text of [[k v] ...] pairs."
  [pairs]
  (str/join "&" (for [[k v] pairs :when (some? v)]
                  (str (url-encode (name k)) "=" (url-encode v)))))

(defn query-params
  "The query parameters of `url` as a map of keyword → string."
  [url]
  (let [q (second (str/split (first (str/split (str url) #"#" 2)) #"\?" 2))]
    (into {} (for [part (str/split (or q "") #"&")
                   :when (seq part)
                   :let [[k v] (str/split part #"=" 2)]]
               [(keyword (url-decode k)) (url-decode (or v ""))]))))

;; --- OAuth --------------------------------------------------------------------

(defn authorize-url
  "The page the user signs in on: code flow with PKCE and an offline (refresh)
  token."
  [{:keys [app-key redirect-uri challenge state]}]
  (str authorize-endpoint "?"
       (form-encode [[:client_id app-key]
                     [:response_type "code"]
                     [:code_challenge challenge]
                     [:code_challenge_method "S256"]
                     [:token_access_type "offline"]
                     [:redirect_uri redirect-uri]
                     [:state state]])))

(defn parse-redirect
  "What the authorize page sent back to `redirect-url`: {:code c}, or
  {:error kw :description d}. A wrong `state` is an error (CSRF protection)."
  [redirect-url expected-state]
  (let [{:keys [code state error error_description]} (query-params redirect-url)]
    (cond
      error                     {:error (keyword error) :description error_description}
      (not= state expected-state) {:error :state-mismatch}
      (not (seq code))          {:error :no-code}
      :else                     {:code code})))

(defn token-request [pairs]
  {:method  "POST"
   :url     (str api-host "/oauth2/token")
   :headers {"Content-Type" "application/x-www-form-urlencoded"}
   :body    (form-encode pairs)})

(defn code-exchange-request [{:keys [code app-key redirect-uri verifier]}]
  (token-request [[:grant_type "authorization_code"]
                  [:code code]
                  [:client_id app-key]
                  [:redirect_uri redirect-uri]
                  [:code_verifier verifier]]))

(defn refresh-request [{:keys [refresh-token app-key]}]
  (token-request [[:grant_type "refresh_token"]
                  [:refresh_token refresh-token]
                  [:client_id app-key]]))

(defn token-result
  "{:access-token :expires-at (epoch ms) :refresh-token? :account-id?} from a
  token response body, given the time the request was sent."
  [body sent-at-ms]
  (let [{:keys [access_token expires_in refresh_token account_id]} (json/decode body)]
    (cond-> {:access-token access_token
             :expires-at   (+ sent-at-ms (* 1000 (or expires_in 0)))}
      refresh_token (assoc :refresh-token refresh_token)
      account_id    (assoc :account-id account_id))))

;; --- API endpoints ------------------------------------------------------------

(defn- rpc [path arg]
  {:method  "POST"
   :url     (str api-host path)
   :headers {"Content-Type" "application/json"}
   :body    (json/encode arg)})

(defn download-request [path]
  {:method  "POST"
   :url     (str content-host "/2/files/download")
   :headers {"Dropbox-API-Arg" (header-safe-json (array-map :path path))}})

(defn upload-mode
  "{:rev r}: update only that revision. {:mode :overwrite}: replace whatever is
  there (what Luggage does). Otherwise: add, never overwriting."
  [{:keys [rev mode]}]
  (cond
    rev                 (array-map tag "update" :update rev)
    (= :overwrite mode) (array-map tag "overwrite")
    :else               (array-map tag "add")))

(defn upload-request
  "`opts` as in `upload-mode`."
  [path text opts]
  {:method  "POST"
   :url     (str content-host "/2/files/upload")
   :headers {"Content-Type"    "application/octet-stream"
             "Dropbox-API-Arg" (header-safe-json (array-map :path path
                                                            :mode (upload-mode opts)
                                                            :autorename false
                                                            :mute true))}
   :body    text})

(defn delete-request [path]
  (rpc "/2/files/delete_v2" (array-map :path path)))

(defn list-folder-request [path]
  (rpc "/2/files/list_folder" (array-map :path path :recursive false :include_deleted false)))

(defn list-folder-continue-request [cursor]
  (rpc "/2/files/list_folder/continue" (array-map :cursor cursor)))

(defn current-account-request []
  (rpc "/2/users/get_current_account" nil))

(defn revoke-request []
  (rpc "/2/auth/token/revoke" nil))

;; --- results ------------------------------------------------------------------

(defn download-result
  "{:text body :rev r}: the file's metadata comes in the Dropbox-API-Result header."
  [{:keys [headers body]}]
  {:text body
   :rev  (:rev (json/decode (get headers "dropbox-api-result")))})

(defn upload-result [{:keys [body]}]
  {:rev (:rev (json/decode body))})

(defn list-folder-result [{:keys [body]}]
  (let [{:keys [entries cursor has_more]} (json/decode body)]
    {:entries  (vec (for [e entries :when (= "file" (get e tag))]
                      {:name (:name e) :path (:path_lower e) :rev (:rev e)}))
     :cursor   cursor
     :has-more (boolean has_more)}))

(defn account-result [{:keys [body]}]
  (let [{:keys [account_id email name]} (json/decode body)]
    {:account-id account_id :email email :name (:display_name name)}))
