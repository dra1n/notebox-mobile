(ns notebox.dev
  "REPL helpers. In the Krell REPL:

    (require '[notebox.dev :as dev])
    (dev/reset)   ; halt the system, reload nothing, init it again"
  (:require [notebox.core :as core]))

(defn reset
  "Halt and re-init the Integrant system with the build profile. Use it after
  changing infrastructure (components); views, events and subs hot-reload."
  []
  (core/stop!)
  (core/start!)
  :reset)
