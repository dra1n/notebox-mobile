(ns notebox.ui.screens.home
  "Books home: every book, and the search over notes, tags and book titles
  (spec/design/screens/03)."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.lists :as lists]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.state :as state]))

(defn- results-list [{:keys [books notes pending]} book-titles]
  [:<>
   (for [b books]
     ^{:key (str "b" (:slug b))}
     [lists/book-row {:book b :on-press #(rf/dispatch [:nav/open-book (:slug b)])}])
   (for [{:keys [book note]} notes]
     ^{:key (str book "/" (:slug note))}
     [lists/note-row {:note note :subtitle (get book-titles book)
                      :on-press #(rf/dispatch [:nav/open-note book (:slug note)])}])
   (when (pos? pending)
     [:> rn/text {:testID "search-pending"
                  :style {:padding 16 :color "#888888" :font-size 13}}
      (str "Searching " (lists/plural pending "more book") "…")])
   (when (and (empty? books) (empty? notes) (zero? pending))
     [:> rn/text {:testID "search-empty" :style {:padding 16 :color "#888888" :font-size 13}}
      "Nothing found."])])

(defn home [_]
  (r/with-let [_ (rf/dispatch [:search/clear])]
    (let [status  @(rf/subscribe [:library/meta-status])
          books   @(rf/subscribe [:library/books])
          totals  @(rf/subscribe [:library/totals])
          query   @(rf/subscribe [:search/query])
          results @(rf/subscribe [:search/results])
          titles  (into {} (map (juxt :slug :title)) books)]
      [frame/screen-frame
       [:<>
        [frame/header {:left :menu :on-left state/open-menu! :logo? true
                       :action {:label "Add note" :on-press #(rf/dispatch [:nav/new-note nil])}}]
        [lists/search-bar {:value query :input-key @(rf/subscribe [:search/input-key])
                           :placeholder "Search notes, tags, books..."
                           :on-change #(rf/dispatch [:search/set-query % {:type :all}])
                           :stats (str (lists/plural (:books totals) "book") " ("
                                       (lists/plural (:notes totals) "note") ") in total")}]
        (case status
          (:unloaded :loading) [feedback/loading {:label "Loading your notes…"}]
          :error [feedback/empty-state {:title "Couldn't load your notes"
                                        :text "Check your connection and try again."
                                        :action {:label "Try again" :on-press #(rf/dispatch [:library/load])}}]
          (if (and (empty? books) (str/blank? query))
            [feedback/empty-state {:title "No notes yet"
                                   :text "You haven't added any notes yet. Add your first one."
                                   :action {:label "Add note" :on-press #(rf/dispatch [:nav/new-note nil])}}]
            [:> rn/scroll-view {:testID "home-list"
                                :refresh-control (r/as-element
                                                  [:> rn/refresh-control
                                                   {:refreshing (= :loading status)
                                                    :on-refresh #(rf/dispatch [:library/refresh])}])}
             (if (str/blank? query)
               (for [b books]
                 ^{:key (:slug b)}
                 [lists/book-row {:book b :on-press #(rf/dispatch [:nav/open-book (:slug b)])}])
               [results-list results titles])]))]])))
