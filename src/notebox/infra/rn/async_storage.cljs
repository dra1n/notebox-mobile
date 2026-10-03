(ns notebox.infra.rn.async-storage
  "The :async-storage kv-store: @react-native-async-storage/async-storage v3,
  in its own database (`name`)."
  (:require ["@react-native-async-storage/async-storage" :refer [createAsyncStorage]]
            [notebox.infra.kv-store :as kv]))

(defrecord AsyncStorageKv [^js storage]
  kv/KvStore
  (get-raw [_ k] (.getItem storage k))
  (set-raw! [_ k s] (.setItem storage k s))
  (remove! [_ k] (.removeItem storage k)))

(defmethod kv/create :async-storage [{:keys [name]}]
  (->AsyncStorageKv (createAsyncStorage (or name "notebox"))))
