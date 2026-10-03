(ns notebox.feature.books.events
  "VIOLATION: requires another feature's events. ALLOWED: a lower feature's subs."
  (:require [notebox.feature.library.events]
            [notebox.feature.library.subs]))
