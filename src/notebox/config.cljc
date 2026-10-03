(ns notebox.config
  "The Integrant system config. Values that differ per environment are wrapped
  in `ig/profile`; `(config profile)` resolves them.

  Profiles:
  - :dev  — local development against the real Dropbox account
  - :test — node/JVM tests; fakes only, no React Native
  - :e2e  — Maestro runs; the app with a seeded fake Dropbox
  - :prod — release builds"
  (:require [integrant.core :as ig]))

;; Chosen at build time: `:closure-defines {notebox.config/PROFILE "e2e"}`.
#?(:cljs (goog-define PROFILE "dev"))

(def profiles #{:dev :test :e2e :prod})

(def dropbox-app
  "The web client's Dropbox app: web and mobile are the same app (roadmap §7)."
  {:app-key      "2t7xyn3a902rv0z"
   :redirect-uri "notebox://oauth"})

(defn- real-or-fake [real fake]
  (ig/profile :dev real :prod real :test fake :e2e fake))

(def base
  {:notebox.infra/http         {:timeout-ms 20000}
   :notebox.infra/secure-store {:impl (real-or-fake :keychain :memory) :service "notebox"}
   :notebox.infra/browser      {:impl (real-or-fake :linking :fake)}
   :notebox.dropbox/auth       (merge dropbox-app
                                      {:http         (ig/ref :notebox.infra/http)
                                       :secure-store (ig/ref :notebox.infra/secure-store)
                                       :browser      (ig/ref :notebox.infra/browser)})
   :notebox.dropbox/client     {:impl (real-or-fake :http :fake)
                                :http (ig/ref :notebox.infra/http)
                                :auth (ig/ref :notebox.dropbox/auth)}
   :notebox.infra.js/luggage   {:client (ig/ref :notebox.dropbox/client)}
   :notebox.storage/repository {:luggage (ig/ref :notebox.infra.js/luggage)}
   :notebox.infra/kv-store     {:impl (real-or-fake :async-storage :memory) :name "notebox"}
   :notebox.infra/navigator    {}

   ;; re-frame's effects and coeffects (notebox.fx.*), built over the components
   :notebox.shell/effects {:repository      (ig/ref :notebox.storage/repository)
                           :auth            (ig/ref :notebox.dropbox/auth)
                           :client          (ig/ref :notebox.dropbox/client)
                           :kv-store        (ig/ref :notebox.infra/kv-store)
                           :navigator       (ig/ref :notebox.infra/navigator)
                           :on-unauthorized [:app/session-expired]}

   :notebox/app {:profile (ig/profile :dev :dev :test :test :e2e :e2e :prod :prod)
                 :effects (ig/ref :notebox.shell/effects)}
   :notebox/ui  {:app (ig/ref :notebox/app)}})

(defn config
  "The config for `profile` (one of `profiles`), with all profile values resolved."
  [profile]
  {:pre [(contains? profiles profile)]}
  (ig/deprofile base [profile]))

(defn build-profile
  "The profile this build was compiled for."
  []
  #?(:cljs (keyword PROFILE)
     :clj  :dev))
