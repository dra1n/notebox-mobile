(ns notebox.infra.kv-store
  "Small per-device settings (the default book). Values are EDN; calls return
  Promises. :memory is for tests; :async-storage is registered by
  notebox.infra.rn.async-storage, which only the app loads."
  (:require [cljs.reader :as reader]
            [integrant.core :as ig]))

(defprotocol KvStore
  (get-raw [this k] "Promise of the string under `k`, or nil.")
  (set-raw! [this k s] "Promise; stores string `s` under `k`.")
  (remove! [this k] "Promise; removes `k`."))

(defn get-value
  "Promise of the EDN value under keyword `k`, or nil."
  [store k]
  (.then (get-raw store (str (symbol k))) #(some-> % reader/read-string)))

(defn set-value!
  "Stores `v` (EDN) under keyword `k`; nil removes it."
  [store k v]
  (if (nil? v)
    (remove! store (str (symbol k)))
    (set-raw! store (str (symbol k)) (pr-str v))))

(defrecord MemoryKv [state]
  KvStore
  (get-raw [_ k] (js/Promise.resolve (get @state k)))
  (set-raw! [_ k s] (swap! state assoc k s) (js/Promise.resolve nil))
  (remove! [_ k] (swap! state dissoc k) (js/Promise.resolve nil)))

(defn memory-kv
  ([] (memory-kv {}))
  ([initial] (->MemoryKv (atom initial))))

(defmulti create :impl)

(defmethod create :memory [{:keys [initial]}] (memory-kv (or initial {})))

(defmethod ig/init-key :notebox.infra/kv-store [_ opts]
  (create opts))
