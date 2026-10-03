(ns notebox.feature.books.events
  (:require [re-frame.core :as rf]
            [notebox.feature.settings.queries :as settings]))

(rf/reg-event-fx
 :books/create
 [(rf/inject-cofx :notebox/new-slugs)]
 (fn [{:keys [new-slugs]} [_ title]]
   {:dispatch [:library/save [{:op :book/create :book (first new-slugs) :title title}]
               {:success-message "Book added."}]}))

(rf/reg-event-fx
 :books/rename
 (fn [_ [_ slug title]]
   {:dispatch [:library/save [{:op :book/rename :book slug :title title}]
               {:success-message "Book renamed."}]}))

(rf/reg-event-fx
 :books/delete
 (fn [{:keys [db]} [_ slug]]
   {:fx (cond-> [[:dispatch [:library/save [{:op :book/delete :book slug}]
                             {:success-message "Book deleted."}]]]
          (= slug (settings/default-book db))
          (conj [:dispatch [:settings/set-default-book nil]]))}))
