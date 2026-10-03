(ns notebox.infra.browser
  "Opening the OAuth sign-in page and waiting for the redirect back into the
  app. One function, so the implementation can change (Linking now; a native
  ASWebAuthenticationSession later) without touching the auth logic.
  :linking is registered by notebox.infra.rn.linking, which only the app loads."
  (:require [integrant.core :as ig]))

(defprotocol Browser
  (open-auth! [this url redirect-prefix]
    "Shows `url`; Promise of the URL the browser was redirected to, once it
    starts with `redirect-prefix`."))

(defrecord FakeBrowser [respond opened]
  Browser
  (open-auth! [_ url _]
    (swap! opened conj url)
    (js/Promise.resolve (respond url))))

(defn fake-browser
  "A browser that answers each sign-in page with `(respond url)`: the redirect
  URL a user would come back with. `opened` collects the pages shown."
  [respond]
  (->FakeBrowser respond (atom [])))

(defmulti create :impl)

(defmethod create :fake [_]
  (fake-browser (fn [_] "notebox://oauth?error=access_denied")))

(defmethod ig/init-key :notebox.infra/browser [_ opts]
  (create opts))
