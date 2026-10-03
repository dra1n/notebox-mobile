(ns notebox.fx.settings
  "Per-device settings in the kv-store:

    {:settings/load {:keys [:default-book] :on-ok [...]}}  ; + {k v}
    {:settings/save {:key :default-book :value v}}"
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.fx.util :as util]
            [notebox.infra.kv-store :as kv]))

(def fx-ids [:settings/load :settings/save])

(defmethod ig/init-key :notebox.fx/settings [_ {:keys [kv-store]}]
  (let [alive (util/alive)]
    (rf/reg-fx :settings/load
               (fn [{ks :keys :as cbs}]
                 (util/dispatch-result
                  alive
                  (.then (js/Promise.all (into-array (map #(kv/get-value kv-store %) ks)))
                         #(zipmap ks %))
                  cbs)))
    (rf/reg-fx :settings/save
               (fn [{:keys [key value]}] (kv/set-value! kv-store key value)))
    {:kv-store kv-store :alive alive}))

(defmethod ig/halt-key! :notebox.fx/settings [_ {:keys [alive]}]
  (reset! alive false)
  (util/clear-fx! fx-ids))
