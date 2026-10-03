(ns notebox.infra.rn.keychain
  "The :keychain secure store: react-native-keychain (iOS Keychain, Android
  Keystore). Each key is its own Keychain service, `<service>.<key>`."
  (:require ["react-native-keychain" :as keychain]
            [notebox.infra.secure-store :as secure-store]))

(defrecord KeychainStore [service]
  secure-store/SecureStore
  (read-secret [_ k]
    (-> (.getGenericPassword keychain #js {:service (str service "." k)})
        (.then (fn [creds] (when creds (.-password ^js creds))))))
  (write-secret! [_ k v]
    (-> (.setGenericPassword keychain k v #js {:service (str service "." k)})
        (.then (constantly nil))))
  (delete-secret! [_ k]
    (-> (.resetGenericPassword keychain #js {:service (str service "." k)})
        (.then (constantly nil)))))

(defmethod secure-store/create :keychain [{:keys [service]}]
  (->KeychainStore (or service "notebox")))
