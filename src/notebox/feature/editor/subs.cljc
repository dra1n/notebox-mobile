(ns notebox.feature.editor.subs
  (:require [re-frame.core :as rf]
            [notebox.feature.editor.queries :as q]))

(rf/reg-sub :editor/default-book (fn [db _] (q/default-book db)))
