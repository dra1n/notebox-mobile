(ns notebox.ui.components.feedback
  "Toasts, the sync pill, loading and empty states, \"note not found\"
  (the design gaps, taken from the desktop frames, roadmap §8.1)."
  (:require [notebox.ui.components.frame :as frame]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn toast [{:keys [message on-dismiss]}]
  (let [error? (= :error (:type message))]
    [:> rn/pressable {:on-press #(on-dismiss (:id message))
                      :testID (if error? "toast-error" "toast-notice")
                      :style {:flex-direction "row" :align-items "center" :gap 10
                              :padding-horizontal 14 :padding-vertical 10 :border-radius 4
                              :background-color (t/color (if error? :bg-orange-light :cyan-lightest))}}
     (if error?
       [:> rn/text {:style (merge (t/font :bold 14) {:color (t/color :bg-orange-bright)})} "!"]
       [svg/svg {:icon :check :size [17 17]}])
     [:> rn/text {:style (merge (t/font :regular 13) {:flex-shrink 1})} (:text message)]]))

(defn toasts
  "The toasts, stacked at the top. Tap to dismiss."
  [{:keys [messages on-dismiss top]}]
  (when (seq messages)
    ;; below the status bar and the header, so they never cover header buttons
    (into [:> rn/view {:pointer-events "box-none"
                       :style {:position "absolute" :left 16 :right 16 :top (or top 132) :gap 8}}]
          (for [m messages] ^{:key (:id m)} [toast {:message m :on-dismiss on-dismiss}]))))

(defn sync-pill
  "\"Saving…\" while changes are on their way to Dropbox."
  [{:keys [visible?]}]
  (when visible?
    [:> rn/view {:testID "sync-pill"
                 :style {:position "absolute" :right 16 :bottom 24 :flex-direction "row" :gap 6
                         :align-items "center" :padding-horizontal 10 :padding-vertical 5
                         :border-radius 12 :background-color (t/color :bg-light)}}
     [:> rn/activity-indicator {:size "small" :color (t/color :text-grey-dark)}]
     [:> rn/text {:style (t/font :regular 12)} "Saving…"]]))

(defn loading [{:keys [label]}]
  [:> rn/view {:testID "loading" :style {:flex 1 :align-items "center" :justify-content "center" :gap 8}}
   [:> rn/activity-indicator {:color (t/color :text-grey-dark)}]
   (when label [:> rn/text {:style (merge (t/font :regular 13) {:color (t/color :text-grey)})} label])])

(defn empty-state
  "An illustration, a title, a line of text and an optional button."
  [{:keys [illustration title text action test-id]}]
  [:> rn/view {:testID (or test-id "empty-state")
               :style {:flex 1 :align-items "center" :justify-content "center" :padding 32 :gap 12}}
   [svg/svg {:icon (or illustration :illustration-empty) :size [104 103]}]
   [:> rn/text {:style (t/font :medium 20)} title]
   (when text
     [:> rn/text {:style (merge (t/font :regular 14) {:color (t/color :text-grey-dark)
                                                      :text-align "center"})}
      text])
   (when action [frame/button action])])

(defn not-found [{:keys [action]}]
  [empty-state {:illustration :illustration-404 :test-id "not-found"
                :title "Note not found"
                :text "This note doesn't exist any more. It may have been deleted on another device."
                :action action}])
