(ns notebox.ui.components.frame
  "The frame of every screen: the dark status/header area, the header with
  its left control, title and action button, and buttons. Presentational:
  props in, hiccup out (roadmap §8.3)."
  (:require [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn logo
  "The NoteBox wordmark: on dark backgrounds \"Box\" is light grey."
  [{:keys [height on-dark?]}]
  (let [h (or height 28)]
    [svg/svg {:icon :logo
              :size [(* h (/ 159 59)) h]
              :colors (when on-dark? {"#A4A0A2" (t/color :text-grey-light)})
              :test-id "logo"}]))

(defn screen-frame
  "Safe area with the dark status bar region above `content` (a hiccup)."
  [content]
  [:> rn/safe-area-view {:edges #js ["top"]
                         :style {:flex 1 :background-color (t/color :bg-dark)}}
   [:> rn/status-bar {:bar-style "light-content"}]
   [:> rn/view {:style {:flex 1 :background-color (t/color :bg-lighter)}}
    content]])

(defn button
  "A primary button. variant :primary (cyan-light) or :commit (darker teal)."
  [{:keys [label on-press variant disabled? test-id]}]
  [:> rn/pressable {:on-press (when-not disabled? on-press)
                    :testID test-id
                    :accessibility-role "button"
                    :style {:height 28 :padding-horizontal 12 :justify-content "center"
                            :border-radius (:button t/radius)
                            :opacity (if disabled? 0.5 1)
                            :background-color (t/color (if (= :commit variant) :logo :cyan-light))}}
   [:> rn/text {:style (merge (t/font :medium 14) {:letter-spacing 0.6})}
    (.toUpperCase (str label))]])

(defn text-button
  "A plain text control (Cancel, Rename, Back)."
  [{:keys [label on-press color test-id size]}]
  [:> rn/pressable {:on-press on-press :testID test-id :hit-slop 8 :accessibility-role "button"}
   [:> rn/text {:style (merge (t/font :regular (or size 14))
                              {:color (or color (t/color :text-grey-light))})}
    label]])

(defn- left-control [{:keys [left on-left]}]
  (case left
    :menu   [:> rn/pressable {:on-press on-left :testID "header-menu" :hit-slop 8
                              :accessibility-label "Menu"}
             [svg/svg {:icon :hamburger :size [23 19] :colors {"#C6C6C6" (t/color :text-grey-light)}}]]
    :back   [:> rn/pressable {:on-press on-left :testID "header-back" :hit-slop 8
                              :accessibility-label "Back"
                              :style {:flex-direction "row" :align-items "center" :gap 8}}
             [svg/svg {:icon :arrow-left :size [20 20]}]
             [:> rn/text {:style (merge (t/font :regular 14) {:color (t/color :text-grey-light)})}
              "Back"]]
    :cancel [text-button {:label "Cancel" :on-press on-left :test-id "header-cancel"}]
    nil))

(defn header
  "The dark header: left control (:menu :back :cancel), optional logo, a title
  (:plain: grey, truncated, centered; :modal: bold white, centered; :section:
  bold white next to the menu, as on Books and Tags) and an action button."
  [{:keys [logo? title title-style action] :as props}]
  [:> rn/view {:style {:height t/header-height :background-color (t/color :bg-dark)
                       :padding-horizontal (t/sp :m) :flex-direction "row"
                       :align-items "center" :justify-content "space-between"}}
   ;; the gap after the left control equals the header's side padding (16)
   [:> rn/view {:style {:flex-direction "row" :align-items "center" :gap (t/sp :m) :min-width 72}}
    [left-control props]
    (when logo? [logo {:height 26 :on-dark? true}])
    (when (= :section title-style)
      [:> rn/text {:testID "header-title" :style (merge (t/font :bold 17) {:color (t/color :white)})}
       title])]
   (when (and title (not= :section title-style))
     [:> rn/text {:number-of-lines 1
                  :testID "header-title"
                  :style (merge (if (= :modal title-style)
                                  (merge (t/font :bold 14) {:color (t/color :white)})
                                  (merge (t/font :regular 14) {:color (t/color :text-grey-light)}))
                                {:max-width 160 :flex-shrink 1 :text-align "center"})}
      title])
   [:> rn/view {:style {:min-width 72 :align-items "flex-end"}}
    (when action [button (merge {:test-id "header-action"} action)])]])
