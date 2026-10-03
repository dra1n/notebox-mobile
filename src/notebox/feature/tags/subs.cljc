(ns notebox.feature.tags.subs
  (:require [re-frame.core :as rf]
            [notebox.domain.tags :as tags]
            [notebox.feature.library.queries :as library]))

(rf/reg-sub :tags/index
            (fn [db _] (tags/tag-index (library/meta-file db) (library/loaded-books db))))
