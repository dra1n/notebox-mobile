(ns notebox.fx.storage
  "Effects over notebox.storage.repository (roadmap §6.4):

    {:storage/load-meta {:on-ok [...] :on-fail [...]}}           ; + meta / error
    {:storage/load-book {:book slug :on-ok [...] :on-fail [...]}}  ; + notes / error
    {:storage/apply-ops {:ops [op ...] :on-ok [...] :on-fail [...]}}

  :storage/apply-ops runs the ops in order and stops at the first failure, so
  a move (add to the target, then remove from the source) never loses the
  note. on-ok gets the results; on-fail gets the error with :completed (how
  many ops succeeded)."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.fx.util :as util]
            [notebox.storage.repository :as repository]))

(def fx-ids [:storage/load-meta :storage/load-book :storage/apply-ops])

(defn- apply-ops! [repo ops]
  (let [done (atom [])]
    (-> (reduce (fn [p op]
                  (.then p (fn [_]
                             (.then (repository/apply-op! repo op)
                                    #(swap! done conj %)))))
                (js/Promise.resolve nil)
                ops)
        (.then (fn [_] @done))
        (.catch (fn [e]
                  (throw (ex-info (or (ex-message e) "save failed")
                                  (assoc (util/error-data e) :completed (count @done)))))))))

(defmethod ig/init-key :notebox.fx/storage [_ {:keys [repository on-unauthorized]}]
  (let [alive (util/alive)
        done  #(util/dispatch-result alive %1 %2 on-unauthorized)]
    (rf/reg-fx :storage/load-meta (fn [cbs] (done (repository/load-meta repository) cbs)))
    (rf/reg-fx :storage/load-book
               (fn [{:keys [book] :as cbs}] (done (repository/load-book repository book) cbs)))
    (rf/reg-fx :storage/apply-ops
               (fn [{:keys [ops] :as cbs}] (done (apply-ops! repository ops) cbs)))
    {:repository repository :alive alive}))

(defmethod ig/halt-key! :notebox.fx/storage [_ {:keys [alive]}]
  (reset! alive false)
  (util/clear-fx! fx-ids))
