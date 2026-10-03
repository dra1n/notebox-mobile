(ns notebox.ui.root
  "The :notebox/ui component: the root view of the app."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            ["react-native" :as rn]))

(defn root []
  (let [status  @(rf/subscribe [:app/status])
        profile @(rf/subscribe [:app/profile])]
    [:> rn/View {:style {:flex 1
                         :align-items "center"
                         :justify-content "center"}}
     [:> rn/Text {:style {:font-size 24}} "Notebox"]
     [:> rn/Text {:testID "app-status"}
      (str "Status: " (name status) " (" (name profile) ")")]]))

(defmethod ig/init-key :notebox/ui [_ _]
  ;; Look `root` up at render time, so a Krell hot reload of this namespace shows
  ;; up without restarting the system.
  (fn [] [root]))
