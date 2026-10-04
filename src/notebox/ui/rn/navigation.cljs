(ns notebox.ui.rn.navigation
  "React Navigation 7 (native stack) for the UI: the container, the stack, and
  the navigator function the :notebox/ui component installs. Route params
  arrive in screens as ClojureScript maps."
  (:require ["@react-navigation/native" :as nav]
            ["@react-navigation/native-stack" :refer [createNativeStackNavigator]]
            [reagent.core :as r]))

(def form-routes
  "Screens that a save leaves: they're replaced by the result, not kept below it."
  #{"note-new" "note-edit"})

(defn create-ref [] (nav/createNavigationContainerRef))

(defn navigator-fn
  "What notebox.infra.navigator calls: (f action route params).
  :navigate goes back to the route if it's already in the stack (popTo),
  replaces a form screen (a saved editor shows the note, and Back skips the
  form), and otherwise pushes. :back pops."
  [^js nav-ref]
  (fn [action route params]
    (when (.isReady nav-ref)
      (case action
        :navigate (let [route  (name route)
                        state  (.getRootState nav-ref)
                        names  (set (map #(.-name ^js %) (.-routes state)))
                        params (clj->js params)]
                    (cond
                      (contains? names route)
                      (.dispatch nav-ref (.popTo nav/StackActions route params))

                      (contains? form-routes (.-name (.getCurrentRoute nav-ref)))
                      (.dispatch nav-ref (.replace nav/StackActions route params))

                      :else (.navigate nav-ref route params)))
        :back     (when (.canGoBack nav-ref) (.goBack nav-ref))))))

(defonce ^:private Stack (createNativeStackNavigator))

(def ^:private screen-component
  "A React component per screen fn, made once so React keeps its identity."
  (memoize
   (fn [screen-fn]
     (r/reactify-component
      (fn [{:keys [route]}]
        [screen-fn (js->clj (.-params ^js route) :keywordize-keys true)])))))

(defn container
  "The navigation container around `child`."
  [nav-ref child]
  [:> nav/NavigationContainer {:ref nav-ref} child])

(defn stack
  "A native stack of `screens` [{:name kw :screen fn :options map}], our own
  headers (the design's), so the native header is off."
  [screens]
  (into [:> (.-Navigator Stack) {:screenOptions #js {:headerShown false}}]
        (for [{:keys [name screen options]} screens]
          [:> (.-Screen Stack) {:key (clojure.core/name name)
                                :name (clojure.core/name name)
                                :component (screen-component screen)
                                :options (clj->js (or options {}))}])))
