(ns notebox.feature.flows-test
  "Every web save flow (roadmap §3.3) and every §1.1 addition, as re-frame
  events against the test system (fake Dropbox seeded with the fixture
  library), including rollback, the move-failure case and session expiry."
  (:require [cljs.test :refer [deftest is async testing]]
            [re-frame.core :as rf]
            [notebox.domain.json :as json]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.fake :as fake]
            [notebox.dropbox.fake-server :as server]
            [notebox.dropbox.fake-store :as fake-store]
            [notebox.feature.library.queries :as library]
            [notebox.feature.sync.queries :as sync]
            [notebox.infra.kv-store :as kv]
            [notebox.test.async :as a]
            [notebox.test.system :as ts :refer [await-db await-message await-saved await-saving sub]]))

(def anna "k3J9aZ0qLx")
(def comics "Cm1c5_bk-0")
(def inbox "inBox_0001")
(def ideas "Id3a5_box9")

(defn- remote [dbx path]
  (some-> (fake-store/download @(:store dbx) path) :ok :text json/decode))

(defn- remote-book [dbx slug] (remote dbx (str "/notes/" slug ".json")))
(defn- remote-meta [dbx] (remote dbx "/notes/.meta.json"))
(defn- remote-info [dbx slug] (some #(when (= slug (:slug %)) %) (:notesInfo (remote-meta dbx))))

(defn- settled? [db] (zero? (sync/pending db)))
(defn- expect-message [text] (.then (await-message text) (fn [_] (is true text))))
(defn- meta-ready? [db] (= :ready (library/meta-status db)))
(defn- book-ready? [slug] #(= :ready (library/book-status % slug)))

(defn- messages [] (map :text (sub [:messaging/messages])))

(defn- started
  "Start the system and wait until the library is loaded and `books` are open."
  ([] (started {} []))
  ([opts books]
   (let [s (ts/start! opts)]
     (doseq [b books] (rf/dispatch-sync [:library/open-book b]))
     (.then (await-db #(and (meta-ready? %) (every? (fn [b] ((book-ready? b) %)) books)))
            (constantly s)))))

;; --- start-up and session ----------------------------------------------------------

(deftest start-signed-out
  (async done
    (ts/start! {:signed-in? false})
    (a/run done
      (.then (await-db #(= :signed-out (get-in % [:auth :status])))
             (fn [_]
               (is (= :signed-out (sub [:auth/status])))
               (is (false? (sub [:auth/signed-in?])))
               (is (= :unloaded (sub [:library/meta-status]))))))))

(deftest start-signed-in-loads-the-library-and-account
  (async done
    (a/run done
      (-> (started {:settings {"default-book" (pr-str comics)}} [])
          (.then #(await-db (fn [db] (get-in db [:auth :account]))))
          (.then (fn [_]
                   (is (true? (sub [:auth/signed-in?])))
                   (is (= "fake@example.com" (:email (sub [:auth/account]))))
                   (is (= 10 (count (sub [:library/books]))))
                   (is (= {:books 10 :notes 20} (sub [:library/totals])) "as recorded in notesInfo")
                   (is (= comics (sub [:settings/default-book])) "the default book comes from the device")))))))

(deftest sign-in-and-out
  (async done
    (let [srv (server/server)]
      (ts/start! {:signed-in? false
                  :http-fetch (server/fetch-fn srv)
                  :browser-respond #(server/approve srv %)})
      (a/run done
        (a/chain
         #(await-db (fn [db] (= :signed-out (get-in db [:auth :status]))))
         #(rf/dispatch-sync [:app/login])
         #(is (= :signing-in (sub [:auth/status])))
         #(await-db meta-ready?)
         #(is (= :signed-in (sub [:auth/status])))
         #(rf/dispatch-sync [:settings/set-default-book anna])
         #(rf/dispatch-sync [:app/logout])
         #(await-db (fn [db] (= :signed-out (get-in db [:auth :status]))))
         (fn []
           (is (= :unloaded (sub [:library/meta-status])) "the library is gone")
           (is (= anna (sub [:settings/default-book])) "device settings stay")))))))

(deftest a-declined-sign-in
  (async done
    (ts/start! {:signed-in? false})   ; the fake browser declines
    (a/run done
      (a/chain
       #(await-db (fn [db] (= :signed-out (get-in db [:auth :status]))))
       #(rf/dispatch-sync [:app/login])
       #(await-db (fn [db] (get-in db [:auth :error])))
       (fn []
         (is (= :signed-out (sub [:auth/status])))
         (is (= :unauthorized (:type (sub [:auth/error]))))
         (expect-message "Couldn't sign in to Dropbox."))))))

(deftest an-expired-session-signs-out
  (async done
    (let [{:keys [dbx]} (ts/start!)]
      (a/run done
        (a/chain
         #(await-db meta-ready?)
         #(fake/fail-next! dbx {:ops [:download] :error {:type :unauthorized :summary "expired"}})
         #(rf/dispatch-sync [:library/refresh])
         #(await-db (fn [db] (= :signed-out (get-in db [:auth :status]))))
         #(expect-message "Your Dropbox session has expired. Please sign in again.")
         #(is (= :unloaded (sub [:library/meta-status]))))))))

;; --- loading ----------------------------------------------------------------------

(deftest opening-books-and-refreshing
  (async done
    (let [{:keys [dbx]} (ts/start!)]
      (a/run done
        (a/chain
         #(await-db meta-ready?)
         #(rf/dispatch-sync [:library/open-book anna])
         #(is (= :loading (sub [:library/book-status anna])))
         #(await-db (book-ready? anna))
         (fn []
           (is (= 3 (count (sub [:library/book-notes anna]))))
           (is (= "Новое поколение дворянства" (:title (sub [:library/note anna "Vh2xQ1aaaa"]))))
           (is (= anna (sub [:library/last-active])))
           (is (= {anna 3} (update-vals (sub [:library/loaded-books]) count))))
         #(rf/dispatch-sync [:library/open-book anna])
         #(is (= :ready (sub [:library/book-status anna])) "opening a loaded book doesn't reload it")
         #(api/upload dbx (str "/notes/" anna ".json") [] {:mode :overwrite})
         #(rf/dispatch-sync [:library/refresh])
         #(await-db (fn [db] (and (meta-ready? db) (= [] (library/book-notes db anna)))))
         #(is (= [] (sub [:library/book-notes anna])) "refresh re-reads loaded books"))))))

(deftest load-failures-are-reported
  (async done
    (let [{:keys [dbx]} (ts/start!)]
      (a/run done
        (a/chain
         #(await-db meta-ready?)
         #(fake/fail-next! dbx {:ops [:download] :re #"Cm1c5" :error {:type :network}})
         #(rf/dispatch-sync [:library/open-book comics])
         #(await-db (fn [db] (= :error (library/book-status db comics))))
         #(expect-message "Couldn't load the book from Dropbox.")
         #(fake/fail-next! dbx {:ops [:download] :re #"meta" :error {:type :server}})
         #(rf/dispatch-sync [:library/load])
         #(await-db (fn [db] (= :error (library/meta-status db))))
         #(expect-message "Couldn't load your notes from Dropbox."))))))

;; --- the web save flows (§3.3) --------------------------------------------------------

(deftest add-note-to-an-existing-book
  (async done
    (a/run done
      (-> (started {} [inbox])
          (.then (fn [{:keys [dbx nav]}]
                   (rf/dispatch-sync [:editor/create-note {:book inbox :title "Купить билеты"
                                                      :text "Поезд" :tags ["todo"]}])
                   (-> (await-saving)
                       (.then (fn [_]
                                (testing "optimistic"
                                  (is (= 2 (count (sub [:library/book-notes inbox]))))
                                  (is (true? (sub [:sync/saving?]))))
                                (await-db settled?)))
                       (.then (fn [_]
                                (let [n (last (remote-book dbx inbox))]
                                  (is (= {:slug "newSlug1xx" :title "Купить билеты" :text "Поезд"
                                          :tags ["todo"] :created-at ts/now-iso} n))
                                  (is (= [:slug :title :text :tags :created-at] (keys n))))
                                (is (= 2 (:count (remote-info dbx inbox))))
                                (is (= ["todo"] (get-in (remote-meta dbx) [:tagsInfo (keyword inbox)])))
                                (is (= [[:navigate :note {:book inbox :note "newSlug1xx"}]] @nav))
                                (expect-message "Note added."))))))))))

(deftest add-note-to-a-new-book
  (async done
    (a/run done
      (-> (started)
          (.then (fn [{:keys [dbx]}]
                   (rf/dispatch-sync [:editor/create-note {:new-book-title "Новая книга" :title "First"}])
                   (-> (await-saved)
                       (.then (fn [_]
                                (is (= [{:slug "newSlug2xx" :title "Новая книга" :count 1}]
                                       (filter #(= "newSlug2xx" (:slug %)) (:notesInfo (remote-meta dbx)))))
                                (is (= ["newSlug1xx"] (map :slug (remote-book dbx "newSlug2xx"))))
                                (is (= "newSlug2xx" (last (:collectionsList (remote-meta dbx))))))))))))))

(deftest edit-a-note-in-place
  (async done
    (a/run done
      (-> (started {} [anna])
          (.then (fn [{:keys [dbx nav]}]
                   (rf/dispatch-sync [:editor/update-note anna "Vh2xQ1cccc"
                                 {:title "Вопрос" :tags ["классика" "цитаты"]} {}])
                   (-> (await-saving)
                       (.then (fn [_]
                                (is (= "Вопрос" (:title (sub [:library/note anna "Vh2xQ1cccc"])))
                                    "optimistic")
                                (await-db settled?)))
                       (.then (fn [_]
                                (let [n (nth (remote-book dbx anna) 2)]
                                  (is (= "Вопрос" (:title n)))
                                  (is (= ts/now-iso (:updated-at n)))
                                  (is (= [:slug :created-at :text :tags :title :updated-at] (keys n))
                                      "merged like the web: new keys appended"))
                                (is (= ["классика" "прочитанное" "5+" "цитаты"]
                                       (get-in (remote-meta dbx) [:tagsInfo (keyword anna)])))
                                (is (= [[:navigate :note {:book anna :note "Vh2xQ1cccc"}]] @nav)))))))))))

(deftest move-a-note-to-an-existing-book
  (async done
    (a/run done
      (-> (started {} [inbox ideas])
          (.then (fn [{:keys [dbx nav]}]
                   (rf/dispatch-sync [:editor/update-note inbox "inb0x00001" {} {:target-book ideas}])
                   (-> (await-saved)
                       (.then (fn [_]
                                (is (= [] (remote-book dbx inbox)))
                                (is (= ["1d3a000001" "inb0x00001"] (map :slug (remote-book dbx ideas))))
                                (is (= 0 (:count (remote-info dbx inbox))))
                                (is (= 2 (:count (remote-info dbx ideas))))
                                (is (= [[:navigate :note {:book ideas :note "inb0x00001"}]] @nav)))))))))))

(deftest move-a-note-to-a-new-book
  (async done
    (a/run done
      (-> (started {} [inbox])
          (.then (fn [{:keys [dbx]}]
                   (rf/dispatch-sync [:editor/update-note inbox "inb0x00001" {:title "Moved"}
                                 {:new-book-title "Calls"}])
                   (-> (await-saved)
                       (.then (fn [_]
                                (is (= [] (remote-book dbx inbox)))
                                (is (= ["Moved"] (map :title (remote-book dbx "newSlug2xx"))))
                                (is (= "Calls" (:title (remote-info dbx "newSlug2xx")))))))))))))

(deftest delete-a-note
  (async done
    (a/run done
      (-> (started {} [comics])
          (.then (fn [{:keys [dbx nav]}]
                   (rf/dispatch-sync [:editor/delete-note comics "c0m1c00002"])
                   (-> (await-saved)
                       (.then (fn [_]
                                (is (= 4 (count (remote-book dbx comics))))
                                (is (= 4 (:count (remote-info dbx comics))) "the drifted count heals")
                                (is (= [[:navigate :book {:book comics}]] @nav)
                                    "to the book, not back to the deleted note")
                                (expect-message "Note deleted."))))))))))

(deftest add-rename-delete-a-book
  (async done
    (let [{:keys [dbx]} (ts/start! {:settings {"default-book" (pr-str "newSlug1xx")}})]
      (a/run done
        (a/chain
         #(await-db meta-ready?)
         #(rf/dispatch-sync [:books/create "Poems"])
         #(await-saved)
         #(is (= {:slug "newSlug1xx" :title "Poems" :count 0} (remote-info dbx "newSlug1xx")))
         #(is (= [] (remote-book dbx "newSlug1xx")))
         #(rf/dispatch-sync [:books/rename "newSlug1xx" "Стихи"])
         #(await-saved)
         #(is (= "Стихи" (:title (remote-info dbx "newSlug1xx"))))
         #(is (:default? (first (filter :default? (sub [:books/list])))))
         #(rf/dispatch-sync [:books/delete "newSlug1xx"])
         #(await-saved)
         (fn []
           (is (nil? (remote-info dbx "newSlug1xx")))
           (is (nil? (remote-book dbx "newSlug1xx")))
           (is (not (some #{"newSlug1xx"} (:collectionsList (remote-meta dbx)))))
           (is (nil? (sub [:settings/default-book])) "deleting the default book clears the default")))))))

;; --- §1.1 additions -----------------------------------------------------------------

(deftest the-default-book
  (async done
    (let [{:keys [kv]} (ts/start!)]
      (a/run done
        (a/chain
         #(await-db meta-ready?)
         #(is (= anna (sub [:editor/default-book])) "no default, nothing opened: the first book")
         #(rf/dispatch-sync [:library/open-book ideas])
         #(is (= ideas (sub [:editor/default-book])) "then the last book opened")
         #(rf/dispatch-sync [:settings/set-default-book comics])
         #(is (= comics (sub [:editor/default-book])) "the default book wins")
         #(.then (kv/get-value kv :default-book) (fn [v] (is (= comics v) "saved on the device")))
         #(is (= [comics] (map :slug (filter :default? (sub [:books/list]))))))))))

(deftest tag-counts-load-every-book
  (async done
    (ts/start!)
    (a/run done
      (a/chain
       #(await-db meta-ready?)
       #(is (not-every? :complete? (sub [:tags/index])))
       #(rf/dispatch-sync [:tags/load-counts])
       #(await-db (fn [db] (= 10 (count (library/loaded-books db)))))
       (fn []
         (let [idx (sub [:tags/index])]
           (is (every? :complete? idx))
           (is (= 3 (:count (first (filter #(= "favorite" (:tag %)) idx)))))))))))

(deftest search-everywhere-in-a-book-and-in-titles
  (async done
    (ts/start!)
    (a/run done
      (a/chain
       #(await-db meta-ready?)
       #(rf/dispatch-sync [:search/set-query "НОВОЕ" {:type :all}])
       #(is (= "НОВОЕ" (sub [:search/query])))
       #(is (pos? (:pending (sub [:search/results]))) "results stream in as books load")
       #(await-db (fn [db] (= 10 (count (library/loaded-books db)))))
       (fn []
         (let [{:keys [notes pending]} (sub [:search/results])]
           (is (= 0 pending))
           (is (= [[anna "Vh2xQ1aaaa"]] (map (juxt :book (comp :slug :note)) notes)))))
       #(rf/dispatch-sync [:search/set-query "comics" {:type :all}])
       #(is (= [comics] (map :slug (:books (sub [:search/results])))) "book titles match too")
       #(rf/dispatch-sync [:search/set-query "rec" {:type :books}])
       #(is (= ["Recipes"] (map :title (:books (sub [:search/results])))))
       #(rf/dispatch-sync [:search/set-query "watch" {:type :book :book comics}])
       #(is (= ["c0m1c00001"] (map (comp :slug :note) (:notes (sub [:search/results])))))
       #(rf/dispatch-sync [:search/clear])
       #(is (= "" (sub [:search/query])))))))

;; --- failures --------------------------------------------------------------------

(deftest a-failed-save-rolls-back
  (async done
    (a/run done
      (-> (started {} [comics])
          (.then (fn [{:keys [dbx]}]
                   (fake/fail-next! dbx {:ops [:upload] :re #"Cm1c5" :error {:type :network}})
                   (rf/dispatch-sync [:editor/delete-note comics "c0m1c00002"])
                   (-> (await-saving)
                       (.then (fn [_]
                                (is (= 4 (count (sub [:library/book-notes comics]))) "optimistic")
                                (await-db #(and (settled? %) ((book-ready? comics) %)
                                                (= 5 (count (library/book-notes % comics)))))))
                       (.then (fn [_]
                                (is (= 5 (count (remote-book dbx comics))) "Dropbox unchanged")
                                (expect-message "Couldn't save to Dropbox: you're offline. Your change was undone."))))))))))

(deftest a-move-that-fails-halfway-keeps-the-note
  (async done
    (a/run done
      (-> (started {} [inbox ideas])
          (.then (fn [{:keys [dbx]}]
                   (fake/fail-next! dbx {:ops [:upload] :re #"inBox" :error {:type :network}})
                   (rf/dispatch-sync [:editor/update-note inbox "inb0x00001" {} {:target-book ideas}])
                   (-> (await-saving)
                       (.then #(await-db (fn [db] (and (settled? db)
                                                       ((book-ready? inbox) db)
                                                       ((book-ready? ideas) db)
                                                       (= 2 (count (library/book-notes db ideas)))))))
                       (.then (fn [_]
                                (is (= ["inb0x00001"] (map :slug (remote-book dbx inbox))))
                                (is (= ["1d3a000001" "inb0x00001"] (map :slug (remote-book dbx ideas)))
                                    "duplicated, never lost (roadmap §6.2)")
                                (is (= ["inb0x00001"] (map :slug (sub [:library/book-notes inbox])))
                                    "after the rollback app-db shows what Dropbox has"))))))))))

(deftest an-edit-of-a-note-deleted-elsewhere-brings-it-back
  (async done
    (a/run done
      (-> (started {} [inbox])
          (.then (fn [{:keys [dbx]}]
                   (-> (api/upload dbx (str "/notes/" inbox ".json") [] {:mode :overwrite})
                       (.then #(rf/dispatch-sync [:editor/update-note inbox "inb0x00001"
                                                  {:title "Still here"} {}]))
                       (.then #(await-saved))
                       (.then (fn [_]
                                (is (= ["Still here"] (map :title (remote-book dbx inbox))))
                                (expect-message "The note had been deleted on another device; your edit brought it back."))))))))))

(deftest editing-a-note-that-isnt-loaded
  (async done
    (ts/start!)
    (a/run done
      (a/chain
       #(await-db meta-ready?)
       #(rf/dispatch-sync [:editor/update-note anna "Vh2xQ1aaaa" {:title "x"} {}])
       #(expect-message "That note isn't loaded.")))))

(deftest messages-can-be-dismissed
  (async done
    (ts/start!)
    (a/run done
      (a/chain
       #(rf/dispatch-sync [:messaging/show {:type :notice :text "Hello"}])
       (fn []
         (let [{:keys [id type]} (last (sub [:messaging/messages]))]
           (is (= :notice type))
           (rf/dispatch-sync [:messaging/dismiss id])))
       #(is (not-any? #{"Hello"} (messages)))))))

(deftest navigation-requests
  (let [{:keys [nav]} (ts/start!)]
    (rf/dispatch-sync [:nav/open-book anna])
    (rf/dispatch-sync [:nav/open-note anna "Vh2xQ1aaaa"])
    (rf/dispatch-sync [:nav/edit-note anna "Vh2xQ1aaaa"])
    (rf/dispatch-sync [:nav/new-note nil])
    (rf/dispatch-sync [:nav/new-note anna])
    (rf/dispatch-sync [:nav/go :tags {}])
    (rf/dispatch-sync [:nav/go-back])
    (is (= [[:navigate :book {:book anna}]
            [:navigate :note {:book anna :note "Vh2xQ1aaaa"}]
            [:navigate :note-edit {:book anna :note "Vh2xQ1aaaa"}]
            [:navigate :note-new {}]
            [:navigate :note-new {:book anna}]
            [:navigate :tags {}]
            [:back nil nil]]
           @nav))))
