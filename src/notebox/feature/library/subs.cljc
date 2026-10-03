(ns notebox.feature.library.subs
  (:require [re-frame.core :as rf]
            [notebox.domain.meta :as meta]
            [notebox.feature.library.queries :as q]))

(rf/reg-sub :library/meta-status (fn [db _] (q/meta-status db)))
(rf/reg-sub :library/meta (fn [db _] (q/meta-file db)))
(rf/reg-sub :library/books :<- [:library/meta] (fn [m _] (meta/books m)))
(rf/reg-sub :library/totals :<- [:library/meta] (fn [m _] (meta/totals m)))
(rf/reg-sub :library/book-info :<- [:library/meta] (fn [m [_ slug]] (meta/book-info m slug)))
(rf/reg-sub :library/book-status (fn [db [_ slug]] (q/book-status db slug)))
(rf/reg-sub :library/book-notes (fn [db [_ slug]] (q/book-notes db slug)))
(rf/reg-sub :library/note (fn [db [_ book slug]] (q/find-note db book slug)))
(rf/reg-sub :library/loaded-books (fn [db _] (q/loaded-books db)))
(rf/reg-sub :library/last-active (fn [db _] (q/last-active db)))
