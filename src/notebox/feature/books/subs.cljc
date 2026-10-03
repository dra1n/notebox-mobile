(ns notebox.feature.books.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.books.queries :as q]))

(rf/reg-sub :books/list (fn [db _] (q/book-list db)))
