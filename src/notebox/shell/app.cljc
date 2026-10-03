(ns notebox.shell.app
  "The :notebox/app component: registers all events and subs (by requiring
  their namespaces) and initializes app-db."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.shell.events]))

(defmethod ig/init-key :notebox/app [_ {:keys [profile]}]
  (rf/dispatch-sync [:app/initialize profile])
  {:profile profile})
