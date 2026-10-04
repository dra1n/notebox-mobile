(ns notebox.shell.app
  "The :notebox/app component: registers all events and subs (by requiring
  their namespaces) and initializes app-db. It depends on
  :notebox.shell/effects, so effects exist before the first event."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.feature.auth.events]
            [notebox.feature.auth.subs]
            [notebox.feature.books.events]
            [notebox.feature.books.subs]
            [notebox.feature.editor.events]
            [notebox.feature.editor.subs]
            [notebox.feature.library.events]
            [notebox.feature.library.subs]
            [notebox.feature.messaging.events]
            [notebox.feature.messaging.subs]
            [notebox.feature.nav.events]
            [notebox.feature.search.events]
            [notebox.feature.search.subs]
            [notebox.feature.settings.events]
            [notebox.feature.settings.subs]
            [notebox.feature.sync.subs]
            [notebox.feature.tags.events]
            [notebox.feature.tags.subs]
            [notebox.shell.events]))

(defmethod ig/init-key :notebox/app [_ {:keys [profile]}]
  (rf/dispatch-sync [:app/initialize profile])
  {:profile profile})
