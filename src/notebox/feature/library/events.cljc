(ns notebox.feature.library.events
  "Loading the library, and the one save flow every feature uses
  (roadmap §6.4): apply the ops to app-db, send them to Dropbox, then adopt
  Dropbox's result or roll back."
  (:require [re-frame.core :as rf]
            [notebox.feature.library.queries :as q]
            [notebox.feature.sync.queries :as sync]))

(rf/reg-event-fx
 :library/load
 (fn [{:keys [db]} _]
   {:db (q/set-meta-loading db)
    :storage/load-meta {:on-ok [:library/meta-loaded] :on-fail [:library/meta-failed]}}))

(rf/reg-event-db
 :library/meta-loaded
 (fn [db [_ m]] (q/set-meta db m)))

(rf/reg-event-fx
 :library/meta-failed
 (fn [{:keys [db]} [_ err]]
   {:db (q/set-meta-error db err)
    :dispatch [:messaging/show {:type :error :text "Couldn't load your notes from Dropbox."}]}))

(defn- load-book-fx [slug]
  [:storage/load-book {:book slug :on-ok [:library/book-loaded slug]
                       :on-fail [:library/book-failed slug]}])

(rf/reg-event-fx
 :library/open-book
 (fn [{:keys [db]} [_ slug]]
   (let [db' (q/set-last-active db slug)]
     (if (#{:ready :loading} (q/book-status db slug))
       {:db db'}
       {:db (q/set-book-loading db' slug)
        :fx [(load-book-fx slug)]}))))

(rf/reg-event-fx
 :library/load-books
 (fn [{:keys [db]} [_ slugs]]
   (let [todo (filter #(#{:unloaded :error} (q/book-status db %)) slugs)]
     {:db (reduce q/set-book-loading db todo)
      :fx (mapv load-book-fx todo)})))

(rf/reg-event-db
 :library/book-loaded
 (fn [db [_ slug notes]] (q/set-book db slug notes)))

(rf/reg-event-fx
 :library/book-failed
 (fn [{:keys [db]} [_ slug err]]
   {:db (q/set-book-error db slug err)
    :dispatch [:messaging/show {:type :error :text "Couldn't load the book from Dropbox."}]}))

(rf/reg-event-fx
 :library/refresh
 (fn [{:keys [db]} _]
   (let [loaded (keys (q/loaded-books db))]
     {:db (reduce q/set-book-loading (q/set-meta-loading db) loaded)
      :fx (into [[:storage/load-meta {:on-ok [:library/meta-loaded]
                                      :on-fail [:library/meta-failed]}]]
                (map load-book-fx loaded))})))

(rf/reg-event-fx
 :library/save
 (fn [{:keys [db]} [_ ops {:keys [success-message]}]]
   {:db (sync/started (reduce q/apply-op db ops))
    :storage/apply-ops {:ops ops
                        :on-ok [:library/saved ops success-message]
                        :on-fail [:library/save-failed ops]}}))

(defn- warning-message [{:keys [type]}]
  (case type
    :note/re-added "The note had been deleted on another device; your edit brought it back."
    nil))

(rf/reg-event-fx
 :library/saved
 (fn [{:keys [db]} [_ ops success-message results]]
   (let [db'      (sync/finished db)
         messages (concat (keep warning-message (mapcat :warnings results))
                          (when success-message [success-message]))]
     {:db (if (zero? (sync/pending db'))
            (q/adopt-results db' ops results)  ; nothing else in flight: take Dropbox's copy
            db')
      :fx (vec (for [m messages] [:dispatch [:messaging/show {:type :notice :text m}]]))})))

(defn- rollback
  "`db` and effects that put `books` (and the meta) back to what Dropbox has:
  loaded books are marked loading and re-read, others are forgotten. Done in
  the failing event itself, so app-db never looks settled while still showing
  the failed change."
  [db books]
  (let [loaded? (set (keys (q/loaded-books db)))]
    {:db (reduce (fn [db slug]
                   (if (loaded? slug)
                     (q/set-book-loading db slug)
                     (update-in db [:library :books] dissoc slug)))
                 db books)
     :fx (into [[:storage/load-meta {:on-ok [:library/meta-loaded] :on-fail [:library/meta-failed]}]]
               (map load-book-fx (filter loaded? books)))}))

(rf/reg-event-fx
 :library/save-failed
 (fn [{:keys [db]} [_ ops err]]
   (let [{db' :db fx :fx} (rollback (sync/finished db) (distinct (map :book ops)))]
     {:db db'
      :fx (conj fx [:dispatch [:messaging/show
                               {:type :error
                                :text (str "Couldn't save to Dropbox"
                                           (when (= :network (:type err)) ": you're offline")
                                           ". Your change was undone.")}]])})))

