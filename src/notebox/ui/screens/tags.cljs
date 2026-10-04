(ns notebox.ui.screens.tags
  "Every tag with its note count (spec/design/screens/11, read-only in v1).
  Tapping a tag searches for it on the home screen."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.lists :as lists]
            [notebox.ui.components.tags :as tags]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.state :as state]))

(defn tags-screen [_]
  (r/with-let [_ (rf/dispatch [:tags/load-counts])]
    (let [index @(rf/subscribe [:tags/index])
          search! (fn [tag] (rf/dispatch [:search/set-query tag {:type :all} {:external? true}])
                    (rf/dispatch [:nav/go :books-home {}]))]
      [frame/screen-frame
       [:<>
        [frame/header {:left :menu :on-left state/open-menu! :title "Tags" :title-style :section}]
        (if (empty? index)
          [feedback/empty-state {:title "No tags yet" :text "Add tags to your notes in the editor."}]
          (into [:> rn/scroll-view {:content-container-style {:padding 16 :gap 12}}
                 [lists/count-line (str (lists/plural (count index) "tag") " in total")]]
                (for [{:keys [tag count complete?]} index]
                  ^{:key tag}
                  [lists/card {:badge [tags/chip {:label tag :on-press #(search! tag)}]
                               :subtitle (if (and (not complete?) (zero? count))
                                           "counting…"
                                           (str (lists/plural count "note") (when-not complete? "…")))
                               :test-id (str "tag-card-" tag)
                               :on-press #(search! tag)}])))]])))
