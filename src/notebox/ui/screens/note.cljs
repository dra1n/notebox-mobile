(ns notebox.ui.screens.note
  "Reading a note (spec/design/screens/05)."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.tags :as tags]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.theme :as t]))

(defn note [{book :book slug :note}]
  (r/with-let [_ (rf/dispatch [:library/open-book book])]
    (let [info   @(rf/subscribe [:library/book-info book])
          status @(rf/subscribe [:library/book-status book])
          n      @(rf/subscribe [:library/note book slug])]
      [frame/screen-frame
       [:<>
        [frame/header {:left :back :on-left #(rf/dispatch [:nav/go-back]) :title (:title n)
                       :action (when n {:label "Edit" :on-press #(rf/dispatch [:nav/edit-note book slug])})}]
        (cond
          (#{:unloaded :loading} status) [feedback/loading {}]
          (nil? n) [feedback/not-found {:action {:label "Back" :on-press #(rf/dispatch [:nav/go-back])}}]
          :else
          [:> rn/scroll-view {:testID "note-detail"
                              :style {:background-color (t/color :white)}
                              :content-container-style {:padding 20 :gap 16 :flex-grow 1}}
           [:> rn/text {:style (merge (t/font :regular 13) {:color (t/color :text-grey)})} (:title info)]
           [:> rn/text {:testID "note-title" :style (t/font :semibold 24)}
            (if (seq (:title n)) (:title n) "No title")]
           [:> rn/view {:style {:height 1 :background-color (t/color :bg-light)}}]
           [:> rn/text {:testID "note-text" :selectable true
                        :style (merge (t/font :regular 16) {:line-height 24})}
            (:text n)]
           [:> rn/view {:style {:flex 1 :min-height 40}}]
           [tags/chips {:tags (:tags n)}]])]])))
