(ns notebox.feature.search.events
  (:require [re-frame.core :as rf]
            [notebox.domain.search :as search]
            [notebox.feature.library.queries :as library]
            [notebox.feature.search.queries :as q]))

(rf/reg-event-fx
 :search/set-query
 (fn [{:keys [db]} [_ query scope]]
   (let [db' (q/set-query db query scope)
         {:keys [type book]} (q/scope db')]
     (cond-> {:db db'}
       (and (= :all type) (search/normalize-query query))
       (assoc :dispatch [:library/load-books (library/unloaded-slugs db)])

       (= :book type)
       (assoc :dispatch [:library/open-book book])))))

(rf/reg-event-db
 :search/clear
 (fn [db _] (q/clear db)))
