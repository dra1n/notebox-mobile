(ns notebox.e2e.main
  "Entry point of the e2e build (e2e/build.edn :main): installs the e2e setup,
  then runs the app."
  (:require [notebox.e2e.setup]
            [notebox.core :as core]))

(defn ^:export -main [& args]
  (apply core/-main args))
