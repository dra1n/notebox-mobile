(ns notebox.core
  (:require [reagent.core :as r]
            ["react-native" :as rn]))

(defn hello []
  [:> rn/View {:style {:flex 1
                       :align-items "center"
                       :justify-content "center"}}
   [:> rn/Text {:style {:font-size 24}} "Hello from Notebox"]])

(defn ^:export -main [& _args]
  (r/as-element [hello]))
