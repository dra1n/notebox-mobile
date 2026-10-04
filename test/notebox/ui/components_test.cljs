(ns notebox.ui.components-test
  "Presentational components: given props, the right texts, testIDs, styles
  and callbacks, in every state (roadmap Phase 5 gate)."
  (:require [cljs.test :refer [deftest is testing]]
            [notebox.ui.components.feedback :as feedback]
            [notebox.ui.components.frame :as frame]
            [notebox.ui.components.lists :as lists]
            [notebox.ui.components.modals :as modals]
            [notebox.ui.components.tags :as tags]
            [notebox.ui.hiccup :as h]
            [notebox.ui.theme :as t]))

(defn- render [component props] (h/expand [component props]))

(deftest header
  (let [calls (atom [])
        tree  (render frame/header {:left :menu :on-left #(swap! calls conj :menu) :logo? true
                                    :action {:label "Add note" :on-press #(swap! calls conj :add)}})]
    (is (contains? (h/test-ids tree) "header-menu"))
    (is (contains? (h/test-ids tree) "logo"))
    (is (some #{"ADD NOTE"} (h/texts tree)) "buttons are upper-case, as designed")
    ((:on-press (h/by-test-id tree "header-menu")))
    ((:on-press (h/by-test-id tree "header-action")))
    (is (= [:menu :add] @calls)))
  (testing "back, with a truncated title"
    (let [tree (render frame/header {:left :back :on-left identity :title "A very long title"})]
      (is (contains? (h/test-ids tree) "header-back"))
      (is (= 1 (:number-of-lines (h/by-test-id tree "header-title"))))
      (is (= 160 (get-in (h/by-test-id tree "header-title") [:style :max-width])))))
  (testing "cancel + modal title + commit button"
    (let [tree (render frame/header {:left :cancel :on-left identity :title "Edit Note" :title-style :modal
                                     :action {:label "Save" :variant :commit :on-press identity}})]
      (is (some #{"Cancel" "Edit Note" "SAVE"} (h/texts tree)))
      (is (= (t/color :white) (get-in (h/by-test-id tree "header-title") [:style :color])))
      (is (= (t/color :logo) (get-in (h/by-test-id tree "header-action") [:style :background-color])))))
  (testing "a section title sits next to the menu"
    (let [tree (render frame/header {:left :menu :on-left identity :title "Books" :title-style :section})]
      (is (= ["Books"] (h/texts tree)))
      (is (= (t/color :white) (get-in (h/by-test-id tree "header-title") [:style :color])))))
  (testing "a disabled button does nothing"
    (let [tree (render frame/button {:label "x" :on-press identity :disabled? true :test-id "b"})]
      (is (nil? (:on-press (h/by-test-id tree "b")))))))

(deftest search-and-rows
  (let [changes (atom [])
        tree    (render lists/search-bar {:value "anna" :placeholder "Search notes, tags, books..."
                                          :on-change #(swap! changes conj %)
                                          :stats "30 books (67 notes) in total"})
        input   (h/by-test-id tree "search-input")]
    (is (= "anna" (:default-value input)) "uncontrolled: the value is the initial text")
    (is (= "Search notes, tags, books..." (:placeholder input)))
    ((:on-change-text input) "karenina")
    (is (= ["karenina"] @changes))
    (is (some #{"30 books (67 notes) in total"} (h/texts tree))))
  (testing "book rows count notes in the singular and plural"
    (is (some #{"1 note"} (h/texts (render lists/book-row {:book {:slug "b" :title "B" :count 1}}))))
    (is (some #{"0 notes"} (h/texts (render lists/book-row {:book {:slug "b" :title "B"}}))))
    (is (contains? (h/test-ids (render lists/book-row {:book {:slug "b1" :title "B"}})) "book-b1")))
  (testing "note rows: placeholders for missing title and text, and a subtitle"
    (let [tree (render lists/note-row {:note {:slug "n1"} :subtitle "Comics"})]
      (is (= ["Comics" "No title" "No additional text"] (h/texts tree)))
      (is (some #(= 2 (:number-of-lines (nth % 2))) (h/native-nodes tree)) "a two-line preview")))
  (testing "cards: the default book is highlighted"
    (let [plain (render lists/card {:title "Comics" :test-id "c"})
          dflt  (render lists/card {:title "Mine" :highlighted? true :subtitle "3 notes • Default"
                                    :action-label "Rename" :on-action identity :test-id "d"})]
      (is (= (t/color :bg-lighter) (get-in (h/by-test-id plain "c") [:style :background-color])))
      (is (= (t/color :cyan-lightest) (get-in (h/by-test-id dflt "d") [:style :background-color])))
      (is (some #{"Rename"} (h/texts dflt)))
      (is (some #{"3 notes • Default"} (h/texts dflt)))))
  (is (= "7 Books" (lists/plural 7 "Book")))
  (is (= "1 tag" (lists/plural 1 "tag"))))

(deftest tag-chips-and-editor
  (let [removed (atom nil)
        tree    (render tags/chip {:label "классика" :on-remove #(reset! removed true)})]
    ((:on-press (h/by-test-id tree "tag-remove-классика")))
    (is @removed))
  (is (= #{"tag-a" "tag-b"} (h/test-ids (render tags/chips {:tags ["a" "b"]}))))
  (testing "suggestions: known tags matching the input, not chosen yet, prefix first"
    (is (= ["work" "homework"] (tags/matching-suggestions ["homework" "work" "todo" "classic"] [] "wor")))
    (is (= ["homework"] (tags/matching-suggestions ["homework" "work"] ["work"] "wor")))
    (is (nil? (tags/matching-suggestions ["work"] [] "  "))))
  (testing "the editor: chips with ×, then + Add tag"
    (let [changes (atom nil)
          tree    (render tags/editor {:tags ["a" "b"] :suggestions [] :on-change #(reset! changes %)})]
      (is (contains? (h/test-ids tree) "tag-add"))
      (is (some #{"+ Add tag"} (h/texts tree)))
      ((:on-press (h/by-test-id tree "tag-remove-a")))
      (is (= ["b"] @changes)))))

(deftest feedback
  (let [dismissed (atom nil)
        tree (render feedback/toasts {:messages [{:id "1" :type :error :text "Offline"}
                                                 {:id "2" :type :notice :text "Saved"}]
                                      :on-dismiss #(reset! dismissed %)})]
    (is (= #{"toast-error" "toast-notice"} (h/test-ids tree)))
    (is (= (t/color :bg-orange-light) (get-in (h/by-test-id tree "toast-error") [:style :background-color])))
    ((:on-press (h/by-test-id tree "toast-notice")))
    (is (= "2" @dismissed)))
  (is (nil? (render feedback/toasts {:messages []})))
  (is (nil? (render feedback/sync-pill {:visible? false})))
  (is (some #{"Saving…"} (h/texts (render feedback/sync-pill {:visible? true}))))
  (is (= ["No notes yet" "Add your first."] (take 2 (h/texts (render feedback/empty-state {:title "No notes yet" :text "Add your first."})))))
  (is (contains? (h/test-ids (render feedback/not-found {})) "not-found"))
  (is (some #{"Loading…"} (h/texts (render feedback/loading {:label "Loading…"})))))

(deftest modals
  (testing "the side menu: account, entries, log out"
    (let [went (atom [])
          tree (render modals/side-menu {:visible? true :account {:email "me@example.com"}
                                         :items [{:id :books :label "Books" :on-press #(swap! went conj :books)}]
                                         :on-logout #(swap! went conj :logout)})]
      (is (some #{"me@example.com"} (h/texts tree)))
      (is (some #{"dropbox account"} (h/texts tree)))
      ((:on-press (h/by-test-id tree "menu-books")))
      ((:on-press (h/by-test-id tree "menu-logout")))
      (is (= [:books :logout] @went))))
  (testing "the prompt: OK is disabled while blank, and trims"
    (let [submitted (atom nil)
          blank (render modals/prompt {:visible? true :title "New book" :on-submit identity :on-cancel identity})
          named (render modals/prompt {:visible? true :title "Rename" :initial "  Comics  "
                                       :on-submit #(reset! submitted %) :on-cancel identity})]
      (is (nil? (:on-press (h/by-test-id blank "prompt-submit"))))
      ((:on-press (h/by-test-id named "prompt-submit")))
      (is (= "Comics" @submitted))))
  (testing "the book picker shows the selection, a new book, or the placeholder"
    (let [books [{:slug "a" :title "Anna"} {:slug "b" :title "Comics"}]]
      (is (some #{"Comics"} (h/texts (render modals/book-picker {:books books :selected "b"}))))
      (is (some #{"Poems"} (h/texts (render modals/book-picker {:books books :new-book-title "Poems"}))))
      (is (some #{"Select Notebook..."} (h/texts (render modals/book-picker {:books books}))))
      (testing "the list is mounted only once the field is pressed"
        (let [props {:books books :on-select identity :on-new-book identity}
              inner (modals/book-picker props)          ; form-2: keeps its open state
              closed (h/expand (inner props))]
          (is (= #{"book-picker"} (h/test-ids closed)))
          ((:on-press (h/by-test-id closed "book-picker")))
          (is (every? (h/test-ids (h/expand (inner props))) ["pick-a" "pick-b" "pick-new-book"])))))))
