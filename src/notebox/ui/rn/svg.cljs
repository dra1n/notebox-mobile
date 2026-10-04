(ns notebox.ui.rn.svg
  "Renders the SVG data of notebox.ui.icons with react-native-svg."
  (:require ["react-native-svg" :as rsvg]
            [notebox.ui.icons :as icons]))

(def ^:private elements
  {:g rsvg/G :path rsvg/Path :rect rsvg/Rect :line rsvg/Line :circle rsvg/Circle})

(defn- recolor [attrs colors]
  (cond-> attrs
    (contains? colors (:fill attrs))   (update :fill colors)
    (contains? colors (:stroke attrs)) (update :stroke colors)))

(defn- render [[tag attrs & children] colors]
  (into [:> (elements tag) (recolor attrs colors)]
        (map #(render % colors) children)))

(defn vertical-fade
  "A rectangle filling its parent that fades `color` from opaque at the top to
  transparent at the bottom (the Start screen's white fade over the photo)."
  [{:keys [color]}]
  [:> rsvg/Svg {:width "100%" :height "100%" :style {:position "absolute"}}
   [:> rsvg/Defs
    [:> rsvg/LinearGradient {:id "fade" :x1 "0" :y1 "0" :x2 "0" :y2 "1"}
     [:> rsvg/Stop {:offset "0" :stopColor color :stopOpacity 1}]
     [:> rsvg/Stop {:offset "0.55" :stopColor color :stopOpacity 0.85}]
     [:> rsvg/Stop {:offset "1" :stopColor color :stopOpacity 0}]]]
   [:> rsvg/Rect {:x 0 :y 0 :width "100%" :height "100%" :fill "url(#fade)"}]])

(defn svg
  "An icon by name (see notebox.ui.icons). `size` [w h] scales it; `colors`
  maps colors in the design to the ones to draw (e.g. {\"#C6C6C6\" \"#888888\"})."
  [{:keys [icon size colors test-id]}]
  (let [{:keys [width height view-box children]} (get icons/icons icon)
        [w h] (or size [width height])]
    (into [:> rsvg/Svg {:width w :height h :viewBox view-box :testID test-id}]
          (map #(render % (or colors {})) children))))
