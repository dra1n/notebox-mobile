(ns notebox.ui.components.tags
  "Tag chips, and the editor's chip list with \"+ Add tag\" and suggestions."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn chip
  "A tag chip; with `on-remove` it has a ×."
  [{:keys [label on-remove on-press]}]
  [:> rn/pressable {:on-press on-press :testID (str "tag-" label)
                    :style {:flex-direction "row" :align-items "center" :gap 6
                            :padding-horizontal 10 :padding-vertical 6
                            :border-radius (:chip t/radius) :background-color (t/color :cyan-light)}}
   [:> rn/text {:style (t/font :medium 13)} label]
   (when on-remove
     [:> rn/pressable {:on-press on-remove :hit-slop 8 :testID (str "tag-remove-" label)
                       :accessibility-label (str "Remove " label)}
      [svg/svg {:icon :close-x :size [9 9]}]])])

(defn chips
  "Read-only chips, wrapping."
  [{:keys [tags on-press]}]
  (into [:> rn/view {:style {:flex-direction "row" :flex-wrap "wrap" :gap 8}}]
        (for [tag tags] ^{:key tag} [chip {:label tag :on-press (when on-press #(on-press tag))}])))

(defn matching-suggestions
  "Up to 5 known tags that start with or contain `input`, not already chosen."
  [suggestions chosen input]
  (let [q (str/lower-case (str/trim (or input "")))]
    (when (seq q)
      (->> suggestions
           (remove (set chosen))
           (filter #(str/includes? (str/lower-case %) q))
           (sort-by #(if (str/starts-with? (str/lower-case %) q) 0 1))
           (take 5)))))

(defn editor
  "The editor's tags: chips with ×, then \"+ Add tag\" that turns into an input
  with suggestions from every known tag. New tags can be typed too."
  [_]
  (let [adding? (r/atom false)
        input   (r/atom "")]
    (fn [{:keys [tags suggestions on-change placeholder]}]
      (let [add! (fn [tag]
                   (let [tag (str/trim tag)]
                     (when (and (seq tag) (not (some #{tag} tags)))
                       (on-change (conj (vec tags) tag))))
                   (reset! input "")
                   (reset! adding? false))]
        [:> rn/view {:style {:gap 8}}
         (into [:> rn/view {:style {:flex-direction "row" :flex-wrap "wrap" :gap 8}}]
               (concat
                (for [tag tags]
                  ^{:key tag} [chip {:label tag :on-remove #(on-change (vec (remove #{tag} tags)))}])
                [(if @adding?
                   ^{:key "input"}
                   [:> rn/text-input {:value @input :auto-focus true :auto-capitalize "none"
                                      :placeholder "Tag" :testID "tag-input"
                                      :on-change-text #(reset! input %)
                                      :on-submit-editing #(add! @input)
                                      :on-blur #(add! @input)
                                      :style (merge (t/font :regular 13)
                                                    {:min-width 90 :padding-horizontal 10 :padding-vertical 5
                                                     :border-width 1 :border-color (t/color :text-grey)
                                                     :border-radius (:chip t/radius)})}]
                   ^{:key "add"}
                   [:> rn/pressable {:on-press #(reset! adding? true) :testID "tag-add"
                                     :style {:padding-horizontal 10 :padding-vertical 6 :border-width 1
                                             :border-color (t/color :text-grey) :border-radius (:chip t/radius)}}
                    [:> rn/text {:style (merge (t/font :regular 13) {:color (t/color :text-grey)})}
                     (or placeholder "+ Add tag")]])]))
         (when-let [ss (seq (matching-suggestions suggestions tags @input))]
           (into [:> rn/view {:style {:flex-direction "row" :flex-wrap "wrap" :gap 8}}]
                 (for [s ss]
                   ^{:key s}
                   [:> rn/pressable {:on-press #(add! s) :testID (str "suggestion-" s)
                                     :style {:padding-horizontal 10 :padding-vertical 6
                                             :border-radius (:chip t/radius)
                                             :background-color (t/color :cyan-lightest)}}
                    [:> rn/text {:style (t/font :regular 13)} s]])))]))))
