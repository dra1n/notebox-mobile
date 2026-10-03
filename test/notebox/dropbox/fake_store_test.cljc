(ns notebox.dropbox.fake-store-test
  (:require [clojure.test :refer [deftest is testing]]
            [notebox.dropbox.fake-store :as fs]))

(deftest revs-and-conflicts
  (let [[s1 r1] (fs/upload fs/empty-store "/notes/A.json" "[1]" :add)
        rev1    (get-in r1 [:ok :rev])]
    (is (string? rev1))
    (testing "add over an existing file: conflict, unless identical"
      (is (= [s1 {:error "path/conflict/file/."}] (fs/upload s1 "/notes/a.json" "[2]" :add)))
      (is (= [s1 {:ok {:rev rev1}}] (fs/upload s1 "/notes/a.json" "[1]" :add))))
    (testing "overwrite replaces whatever is there, or creates"
      (let [[s2 r2] (fs/upload s1 "/notes/a.json" "[9]" :overwrite)]
        (is (not= rev1 (get-in r2 [:ok :rev])))
        (is (= "[9]" (get-in (fs/download s2 "/notes/a.json") [:ok :text]))))
      (is (contains? (second (fs/upload s1 "/notes/new.json" "[]" :overwrite)) :ok)))
    (testing "update with the current rev"
      (let [[s2 r2] (fs/upload s1 "/notes/a.json" "[2]" rev1)]
        (is (not= rev1 (get-in r2 [:ok :rev])))
        (is (= {:ok {:text "[2]" :rev (get-in r2 [:ok :rev])}} (fs/download s2 "/NOTES/a.json")))
        (testing "a stale rev is a conflict"
          (is (= [s2 {:error "path/conflict/file/."}] (fs/upload s2 "/notes/a.json" "[3]" rev1))))))
    (testing "update of a missing file is a conflict"
      (is (= {:error "path/conflict/file/."} (second (fs/upload s1 "/notes/b.json" "[]" rev1)))))
    (testing "download, delete, list"
      (is (= {:error "path/not_found/."} (fs/download s1 "/notes/b.json")))
      (is (= {:ok [{:name "A.json" :path "/notes/a.json" :rev rev1}]} (fs/list-folder s1 "/Notes")))
      (is (= {:error "path/not_found/."} (fs/list-folder s1 "/other")))
      (let [[s3 r3] (fs/delete s1 "/notes/a.json")]
        (is (= {:ok nil} r3))
        (is (= {:error "path_lookup/not_found/."} (second (fs/delete s3 "/notes/a.json"))))
        (is (= {:error "path/not_found/."} (fs/list-folder s3 "/notes")))))
    (testing "only direct children are listed"
      (let [[s4] (fs/upload s1 "/notes/sub/x.json" "[]" :add)]
        (is (= ["A.json"] (map :name (:ok (fs/list-folder s4 "/notes")))))
        (is (= ["x.json"] (map :name (:ok (fs/list-folder s4 "/notes/sub")))))))
    (is (= "fake@example.com" (get-in (fs/account s1) [:ok :email])))))
