(ns notebox.ui.routes
  "The screens, as data, for each session state (roadmap §8.2): the stack
  switches with :auth/status, so signing in or out replaces it."
  (:require [notebox.ui.screens.book :as book]
            [notebox.ui.screens.books :as books]
            [notebox.ui.screens.editor :as editor]
            [notebox.ui.screens.home :as home]
            [notebox.ui.screens.note :as note]
            [notebox.ui.screens.start :as start]
            [notebox.ui.screens.tags :as tags]))

(def library
  [{:name :books-home :screen home/home}
   {:name :book       :screen book/book}
   {:name :note       :screen note/note}
   ;; regular screens sliding up (a fullScreenModal ignores the safe area)
   {:name :note-edit  :screen editor/edit-note :options {:animation "slide_from_bottom"}}
   {:name :note-new   :screen editor/new-note  :options {:animation "slide_from_bottom"}}
   {:name :books      :screen books/books}
   {:name :tags       :screen tags/tags-screen}])

(defn screens [auth-status]
  (case auth-status
    :unknown [{:name :splash :screen start/splash}]
    (:signed-out :signing-in) [{:name :start :screen start/start}]
    library))
