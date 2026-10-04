(ns notebox.e2e.setup
  "Preloaded by the e2e build only (e2e/build.edn): the app starts signed in,
  over a fake Dropbox holding the fixture library. Nothing here reaches the
  dev or release builds."
  (:require-macros [notebox.e2e.seed :refer [fixture-files]])
  (:require [notebox.config :as config]
            [notebox.domain.json :as json]))

(def seed
  (into {} (for [[path text] (fixture-files)] [path (json/decode text)])))

(swap! config/overrides merge
       {:notebox.dropbox/client     {:seed seed}
        :notebox.infra/secure-store {:initial {"dropbox-refresh-token" "e2e"}}})
