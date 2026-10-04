(ns notebox.feature.editor.events
  (:require [re-frame.core :as rf]
            [notebox.feature.editor.queries :as q]
            [notebox.feature.library.queries :as library]))

(rf/reg-event-fx
 :editor/create-note
 [(rf/inject-cofx :notebox/now) (rf/inject-cofx :notebox/new-slugs)]
 (fn [cofx [_ form]]
   (let [ops  (q/create-ops form cofx)
         note (:note (last ops))
         book (q/final-book ops)]
     {:dispatch [:library/save ops {:success-message "Note added."}]
      :nav/navigate [:note {:book book :note (:slug note)}]})))

(rf/reg-event-fx
 :editor/update-note
 [(rf/inject-cofx :notebox/now) (rf/inject-cofx :notebox/new-slugs)]
 (fn [{:keys [db] :as cofx} [_ book slug changes move]]
   (if-let [current (library/find-note db book slug)]
     (let [ops (q/update-ops current book changes move cofx)]
       {:dispatch [:library/save ops {:success-message "Note saved."}]
        :nav/navigate [:note {:book (q/final-book ops) :note slug}]})
     {:dispatch [:messaging/show {:type :error :text "That note isn't loaded."}]})))

(rf/reg-event-fx
 :editor/delete-note
 (fn [_ [_ book slug]]
   {:dispatch [:library/save [{:op :note/remove :book book :slug slug}]
               {:success-message "Note deleted."}]
    :nav/navigate [:book {:book book}]}))
