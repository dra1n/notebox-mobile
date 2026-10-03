(ns notebox.shell.effects
  "The re-frame effects component: builds every effect and coeffect handler
  (notebox.fx.*) from the components it depends on and registers them; on
  halt it unregisters them, and results still in flight are dropped (their
  `alive` flag goes off)."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.fx.auth :as fx.auth]
            [notebox.fx.cofx :as fx.cofx]
            [notebox.fx.navigation :as fx.navigation]
            [notebox.fx.settings :as fx.settings]
            [notebox.fx.storage :as fx.storage]))

(defn handlers
  "{:fx {id handler} :cofx {id handler}} for the given components."
  [deps]
  {:fx   (merge (fx.storage/effects deps)
                (fx.auth/effects deps)
                (fx.settings/effects deps)
                (fx.navigation/effects deps))
   :cofx (fx.cofx/coeffects deps)})

(defmethod ig/init-key :notebox.shell/effects [_ deps]
  (let [alive (atom true)
        {:keys [fx cofx] :as hs} (handlers (assoc deps :alive alive))]
    (doseq [[id f] fx] (rf/reg-fx id f))
    (doseq [[id f] cofx] (rf/reg-cofx id f))
    {:alive alive :fx-ids (keys fx) :cofx-ids (keys cofx) :handlers hs}))

(defmethod ig/halt-key! :notebox.shell/effects [_ {:keys [alive fx-ids cofx-ids]}]
  (reset! alive false)
  (doseq [id fx-ids] (rf/clear-fx id))
  (doseq [id cofx-ids] (rf/clear-cofx id)))
