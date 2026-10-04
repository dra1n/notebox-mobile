(ns notebox.ui.screens.editor
  "Editing and creating notes (spec/design/screens/06, 07). The form lives in
  a local atom until Save; Save dispatches the editor events (Phase 4)."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.modals :as modals]
            [notebox.ui.components.tags :as tags]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.theme :as t]))

(defn- form-view
  [{:keys [form books suggestions title-placeholder text-placeholder on-delete]}]
  (r/with-let [new-book-prompt? (r/atom false)]
    [:> rn/keyboard-avoiding-view {:behavior (when rn/ios? "padding") :style {:flex 1}}
     [:> rn/scroll-view {:keyboard-should-persist-taps "handled"
                         :style {:background-color (t/color :white)}
                         :content-container-style {:padding 16 :gap 18 :flex-grow 1}}
      [modals/book-picker {:books books :selected (:book @form) :new-book-title (:new-book-title @form)
                           :on-select #(swap! form assoc :book % :new-book-title nil)
                           :on-new-book #(reset! new-book-prompt? true)}]
      [:> rn/text-input {:value (:title @form) :placeholder title-placeholder :testID "note-title-input"
                         :placeholder-text-color (t/color :text-grey)
                         :on-change-text #(swap! form assoc :title %)
                         :style (merge (t/font :medium 18)
                                       {:padding-vertical 8 :border-bottom-width 1
                                        :border-bottom-color (t/color :cyan)})}]
      [:> rn/text-input {:value (:text @form) :placeholder text-placeholder :multiline true
                         :testID "note-text-input" :placeholder-text-color (t/color :text-grey)
                         :text-align-vertical "top"
                         :on-change-text #(swap! form assoc :text %)
                         :style (merge (t/font :regular 15) {:min-height 160 :line-height 22})}]
      [:> rn/view {:style {:flex 1}}]
      [tags/editor {:tags (:tags @form) :suggestions suggestions
                    :on-change #(swap! form assoc :tags %)}]
      (when on-delete
        [:> rn/view {:style {:align-items "flex-start" :padding-top 8}}
         [frame/text-button {:label "Delete note" :color (t/color :bg-orange-bright)
                             :on-press on-delete :test-id "delete-note"}]])]
     [modals/prompt {:visible? @new-book-prompt? :title "New book" :placeholder "Book title"
                     :submit-label "Use"
                     :on-submit (fn [title] (swap! form assoc :new-book-title title :book nil)
                                  (reset! new-book-prompt? false))
                     :on-cancel #(reset! new-book-prompt? false)}]]))

(defn- tag-names [] (map :tag @(rf/subscribe [:tags/index])))

(defn edit-note [{book :book slug :note}]
  (r/with-let [current @(rf/subscribe [:library/note book slug])
               form    (r/atom {:book book :title (or (:title current) "") :text (or (:text current) "")
                                :tags (vec (:tags current))})]
    (let [books @(rf/subscribe [:library/books])
          save! (fn []
                  (let [{:keys [title text tags new-book-title] target :book} @form]
                    (rf/dispatch [:editor/update-note book slug {:title title :text text :tags tags}
                                  (cond-> {}
                                    new-book-title (assoc :new-book-title new-book-title)
                                    (and target (not= target book)) (assoc :target-book target))])))]
      [frame/screen-frame
       [:<>
        [frame/header {:left :cancel :on-left #(rf/dispatch [:nav/go-back])
                       :title "Edit Note" :title-style :modal
                       :action {:label "Save" :variant :commit :on-press save!}}]
        (if current
          [form-view {:form form :books books :suggestions (tag-names)
                      :on-delete #(rn/confirm! {:title "Delete this note?"
                                                :message "It will be removed from Dropbox."
                                                :confirm-label "Delete" :destructive? true
                                                :on-confirm (fn [] (rf/dispatch [:editor/delete-note book slug]))})}]
          [feedback/not-found {:action {:label "Back" :on-press #(rf/dispatch [:nav/go-back])}}])]])))

(defn new-note [{book :book}]
  (r/with-let [default @(rf/subscribe [:editor/default-book])
               form    (r/atom {:book (or book default) :title "" :text "" :tags []})]
    (let [books (rf/subscribe [:library/books])
          ready? (or (:book @form) (seq (:new-book-title @form)))
          save! (fn []
                  (let [{:keys [title text tags new-book-title] target :book} @form]
                    (rf/dispatch [:editor/create-note (cond-> {:title title :text text :tags tags}
                                                        new-book-title (assoc :new-book-title new-book-title)
                                                        (not new-book-title) (assoc :book target))])))]
      [frame/screen-frame
       [:<>
        [frame/header {:left :cancel :on-left #(rf/dispatch [:nav/go-back])
                       :title "New Note" :title-style :modal
                       :action {:label "Add note" :on-press save! :disabled? (not ready?)}}]
        [form-view {:form form :books @books :suggestions (tag-names)
                    :title-placeholder "Enter note title..."
                    :text-placeholder "Add note content here..."}]]])))
