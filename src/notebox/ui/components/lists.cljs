(ns notebox.ui.components.lists
  "Search bar, stats line, and the rows and cards of the lists (books, notes,
  books manage, tags)."
  (:require [clojure.string :as str]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.rn.svg :as svg]
            [notebox.ui.theme :as t]))

(defn search-bar
  "The white search-and-stats block: an input and a stats line."
  [{:keys [value placeholder on-change stats test-id]}]
  [:> rn/view {:style {:background-color (t/color :white) :padding (t/sp :m) :gap (t/sp :s)
                       :border-bottom-width 1 :border-bottom-color (t/color :bg-light)}}
   [:> rn/view {:style {:flex-direction "row" :align-items "center" :gap 8
                        :background-color (t/color :bg-lighter) :border-radius (:input t/radius)
                        :padding-horizontal 12 :padding-vertical 10}}
    [svg/svg {:icon :search :size [16 16]}]
    [:> rn/text-input {:value (or value "")
                       :placeholder placeholder
                       :placeholder-text-color (t/color :text-grey)
                       :on-change-text on-change
                       :auto-correct false
                       :auto-capitalize "none"
                       :clear-button-mode "while-editing"
                       :testID (or test-id "search-input")
                       :style (merge (t/font :regular 14) {:flex 1 :padding 0})}]]
   (when stats
     [:> rn/text {:testID "stats" :style (t/font :medium 14)} stats])])

(defn plural [n word] (str n " " word (when (not= 1 n) "s")))

(defn- separator []
  [:> rn/view {:style {:height 1 :background-color (t/color :bg-light)}}])

(defn book-row
  "A book in the home list: icon, title, \"N notes\"."
  [{:keys [book on-press]}]
  [:<>
   [:> rn/pressable {:on-press on-press :testID (str "book-" (:slug book))
                     :style (fn [^js s] #js {:padding 16 :flexDirection "row" :gap 12
                                             :backgroundColor (if (.-pressed s) (t/color :bg-light)
                                                                  (t/color :bg-lighter))})}
    [svg/svg {:icon :book :size [12 14]}]
    [:> rn/view {:style {:flex 1 :gap 6}}
     [:> rn/text {:style (t/font :regular 14) :number-of-lines 1} (:title book)]
     [:> rn/text {:style (t/font :regular 12)} (plural (or (:count book) 0) "note")]]]
   [separator]])

(defn note-preview
  "The first two lines of a note's text, or the design's placeholder."
  [text]
  (if (str/blank? text) "No additional text" text))

(defn note-row
  "A note in a book's list: title (or \"No title\") and a two-line preview."
  [{:keys [note subtitle on-press]}]
  [:<>
   [:> rn/pressable {:on-press on-press :testID (str "note-" (:slug note))
                     :style (fn [^js s] #js {:padding 16 :gap 6
                                             :backgroundColor (if (.-pressed s) (t/color :bg-light)
                                                                  (t/color :bg-lighter))})}
    (when subtitle
      [:> rn/text {:style (merge (t/font :regular 12) {:color (t/color :text-grey)})} subtitle])
    [:> rn/text {:style (t/font :semibold 14) :number-of-lines 1}
     (if (str/blank? (:title note)) "No title" (:title note))]
    [:> rn/text {:style (merge (t/font :regular 13) {:color (t/color :text-grey-dark)})
                 :number-of-lines 2}
     (note-preview (:text note))]]
   [separator]])

(defn card
  "A card on the Books and Tags screens. `highlighted?`: the default book."
  [{:keys [title badge subtitle highlighted? action-label on-action on-press test-id]}]
  [:> rn/pressable {:on-press on-press :testID test-id
                    :style {:flex-direction "row" :align-items "center" :padding 12
                            :border-radius (:card t/radius) :border-width 1
                            :border-color (t/color (if highlighted? :cyan :bg-light))
                            :background-color (t/color (if highlighted? :cyan-lightest :bg-lighter))}}
   [:> rn/view {:style {:flex 1 :gap 4 :flex-direction (if badge "row" "column")
                        :align-items (if badge "center" "flex-start")}}
    (when title [:> rn/text {:style (t/font :medium 14) :number-of-lines 1} title])
    badge
    (when subtitle
      [:> rn/text {:style (merge (t/font :regular 12) {:color (t/color :text-grey)
                                                       :margin-left (if badge 8 0)})}
       subtitle])]
   (when action-label
     [:> rn/pressable {:on-press on-action :hit-slop 8 :testID (some-> test-id (str "-action"))}
      [:> rn/text {:style (merge (t/font :regular 14) {:color (t/color :text-grey)})} action-label]])])

(defn count-line
  "The grey count above the cards (\"7 Books\", \"5 tags in total\")."
  [text]
  [:> rn/text {:testID "stats" :style (merge (t/font :regular 14) {:color (t/color :text-grey)})}
   text])
