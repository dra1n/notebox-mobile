(ns notebox.ui.root
  "The :notebox/ui component: the root view, and the navigator it installs so
  that :nav/navigate effects reach React Navigation (roadmap §8.1a)."
  (:require [integrant.core :as ig]
            [re-frame.core :as rf]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.modals :as modals]
            [notebox.ui.rn.navigation :as native.nav]
            [notebox.ui.rn.core :as rn]
            [notebox.ui.routes :as routes]
            [notebox.ui.state :as state]))

(defn- go! [route]
  (state/close-menu!)
  (rf/dispatch [:nav/go route {}]))

(defn- side-menu []
  [modals/side-menu
   {:visible? @state/menu-open?
    :account  @(rf/subscribe [:auth/account])
    :items    [{:id :books-home :label "Books & notes" :on-press #(go! :books-home)}
               {:id :books      :label "Books"         :on-press #(go! :books)}
               {:id :tags       :label "Tags"          :on-press #(go! :tags)}]
    :on-close state/close-menu!
    :on-logout (fn []
                 (rn/confirm! {:title "Log out?" :message "You'll need to sign in to Dropbox again."
                               :confirm-label "Log out"
                               :on-confirm (fn [] (state/close-menu!) (rf/dispatch [:app/logout]))}))}])

(defn root-view [nav-ref]
  (let [status @(rf/subscribe [:auth/status])]
    [:> rn/safe-area-provider
     [native.nav/container nav-ref [native.nav/stack (routes/screens status)]]
     [feedback/toasts {:messages @(rf/subscribe [:messaging/messages])
                       :on-dismiss #(rf/dispatch [:messaging/dismiss %])}]
     [feedback/sync-pill {:visible? @(rf/subscribe [:sync/saving?])}]
     ;; mounted only while open: a fresh native modal each time
     (when (and (= :signed-in status) @state/menu-open?) [side-menu])]))

(defmethod ig/init-key :notebox/ui [_ {:keys [navigator]}]
  ;; `navigator` is the :notebox.infra/navigator component: an atom we fill with
  ;; the navigation function (the UI layer doesn't require infra namespaces).
  (let [nav-ref (native.nav/create-ref)]
    (reset! navigator (native.nav/navigator-fn nav-ref))
    {:navigator navigator
     :root      (fn [] [root-view nav-ref])}))

(defmethod ig/halt-key! :notebox/ui [_ {:keys [navigator]}]
  (reset! navigator nil))
