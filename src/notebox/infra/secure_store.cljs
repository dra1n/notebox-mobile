(ns notebox.infra.secure-store
  "Secrets (the Dropbox refresh token) in Keychain/Keystore. All calls return
  Promises. The :memory implementation is for tests; :keychain is registered by
  notebox.infra.rn.keychain, which only the app loads."
  (:require [integrant.core :as ig]))

(defprotocol SecureStore
  (read-secret [this k] "Promise of the string stored under `k`, or nil.")
  (write-secret! [this k v] "Promise; stores `v` under `k`.")
  (delete-secret! [this k] "Promise; removes `k`."))

(defrecord MemoryStore [state]
  SecureStore
  (read-secret [_ k] (js/Promise.resolve (get @state k)))
  (write-secret! [_ k v] (swap! state assoc k v) (js/Promise.resolve nil))
  (delete-secret! [_ k] (swap! state dissoc k) (js/Promise.resolve nil)))

(defn memory-store
  ([] (memory-store {}))
  ([initial] (->MemoryStore (atom initial))))

(defmulti create
  "The SecureStore for `(:impl opts)`."
  :impl)

(defmethod create :memory [{:keys [initial]}] (memory-store (or initial {})))

(defmethod ig/init-key :notebox.infra/secure-store [_ opts]
  (create opts))
