(ns notebox.infra.rn.linking
  "The :linking browser: opens the sign-in page in the system browser and waits
  for the custom-scheme redirect (notebox://oauth?…) to reach the app through
  React Native's Linking. Only the latest sign-in attempt is waited for."
  (:require ["react-native" :as rn]
            [clojure.string :as str]
            [notebox.infra.browser :as browser]))

(defrecord LinkingBrowser [pending]
  browser/Browser
  (open-auth! [_ url redirect-prefix]
    (js/Promise.
     (fn [resolve reject]
       (some-> @pending .remove)
       (let [sub (.addEventListener rn/Linking "url"
                                    (fn [^js event]
                                      (when (str/starts-with? (.-url event) redirect-prefix)
                                        (some-> @pending .remove)
                                        (reset! pending nil)
                                        (resolve (.-url event)))))]
         (reset! pending sub)
         (-> (.openURL rn/Linking url)
             (.catch (fn [e]
                       (.remove sub)
                       (reset! pending nil)
                       (reject e)))))))))

(defmethod browser/create :linking [_]
  (->LinkingBrowser (atom nil)))
