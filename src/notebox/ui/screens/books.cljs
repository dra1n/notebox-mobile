(ns notebox.ui.screens.books
  "Managing books: add, rename, set the default, delete (spec/design/screens/09)."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.domain.search :as search]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.lists :as lists]
            [notebox.ui.components.modals :as modals]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.state :as state]))

(defn- book-actions! [{:keys [slug title default?]}]
  (rn/choose! {:title title
               :options (cond-> [{:label "Open" :on-press #(rf/dispatch [:nav/open-book slug])}]
                          (not default?)
                          (conj {:label "Make default" :on-press #(rf/dispatch [:settings/set-default-book slug])})
                          :always
                          (conj {:label "Delete" :destructive? true
                                 :on-press #(rn/confirm! {:title (str "Delete \"" title "\"?")
                                                          :message "The book and all its notes will be removed from Dropbox."
                                                          :confirm-label "Delete" :destructive? true
                                                          :on-confirm (fn [] (rf/dispatch [:books/delete slug]))})}))}))

(defn books [_]
  (r/with-let [query (r/atom "")
               prompt (r/atom nil)]   ; nil | {:mode :new} | {:mode :rename :book b}
    (let [all   @(rf/subscribe [:books/list])
          shown (filter #(search/matches-book? @query %) all)]
      [frame/screen-frame
       [:<>
        [frame/header {:left :menu :on-left state/open-menu! :title "Books" :title-style :modal
                       :action {:label "Add book" :on-press #(reset! prompt {:mode :new})}}]
        [lists/search-bar {:value @query :placeholder "Search books..." :on-change #(reset! query %)
                           :test-id "books-search"}]
        (if (empty? all)
          [feedback/empty-state {:title "No books yet"
                                 :action {:label "Add book" :on-press #(reset! prompt {:mode :new})}}]
          (into [:> rn/scroll-view {:content-container-style {:padding 16 :gap 12}}
                 [lists/count-line (lists/plural (count all) "Book")]]
                (for [{:keys [slug title count default?] :as b} shown]
                  ^{:key slug}
                  [lists/card {:title title :highlighted? default? :test-id (str "book-card-" slug)
                               :subtitle (str (lists/plural (or count 0) "note") (when default? " • Default"))
                               :action-label "Rename" :on-action #(reset! prompt {:mode :rename :book b})
                               :on-press #(book-actions! b)}])))
        [modals/prompt {:visible? (some? @prompt)
                        :title (if (= :rename (:mode @prompt)) "Rename book" "New book")
                        :initial (get-in @prompt [:book :title]) :placeholder "Book title"
                        :submit-label (if (= :rename (:mode @prompt)) "Rename" "Add")
                        :on-submit (fn [title]
                                     (if (= :rename (:mode @prompt))
                                       (rf/dispatch [:books/rename (get-in @prompt [:book :slug]) title])
                                       (rf/dispatch [:books/create title]))
                                     (reset! prompt nil))
                        :on-cancel #(reset! prompt nil)}]]])))
