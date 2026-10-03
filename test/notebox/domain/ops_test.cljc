(ns notebox.domain.ops-test
  "Properties every op must have (spec/roadmap.md §6.2), plus examples."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [notebox.domain.meta :as meta]
            [notebox.domain.note :as note]
            [notebox.domain.ops :as ops]
            [notebox.test.gen :as g]))

(def gen-state
  (gen/tuple g/gen-meta g/gen-notes))

(defn- files [r] (select-keys r [:meta :notes]))

(defspec every-op-is-idempotent 300
  (prop/for-all [[m notes] gen-state
                 op g/gen-op]
    (let [once  (ops/apply-op op {:meta m :notes notes})
          twice (ops/apply-op op (files once))]
      (= (files once) (files twice)))))

(defspec note-ops-leave-meta-derived-from-content 300
  (prop/for-all [[m notes] gen-state
                 op (gen/one-of [g/gen-note-op
                                 (gen/fmap (fn [b] {:op :book/create :book b :title "New"}) g/gen-book)])]
    (let [{m' :meta notes' :notes} (ops/apply-op op {:meta m :notes notes})
          b (:book op)]
      (and (= (note/distinct-tags notes') (meta/book-tags m' b))
           (if-let [info (meta/book-info m' b)]
             (= (count notes') (:count info))
             true)))))

(defspec slugs-stay-unique 300
  (prop/for-all [notes g/gen-notes
                 op-seq (gen/vector g/gen-note-op 0 15)]
    (let [final (reduce (fn [ns op] (:notes (ops/apply-op op {:meta {} :notes ns})))
                        notes
                        op-seq)]
      (apply distinct? nil (map :slug final)))))

(defspec ops-on-missing-targets-change-nothing-but-derived-meta 300
  (prop/for-all [[m notes] gen-state
                 b g/gen-book]
    (let [slug    "missing000"                  ; never in g/slug-pool
          derived (meta/refresh-book m b notes)]
      (and (= {:meta derived :notes notes :warnings []}
              (ops/apply-op {:op :note/remove :book b :slug slug} {:meta m :notes notes}))
           (if (meta/book-info m b)
             true
             (= m (:meta (ops/apply-op {:op :book/rename :book b :title "x"} {:meta m :notes notes}))))
           (if (or (meta/book-info m b) (some #{b} (:collectionsList m))
                   (contains? (:tagsInfo m) (keyword b)))
             true
             (= m (:meta (ops/apply-op {:op :book/delete :book b} {:meta m :notes notes}))))))))

(defspec unknown-keys-survive-every-op 200
  (prop/for-all [[m notes] gen-state
                 op g/gen-op]
    (let [m (assoc m :syncedBy "desktop")]
      (= "desktop" (:syncedBy (:meta (ops/apply-op op {:meta m :notes notes})))))))

(deftest examples
  (let [m     (meta/create-book {} "b1" "Book")
        n1    (note/new-note {:slug "n1" :title "T" :text "x" :tags ["a"] :created-at "t0"})]
    (testing "add, update (merge + updated-at), remove"
      (let [r1 (ops/apply-op {:op :note/add :book "b1" :note n1} {:meta m :notes []})
            r2 (ops/apply-op {:op :note/update :book "b1"
                              :note (note/edit n1 {:tags ["a" "b"]} "t1")}
                             (files r1))
            r3 (ops/apply-op {:op :note/remove :book "b1" :slug "n1"} (files r2))]
        (is (= [n1] (:notes r1)))
        (is (= 1 (:count (meta/book-info (:meta r1) "b1"))))
        (is (= ["a" "b"] (meta/book-tags (:meta r2) "b1")))
        (is (= [:slug :title :text :tags :created-at :updated-at] (keys (first (:notes r2)))))
        (is (= [] (:notes r3)))
        (is (= 0 (:count (meta/book-info (:meta r3) "b1"))))
        (is (= [] (meta/book-tags (:meta r3) "b1")))))
    (testing "update of a missing note re-adds it with a warning"
      (let [r (ops/apply-op {:op :note/update :book "b1" :note n1} {:meta m :notes []})]
        (is (= [n1] (:notes r)))
        (is (= [{:type :note/re-added :book "b1" :slug "n1"}] (:warnings r)))))
    (testing "which files each op touches"
      (is (= {:note/add :write :note/update :write :note/remove :write
              :book/create :create :book/rename nil :book/delete :delete}
             (into {} (map (fn [t] [t (ops/book-file {:op t})])) ops/op-types))))
    (testing "create, rename, delete a book"
      (let [m2 (:meta (ops/apply-op {:op :book/create :book "b2" :title "Two"} {:meta m :notes nil}))
            m3 (:meta (ops/apply-op {:op :book/rename :book "b2" :title "Deux"} {:meta m2}))
            r4 (ops/apply-op {:op :book/delete :book "b2"} {:meta m3 :notes []})]
        (is (= ["b1" "b2"] (:collectionsList m2)))
        (is (= [] (meta/book-tags m2 "b2")))
        (is (= "Deux" (:title (meta/book-info m3 "b2"))))
        (is (= m (:meta r4)))
        (is (nil? (:notes r4)))))
    (testing "an unknown op is an error"
      (doseq [f [ops/book-file #(ops/apply-op % {:meta m :notes []})]]
        (is (= :op/unknown
               (try (f {:op :note/frobnicate :book "b1"}) nil
                    (catch #?(:clj Exception :cljs :default) e (:type (ex-data e))))))))
    (testing "totals"
      (is (= {:books 1 :notes 0} (meta/totals m)))
      (is (= {:books 0 :notes 0} (meta/totals {}))))))
