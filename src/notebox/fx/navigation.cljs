(ns notebox.fx.navigation
  "Navigation as data (roadmap §5.2 rule 4):

    {:nav/navigate [route params]}  {:nav/back nil}

  The component holds a `navigator` slot that the UI fills (Phase 5) with a
  function (fn [action route params]); until then navigation is a no-op."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.fx.util :as util]))

(def fx-ids [:nav/navigate :nav/back])

(defmethod ig/init-key :notebox.fx/navigation [_ _]
  (let [navigator (atom nil)]
    (rf/reg-fx :nav/navigate (fn [[route params]] (some-> @navigator (apply [:navigate route params]))))
    (rf/reg-fx :nav/back (fn [_] (some-> @navigator (apply [:back nil nil]))))
    {:navigator navigator}))

(defmethod ig/halt-key! :notebox.fx/navigation [_ _]
  (util/clear-fx! fx-ids))
