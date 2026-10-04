(ns notebox.ui.components.modals
  "Things that open over a screen: the side menu (§8.1, `0:1134`), the book
  picker of the editor, and a one-line text prompt (new book, rename)."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn- menu-item [{:keys [label on-press active? test-id]}]
  [:> rn/pressable {:on-press on-press :testID test-id
                    :style {:padding-horizontal 24 :padding-vertical 14
                            :background-color (if active? (t/color :bg-medium) "transparent")}}
   [:> rn/text {:style (merge (t/font :regular 14) {:color (t/color :white)})} label]])

(defn side-menu
  "The dark sheet: logo, account, menu entries, log out."
  [{:keys [visible? account active items on-close on-logout]}]
  [:> rn/modal {:visible (boolean visible?) :transparent true :animation-type "fade"
                :on-request-close on-close}
   [:> rn/view {:style {:flex 1 :flex-direction "row"}}
    [:> rn/view {:testID "side-menu"
                 :style {:width 260 :background-color (t/color :bg-dark) :padding-top 60}}
     [:> rn/view {:style {:align-items "center" :padding-bottom 12 :border-bottom-width 1
                          :border-bottom-color (t/color :text-grey-dark)}}
      [frame/logo {:height 34 :on-dark? true}]]
     [:> rn/view {:style {:padding-horizontal 24 :padding-vertical 12 :gap 2 :border-bottom-width 1
                          :border-bottom-color (t/color :text-grey-dark)}}
      [:> rn/text {:testID "account-email"
                   :style (merge (t/font :medium 13) {:color (t/color :white)})}
       (or (:email account) " ")]
      [:> rn/view {:style {:flex-direction "row" :gap 4 :align-items "center"}}
       [svg/svg {:icon :dropbox :size [12 10]}]
       [:> rn/text {:style (merge (t/font :regular 11) {:color (t/color :text-grey)})}
        "dropbox account"]]]
     (into [:> rn/view {:style {:padding-vertical 8}}]
           (for [{:keys [id label on-press]} items]
             ^{:key id} [menu-item {:label label :on-press on-press :active? (= id active)
                                    :test-id (str "menu-" (name id))}]))
     [:> rn/view {:style {:flex 1}}]
     [:> rn/view {:style {:border-top-width 1 :border-top-color (t/color :text-grey-dark)
                          :padding-bottom 32}}
      [menu-item {:label "Log out" :on-press on-logout :test-id "menu-logout"}]]]
    [:> rn/pressable {:on-press on-close :testID "side-menu-backdrop"
                      :style {:flex 1 :background-color "rgba(0,0,0,0.35)"}}]]])

(defn prompt
  "A small dialog with one text field: OK calls (on-submit text)."
  [_]
  (let [value (r/atom nil)]
    (fn [{:keys [visible? title placeholder initial submit-label on-submit on-cancel]}]
      (let [v (if (nil? @value) (or initial "") @value)
            submit! (fn []
                      (when-not (str/blank? v) (on-submit (str/trim v)))
                      (reset! value nil))
            cancel! (fn [] (reset! value nil) (on-cancel))]
        [:> rn/modal {:visible (boolean visible?) :transparent true :animation-type "fade"
                      :on-request-close cancel!}
         [:> rn/keyboard-avoiding-view {:behavior (when rn/ios? "padding")
                                        :style {:flex 1 :justify-content "center" :padding 32
                                                :background-color "rgba(0,0,0,0.35)"}}
          [:> rn/view {:testID "prompt"
                       :style {:background-color (t/color :white) :border-radius 8 :padding 20 :gap 16}}
           [:> rn/text {:style (t/font :medium 16)} title]
           [:> rn/text-input {:default-value (or initial "") :placeholder placeholder :auto-focus true
                              :testID "prompt-input"
                              :placeholder-text-color (t/color :text-grey)
                              :on-change-text #(reset! value %)
                              :on-submit-editing submit!
                              :style (merge (t/font :regular 15)
                                            {:border-bottom-width 1 :border-bottom-color (t/color :cyan)
                                             :padding-vertical 6})}]
           [:> rn/view {:style {:flex-direction "row" :justify-content "flex-end" :gap 20
                                :align-items "center"}}
            [frame/text-button {:label "Cancel" :on-press cancel! :color (t/color :text-grey)
                                :test-id "prompt-cancel"}]
            [frame/button {:label (or submit-label "OK") :on-press submit! :test-id "prompt-submit"
                           :disabled? (str/blank? v)}]]]]]))))

(defn book-picker
  "The editor's book field and its list: pick a book, or start a new one."
  [_]
  (let [open? (r/atom false)]
    (fn [{:keys [books selected new-book-title placeholder on-select on-new-book]}]
      (let [selected-title (or new-book-title (:title (some #(when (= selected (:slug %)) %) books)))]
        [:<>
         [:> rn/pressable {:on-press #(reset! open? true) :testID "book-picker"
                           :style {:flex-direction "row" :align-items "center" :padding-horizontal 12
                                   :height 36 :border-radius 3 :border-width 1
                                   :border-color (t/color (if selected-title :cyan :bg-light))
                                   :background-color (t/color (if selected-title :cyan-lightest :white))}}
          [:> rn/text {:style (merge (t/font :medium 13) {:flex 1}) :number-of-lines 1}
           (or selected-title placeholder "Select Notebook...")]
          [svg/svg {:icon :chevron-down :size [12 8]}]]
         (when @open?   ; mounted only while open: a fresh native modal each time
         [:> rn/modal {:visible true :transparent true :animation-type "fade"
                       :on-request-close #(reset! open? false)}
          [:> rn/view {:style {:flex 1 :justify-content "center" :padding 32}}
           ;; the backdrop is a sibling behind the list: a pressable parent would hide
           ;; the list's items from accessibility (and from Maestro)
           [:> rn/pressable {:on-press #(reset! open? false) :testID "book-picker-backdrop"
                             :style {:position "absolute" :top 0 :left 0 :right 0 :bottom 0
                                     :background-color "rgba(0,0,0,0.35)"}}]
           [:> rn/view {:testID "book-picker-list"
                        :style {:background-color (t/color :white) :border-radius 8 :max-height 420}}
            (into [:> rn/scroll-view]
                  (concat
                   (for [{:keys [slug title]} books]
                     ^{:key slug}
                     [:> rn/pressable {:on-press (fn [] (reset! open? false) (on-select slug))
                                       :testID (str "pick-" slug)
                                       :style {:padding 14 :border-bottom-width 1
                                               :border-bottom-color (t/color :bg-light)
                                               :background-color (if (= slug selected)
                                                                   (t/color :cyan-lightest)
                                                                   (t/color :white))}}
                      [:> rn/text {:style (t/font :regular 14)} title]])
                   [^{:key "new"}
                    [:> rn/pressable {:on-press (fn [] (reset! open? false) (on-new-book))
                                      :testID "pick-new-book" :style {:padding 14}}
                     [:> rn/text {:style (merge (t/font :medium 14) {:color (t/color :cyan)})}
                      "+ New book…"]]]))]]])]))))
