(ns notebox.feature.tags.events
  "Tag counts need every book's notes: opening the Tags screen loads them."
  (:require [re-frame.core :as rf]
            [notebox.feature.library.queries :as library]))

(rf/reg-event-fx
 :tags/load-counts
 (fn [{:keys [db]} _]
   {:dispatch [:library/load-books (library/unloaded-slugs db)]}))
