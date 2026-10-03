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

(def base
  {:notebox/app {:profile (ig/profile :dev :dev :test :test :e2e :e2e :prod :prod)}
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
