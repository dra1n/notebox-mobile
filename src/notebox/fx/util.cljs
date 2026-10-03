(ns notebox.fx.util
  "Shared plumbing for effects: turning Promises into result events."
  (:require [re-frame.core :as rf]))

(defn error-data
  "The error map of a rejection: ex-data (notebox.dropbox.errors), or :other."
  [e]
  (or (ex-data e) {:type :other :summary (or (ex-message e) (str e))}))

(defn dispatch-result
  "When Promise `p` settles, dispatch `on-ok` + value or `on-fail` + error map,
  unless the `alive` flag (an atom, owned by notebox.shell.effects) is off by
  then: results that arrive after a halt (dev/reset, a test's next system)
  are dropped, so they can't leak into the new system.
  If `on-unauthorized` is given (configuration data, so features never refer
  to the shell), an :unauthorized failure dispatches it instead of `on-fail`:
  the session is over, and the shell resets everything."
  ([alive p callbacks] (dispatch-result alive p callbacks nil))
  ([alive p {:keys [on-ok on-fail]} on-unauthorized]
   (let [dispatch #(when @alive (rf/dispatch %))]
     (.then p
            (fn [v] (when on-ok (dispatch (conj on-ok v))))
            (fn [e]
              (let [err (error-data e)]
                (cond
                  (and on-unauthorized (= :unauthorized (:type err))) (dispatch on-unauthorized)
                  on-fail (dispatch (conj on-fail err)))))))))

