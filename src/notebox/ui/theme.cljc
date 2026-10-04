(ns notebox.ui.theme
  "Design tokens (roadmap §8.4, measured in spec/design/README.md). Light only.")

(def colors
  {:cyan             "#3CB0BD"
   :cyan-dark        "#8ED6DE"
   :cyan-light       "#ADE4EA"
   :cyan-lightest    "#D9F5F8"
   :logo             "#6CC5CF"
   :text             "#323232"
   :text-grey-dark   "#696468"
   :text-grey        "#888888"
   :text-grey-slight "#AFAFAF"
   :text-grey-light  "#C6C6C6"
   :bg-dark          "#2C292B"
   :bg-medium        "#696468"
   :bg-light         "#DFDFDF"
   :bg-lighter       "#F6F6F6"
   :bg-orange-light  "#FFE7DC"
   :bg-orange-bright "#FF6D26"
   :white            "#FFFFFF"})

(defn color [k] (get colors k))

(def space {:xxs 4 :xs 8 :s 12 :m 16 :l 20 :xl 24})
(defn sp [k] (get space k))

(def font-family
  "nil = the platform font: SF Pro on iOS, Roboto on Android (roadmap §11:
  bundling Roboto on iOS wasn't simple, so the agreed fallback applies)."
  nil)

(defn font
  "Text style for `weight` #{:regular :medium :semibold :bold} and `size`."
  [weight size]
  (cond-> {:font-size size
           :font-weight (case weight :regular "400" :medium "500" :semibold "600" :bold "700")
           :color (color :text)}
    font-family (assoc :font-family font-family)))

(def header-height 64)
(def radius {:button 3 :chip 4 :card 6 :input 8})
