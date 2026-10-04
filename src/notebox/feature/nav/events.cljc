(ns notebox.feature.nav.events
  "Navigation requests from the UI, as events that emit navigation as data
  (the :nav/navigate effect; roadmap §5.2 rule 4)."
  (:require [re-frame.core :as rf]))

(rf/reg-event-fx :nav/go (fn [_ [_ route params]] {:nav/navigate [route params]}))
(rf/reg-event-fx :nav/go-back (fn [_ _] {:nav/back nil}))
(rf/reg-event-fx :nav/open-book (fn [_ [_ book]] {:nav/navigate [:book {:book book}]}))
(rf/reg-event-fx :nav/open-note (fn [_ [_ book note]] {:nav/navigate [:note {:book book :note note}]}))
(rf/reg-event-fx :nav/edit-note (fn [_ [_ book note]] {:nav/navigate [:note-edit {:book book :note note}]}))
(rf/reg-event-fx :nav/new-note (fn [_ [_ book]] {:nav/navigate [:note-new (if book {:book book} {})]}))
