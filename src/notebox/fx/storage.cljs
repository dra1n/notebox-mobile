(ns notebox.fx.storage
  "Effect handlers over notebox.storage.repository (roadmap §6.4):

    {:storage/load-meta {:on-ok [...] :on-fail [...]}}           ; + meta / error
    {:storage/load-book {:book slug :on-ok [...] :on-fail [...]}}  ; + notes / error
    {:storage/apply-ops {:ops [op ...] :on-ok [...] :on-fail [...]}}

  :storage/apply-ops runs the ops in order and stops at the first failure, so
  a move (add to the target, then remove from the source) never loses the
  note. on-ok gets the results; on-fail gets the error with :completed (how
  many ops succeeded).

  Plain functions; notebox.shell.effects registers them."
  (:require [notebox.fx.util :as util]
            [notebox.storage.repository :as repository]))

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

(defn effects
  "{fx-id handler} over `repository`."
  [{:keys [repository on-unauthorized alive]}]
  (let [done #(util/dispatch-result alive %1 %2 on-unauthorized)]
    {:storage/load-meta (fn [cbs] (done (repository/load-meta repository) cbs))
     :storage/load-book (fn [{:keys [book] :as cbs}]
                          (done (repository/load-book repository book) cbs))
     :storage/apply-ops (fn [{:keys [ops] :as cbs}]
                          (done (apply-ops! repository ops) cbs))}))
