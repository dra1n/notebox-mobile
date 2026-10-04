(ns notebox.ui.screens.start
  "Splash (session unknown) and Start (signed out): spec/design/screens/01, 02."
  (:require [re-frame.core :as rf]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.rn.images :as images]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn splash [_]
  [:> rn/view {:testID "splash" :style {:flex 1 :align-items "center" :justify-content "center"
                                        :background-color (t/color :bg-lighter)}}
   [frame/logo {:height 59}]])

(defn start [_]
  (let [status @(rf/subscribe [:auth/status])
        signing-in? (= :signing-in status)]
    [:> rn/image-background {:source images/start-background :resize-mode "cover"
                             :testID "start" :style {:flex 1}}
     [:> rn/status-bar {:bar-style "dark-content"}]
     [svg/vertical-fade {:color (t/color :bg-lighter)}]
     [:> rn/view {:style {:align-items "center" :padding-top 96 :gap 12 :padding-horizontal 32}}
      [frame/logo {:height 59}]
      [:> rn/text {:style (merge (t/font :medium 16) {:margin-top 24})} "Your personal notebook in Dropbox"]
      [:> rn/text {:style (merge (t/font :regular 14) {:color (t/color :text-grey-dark) :text-align "center"})}
       "Keep all your notes in one place.\nOrganize them in books."]]
     [:> rn/view {:style {:margin-horizontal 44 :margin-top 48 :padding 28 :gap 20 :align-items "center"
                          :background-color (t/color :white)
                          :shadow-color "#000" :shadow-opacity 0.12 :shadow-radius 8
                          :shadow-offset {:width 0 :height 2}}}
      [:> rn/text {:style (merge (t/font :regular 15) {:text-align "center"})}
       "Login with your Dropbox account to get started"]
      [frame/button {:label (if signing-in? "Waiting for Dropbox…" "Let's go")
                     :disabled? signing-in?
                     :on-press #(rf/dispatch [:app/login])
                     :test-id "login"}]]]))
