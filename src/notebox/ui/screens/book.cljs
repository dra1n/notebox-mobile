(ns notebox.ui.screens.book
  "One book's notes, with search inside the book (spec/design/screens/04)."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.lists :as lists]
            [notebox.ui.rn.core :as rn]))

(defn book [{slug :book}]
  (r/with-let [_ (rf/dispatch [:search/clear])
               _ (rf/dispatch [:library/open-book slug])]
    (let [info    @(rf/subscribe [:library/book-info slug])
          status  @(rf/subscribe [:library/book-status slug])
          notes   @(rf/subscribe [:library/book-notes slug])
          query   @(rf/subscribe [:search/query])
          results @(rf/subscribe [:search/results])
          shown   (if (str/blank? query) notes (map :note (:notes results)))]
      [frame/screen-frame
       [:<>
        [frame/header {:left :back :on-left #(rf/dispatch [:nav/go-back]) :title (:title info)
                       :action {:label "Add note" :on-press #(rf/dispatch [:nav/new-note slug])}}]
        [lists/search-bar {:value query :placeholder "Search notes, tags..."
                           :on-change #(rf/dispatch [:search/set-query % {:type :book :book slug}])
                           :stats (str (lists/plural (count notes) "note") " in total")}]
        (case status
          (:unloaded :loading) [feedback/loading {}]
          :error [feedback/empty-state {:title "Couldn't load this book"
                                        :action {:label "Try again"
                                                 :on-press #(rf/dispatch [:library/load-books [slug]])}}]
          (if (empty? notes)
            [feedback/empty-state {:title "No notes in this book"
                                   :action {:label "Add note" :on-press #(rf/dispatch [:nav/new-note slug])}}]
            (into [:> rn/scroll-view {:testID "book-notes"}]
                  (for [n shown]
                    ^{:key (:slug n)}
                    [lists/note-row {:note n :on-press #(rf/dispatch [:nav/open-note slug (:slug n)])}]))))]])
    ;; the home screen searches everywhere: don't leave a book-scoped search behind
    (finally (rf/dispatch [:search/clear]))))
