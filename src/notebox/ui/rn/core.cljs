(ns notebox.ui.rn.core
  "React Native for the UI: the primitives (use as [:> rn/view props ...]),
  safe areas, and the system confirm dialog. One of the few namespaces that
  require JS modules (roadmap §5.2 item 7).

  Prop names: Reagent camel-cases kebab-case keys (:on-press → onPress), but
  write :testID literally (:test-id would become testId)."
  (:require ["react-native" :as rn]
            ["react-native-safe-area-context" :as safe-area]))

(def view rn/View)
(def text rn/Text)
(def text-input rn/TextInput)
(def pressable rn/Pressable)
(def scroll-view rn/ScrollView)
(def flat-list rn/FlatList)
(def image rn/Image)
(def image-background rn/ImageBackground)
(def modal rn/Modal)
(def activity-indicator rn/ActivityIndicator)
(def status-bar rn/StatusBar)
(def keyboard-avoiding-view rn/KeyboardAvoidingView)
(def refresh-control rn/RefreshControl)

(def safe-area-provider safe-area/SafeAreaProvider)
(def safe-area-view safe-area/SafeAreaView)

(def ios? (= "ios" (.-OS rn/Platform)))

(defn choose!
  "The system's action sheet-like dialog: `options` [{:label :on-press
  :destructive?}], plus Cancel."
  [{:keys [title message options]}]
  (.alert rn/Alert title message
          (clj->js (conj (vec (for [{:keys [label on-press destructive?]} options]
                                {:text label :onPress on-press
                                 :style (if destructive? "destructive" "default")}))
                         {:text "Cancel" :style "cancel"}))))

(defn confirm!
  "The system's two-button dialog; calls `on-confirm` if the user agrees."
  [{:keys [title message confirm-label destructive? on-confirm]}]
  (.alert rn/Alert title message
          #js [#js {:text "Cancel" :style "cancel"}
               #js {:text (or confirm-label "OK")
                    :style (if destructive? "destructive" "default")
                    :onPress on-confirm}]))
