(ns notebox.storage.repository-test
  "Storage through Luggage (roadmap Phase 3 gate), in node against the fake
  Dropbox: the Phase 1 golden scenarios through Luggage, Luggage's edge cases,
  and the serial queue."
  (:require [cljs.test :refer [deftest is async testing]]
            [clojure.string :as str]
            [notebox.domain.json :as json]
            [notebox.dropbox.api :as api]
            [notebox.dropbox.errors :as errors]
            [notebox.dropbox.fake :as fake]
            [notebox.dropbox.fake-store :as fake-store]
            [notebox.infra.js.luggage :as luggage]
            [notebox.storage.repository :as repository]
            [notebox.test.async :as a]
            [notebox.test.io :as io]))

(def fixtures "test/resources/fixtures")

(defn- json-files [dir]
  (filter #(str/ends-with? % ".json") (io/list-dir dir)))

(defn export-files
  "{\"/notes/<file>\" data} of the Phase 1 fixture folder."
  []
  (let [dir (str fixtures "/notes-export")]
    (into {} (for [f (json-files dir)]
               [(str "/notes/" f) (json/decode (io/slurp-utf8 (str dir "/" f)))]))))

(defn- setup
  ([] (setup (export-files)))
  ([files]
   (let [dbx (fake/fake files)]
     {:dbx dbx :repo (repository/repository (luggage/luggage dbx))})))

(defn- text-of [dbx path]
  (get-in (fake-store/download @(:store dbx) path) [:ok :text]))

(defn- notes-files [dbx]
  (set (for [[p] (:files @(:store dbx)) :when (str/starts-with? p "/notes/")] p)))

(defn- lower [s] (.toLowerCase s))

(defn- run-scenario [name]
  (let [dir      (str fixtures "/scenarios/" name)
        scenario (json/decode (io/slurp-utf8 (str dir "/scenario.json")))
        ops      (map #(update % :op keyword) (:ops scenario))
        {:keys [dbx repo]} (setup)
        warnings (atom [])]
    (-> (reduce (fn [p op]
                  (.then p (fn [_] (.then (repository/apply-op! repo op)
                                          #(swap! warnings into (:warnings %))))))
                (js/Promise.resolve nil)
                ops)
        (.then (fn [_]
                 (testing name
                   (is (= (mapv #(update % :type keyword) (:warnings scenario)) @warnings))
                   (let [expected (str dir "/expected")
                         files    (json-files expected)]
                     (is (= (set (map #(lower (str "/notes/" %)) files)) (notes-files dbx))
                         "the same set of files")
                     (doseq [f files]
                       (is (= (io/slurp-utf8 (str expected "/" f))
                              (text-of dbx (str "/notes/" f)))
                           (str f " bytes"))))))))))

(deftest golden-scenarios-through-luggage
  (async done
    (let [names (io/list-dir (str fixtures "/scenarios"))]
      (is (= 12 (count names)))
      (a/run done (reduce (fn [p n] (.then p #(run-scenario n))) (js/Promise.resolve nil) names)))))

(deftest reads
  (async done
    (let [{:keys [repo]} (setup)]
      (a/run done
        (a/chain
         #(.then (repository/load-meta repo)
                 (fn [m]
                   (is (= [:collectionsList :notesInfo :tagsInfo :syncedBy] (keys m)))
                   (is (= 10 (count (:notesInfo m))))))
         #(.then (repository/load-book repo "k3J9aZ0qLx")
                 (fn [notes] (is (= ["Vh2xQ1aaaa" "Vh2xQ1bbbb" "Vh2xQ1cccc"] (map :slug notes)))))
         #(.then (repository/load-book repo "Gh0st-g0ne")
                 (fn [notes] (is (= [] notes) "a missing book file reads as []"))))))))

(deftest luggage-edge-cases
  (async done
    (let [{:keys [dbx repo]} (setup {})
          lug (:luggage repo)]
      (a/run done
        (a/chain
         #(.then (repository/load-meta repo) (fn [m] (is (= {} m) "a missing meta is {}")))
         #(repository/apply-op! repo {:op :book/create :book "b1" :title "One"})
         #(repository/apply-op! repo {:op :book/create :book "b2" :title "Two"})
         #(.then (repository/load-meta repo)
                 (fn [m]
                   (is (= ["b1" "b2"] (:collectionsList m)) "create appends to collectionsList")
                   (is (= ["b1" "b2"] (map :slug (:notesInfo m))))))
         #(.then (repository/apply-op! repo {:op :book/create :book "b1" :title "One again"})
                 (fn [_] (is (= "[]" (text-of dbx "/notes/b1.json")))))
         #(repository/apply-op! repo {:op :note/add :book "b1"
                                      :note (array-map :slug "n1" :title "x" :tags ["t"])})
         #(.then (repository/apply-op! repo {:op :book/create :book "b1" :title "Retried"})
                 (fn [_] (is (= "[{\"slug\":\"n1\",\"title\":\"x\",\"tags\":[\"t\"]}]"
                                (text-of dbx "/notes/b1.json"))
                             "a repeated create never wipes the book")))
         #(api/upload dbx "/notes/.meta.json"
                      (assoc (json/decode (text-of dbx "/notes/.meta.json")) :syncedBy "desktop")
                      {:mode :overwrite})
         #(repository/apply-op! repo {:op :book/delete :book "b2"})
         #(.then (repository/load-meta repo)
                 (fn [m]
                   (is (= ["b1"] (:collectionsList m)) "delete removes it from collectionsList")
                   (is (= ["b1"] (map :slug (:notesInfo m))))
                   (is (= {:b1 ["t"]} (:tagsInfo m)))
                   (is (= "desktop" (:syncedBy m)) "unknown meta keys survive")))
         #(is (nil? (text-of dbx "/notes/b2.json")) "the book file is deleted")
         #(.then (luggage/read-book lug "b1")
                 (fn [notes] (is (= [{:slug "n1" :title "x" :tags ["t"]}] notes)))))))))

(defrecord FailingDropbox [inner fail?]
  api/DropboxApi
  (download [_ p] (api/download inner p))
  (upload [_ p d o]
    (if (@fail? p)
      (js/Promise.reject (errors/->ex {:type :network :summary "offline"}))
      (api/upload inner p d o)))
  (delete [_ p] (api/delete inner p))
  (list-folder [_ p] (api/list-folder inner p))
  (current-account [_] (api/current-account inner)))

(deftest failed-writes-reject-with-the-client-error
  (async done
    (let [inner (fake/fake (export-files))
          fail? (atom #{"/notes/k3J9aZ0qLx.json"})
          repo  (repository/repository (luggage/luggage (->FailingDropbox inner fail?)))
          op    {:op :note/remove :book "k3J9aZ0qLx" :slug "Vh2xQ1aaaa"}]
      (a/run done
        (a/chain
         #(.then (a/error-type (repository/apply-op! repo op))
                 (fn [t] (is (= :network t))))
         #(.then (api/download inner "/notes/k3J9aZ0qLx.json")
                 (fn [{:keys [data]}] (is (= 3 (count data)) "the book is unchanged")))
         #(reset! fail? #{})
         #(.then (repository/apply-op! repo op)
                 (fn [{:keys [notes]}] (is (= 2 (count notes)) "the same op works once online"))))))))

(deftest ops-run-one-at-a-time-in-order
  (async done
    (let [{:keys [dbx repo]} (setup {})
          notes (for [i (range 6)] (array-map :slug (str "n" i) :title (str i)))]
      (a/run done
        (a/chain
         #(repository/apply-op! repo {:op :book/create :book "b" :title "B"})
         (fn []
           (let [ps (mapv #(repository/apply-op! repo {:op :note/add :book "b" :note %}) notes)]
             (is (= 6 @(:in-flight repo)) "all six are queued")
             (js/Promise.all (into-array ps))))
         (fn []
           (is (= 0 @(:in-flight repo)))
           (is (= (map :slug notes) (map :slug (json/decode (text-of dbx "/notes/b.json"))))
               "no add was lost, and they're in submission order")
           (is (= 6 (-> (json/decode (text-of dbx "/notes/.meta.json")) :notesInfo first :count))))
         ;; a failure doesn't stop the ops queued after it
         (fn []
           (let [bad  (repository/apply-op! repo {:op :note/frobnicate :book "b"})
                 good (repository/apply-op! repo {:op :note/remove :book "b" :slug "n0"})]
             (js/Promise.all #js [(a/error-type bad) good])))
         (fn [] (is (= 5 (count (json/decode (text-of dbx "/notes/b.json")))))))))))
