(ns notebox.dev
  "REPL helpers. In the Krell REPL:

    (require '[notebox.dev :as dev])
    (dev/reset)            ; halt the system and init it again
    (dev/login!)           ; sign in to Dropbox in the simulator's browser
    (dev/check-dropbox)    ; print the account email and the parsed .meta.json
    (dev/expire-token!)    ; make the next call refresh the access token
    (dev/logout!)
    (dev/load-meta)        ; the meta, read through Luggage
    (dev/load-book \"slug\")
    (dev/apply-op! {:op :book/create :book (dev/new-slug) :title \"Mobile test\"})

  The Dropbox helpers are asynchronous: they print when done, and keep the
  result (or error) in `dev/result`, so `@dev/result` shows it too."
  (:require [notebox.core :as core]
            [notebox.dropbox.api :as api]
            [notebox.domain.note :as note]
            [notebox.dropbox.auth :as auth]
            [notebox.storage.repository :as repository]))

(defonce result (atom nil))

(defn reset
  "Halt and re-init the Integrant system with the build profile. Use it after
  changing infrastructure (components); views, events and subs hot-reload."
  []
  (core/stop!)
  (core/start!)
  :reset)

(defn- component [k] (get @core/system k))

(defn- report! [label p]
  (-> p
      (.then (fn [v]
               (reset! result v)
               (println label "→" (pr-str v))
               v))
      (.catch (fn [e]
                (reset! result {:error (ex-message e) :data (ex-data e)})
                (println label "failed:" (ex-message e) (pr-str (ex-data e))))))
  :pending)

(defn login! []
  (report! "login" (auth/login! (component :notebox.dropbox/auth))))

(defn logout! []
  (report! "logout" (auth/logout! (component :notebox.dropbox/auth))))

(defn expire-token! []
  (auth/expire! (component :notebox.dropbox/auth))
  :expired)

(defn check-dropbox
  "The signed-in account's email and its parsed /notes/.meta.json."
  []
  (let [dbx (component :notebox.dropbox/client)]
    (report! "check-dropbox"
             (-> (js/Promise.all #js [(api/current-account dbx)
                                      (api/download dbx "/notes/.meta.json")])
                 (.then (fn [[account meta-file]]
                          {:email (:email account)
                           :meta  (:data meta-file)
                           :rev   (:rev meta-file)}))))))

(defn new-slug [] (note/new-slug))

(defn load-meta []
  (report! "load-meta" (repository/load-meta (component :notebox.storage/repository))))

(defn load-book [slug]
  (report! "load-book" (repository/load-book (component :notebox.storage/repository) slug)))

(defn apply-op!
  "Applies a notebox.domain.ops op to Dropbox through Luggage."
  [op]
  (report! "apply-op!" (repository/apply-op! (component :notebox.storage/repository) op)))
