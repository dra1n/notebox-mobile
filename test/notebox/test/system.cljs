(ns notebox.test.system
  "The test system for feature tests: the real :test config (fake Dropbox,
  memory stores, fake browser) with deterministic time and slugs, a recording
  navigator, and the Phase 1 fixture library in the fake Dropbox."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [notebox.config :as config]
            [notebox.dropbox.auth :as auth]
            [notebox.fx.auth]
            [notebox.fx.cofx]
            [notebox.fx.navigation]
            [notebox.fx.settings]
            [notebox.fx.storage]
            [notebox.infra.browser]
            [notebox.infra.http]
            [notebox.infra.js.luggage]
            [notebox.infra.kv-store]
            [notebox.infra.secure-store]
            [notebox.dropbox.client]
            [notebox.shell.app]
            [notebox.storage.repository]
            [notebox.storage.repository-test :refer [export-files]]))

(def now-ms 1791030896789)   ; 2026-10-03T12:34:56.789Z
(def now-iso "2026-10-03T12:34:56.789Z")

(defonce ^:private current (atom nil))

(defn stop! []
  (when-let [s @current]
    (ig/halt! (:system s))
    (reset! current nil)))

(defn start!
  "Starts a fresh test system; returns
  {:system :dbx (the fake Dropbox) :nav (recorded navigation) :kv :secure-store}.
  opts: :signed-in? (default true), :files (default: the fixture library),
  :settings (kv-store contents), :http-fetch / :browser-respond (for sign-in)."
  ([] (start! {}))
  ([{:keys [signed-in? files settings http-fetch browser-respond]
     :or {signed-in? true}}]
   (stop!)
   (reset! app-db {})
   (let [slugs (atom 0)
         cfg   (-> (config/config :test)
                   (dissoc :notebox/ui)
                   (assoc-in [:notebox.dropbox/client :seed] (or files (export-files)))
                   (assoc-in [:notebox.infra/secure-store :initial]
                             (if signed-in? {auth/refresh-token-key "refresh-test"} {}))
                   (assoc-in [:notebox.infra/kv-store :initial] (or settings {}))
                   (assoc-in [:notebox.fx/cofx :now-fn] (constantly now-ms))
                   (assoc-in [:notebox.fx/cofx :slug-fn] #(str "newSlug" (swap! slugs inc) "xx"))
                   (cond->
                     http-fetch      (assoc-in [:notebox.infra/http :fetch-fn] http-fetch)
                     browser-respond (assoc-in [:notebox.infra/browser :respond] browser-respond)))
         nav   (atom [])
         sys   (ig/init cfg)]
     ;; :app/initialize (dispatched while :notebox/app starts) doesn't navigate.
     (reset! (get-in sys [:notebox.fx/navigation :navigator])
             (fn [action route params] (swap! nav conj [action route params])))
     (reset! current {:system sys})
     {:system       sys
      :dbx          (:notebox.dropbox/client sys)
      :nav          nav
      :kv           (:notebox.infra/kv-store sys)
      :secure-store (:notebox.infra/secure-store sys)})))

(defn await-db
  "Promise of app-db once `(pred db)` holds (checked now and on every change).
  Rejects after `ms` with the last db in ex-data."
  ([pred] (await-db pred 3000))
  ([pred ms]
   (js/Promise.
    (fn [resolve reject]
      (let [k     (gensym "await")
            done  (atom false)
            check (fn [db]
                    (when (and (not @done) (pred db))
                      (reset! done true)
                      (remove-watch app-db k)
                      (resolve db)))]
        (add-watch app-db k (fn [_ _ _ db] (check db)))
        (check @app-db)
        (js/setTimeout (fn []
                         (when-not @done
                           (reset! done true)
                           (remove-watch app-db k)
                           (reject (ex-info "await-db timed out" {:db @app-db}))))
                       ms))))))

(defn- pending [db] (get-in db [:sync :pending] 0))

(defn await-saving
  "Promise of app-db once a save has started (and its optimistic change is in)."
  []
  (await-db #(pos? (pending %))))

(defn await-saved
  "Promise of app-db once a save has started and everything has settled."
  []
  (.then (await-saving) #(await-db (fn [db] (zero? (pending db))))))

(defn await-message
  "Promise of app-db once a toast with `text` is showing."
  [text]
  (await-db (fn [db] (some #(= text (:text %)) (get-in db [:messaging :messages])))))

(defn sub [q] @(rf/subscribe q))
