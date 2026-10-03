(ns notebox.fx.settings
  "Effect handlers for per-device settings in the kv-store:

    {:settings/load {:keys [:default-book] :on-ok [...]}}  ; + {k v}
    {:settings/save {:key :default-book :value v}}

  Plain functions; notebox.shell.effects registers them."
  (:require [notebox.fx.util :as util]
            [notebox.infra.kv-store :as kv]))

(defn effects
  "{fx-id handler} over `kv-store`."
  [{:keys [kv-store alive]}]
  {:settings/load (fn [{ks :keys :as cbs}]
                    (util/dispatch-result
                     alive
                     (.then (js/Promise.all (into-array (map #(kv/get-value kv-store %) ks)))
                            #(zipmap ks %))
                     cbs))
   :settings/save (fn [{:keys [key value]}] (kv/set-value! kv-store key value))})
