(ns notebox.dropbox.fake-store
  "The state machine of an in-memory Dropbox: files with revs, as the real API
  behaves for the calls we use. Shared by the fake API (notebox.dropbox.fake)
  and the fake HTTP server the client is tested against, so both agree.

  Results are {:ok value} or {:error summary}, where summary is a Dropbox
  error_summary (\"path/not_found/..\", \"path/conflict/file/..\")."
  (:require [clojure.string :as str]))

(def empty-store
  {:files    {}
   :next-rev 1
   :account  {:account_id "dbid:fake-account" :email "fake@example.com"
              :name {:display_name "Fake User"}}})

(defn- lower [s] #?(:clj (.toLowerCase ^String s java.util.Locale/ROOT) :cljs (.toLowerCase s)))

(defn- rev-string [n]
  (let [h #?(:clj (Long/toHexString n) :cljs (.toString n 16))]
    (str "0" (subs "00000000000000" (count h)) h)))

(defn- parent [path]
  (let [i (str/last-index-of path "/")]
    (if (pos? i) (subs path 0 i) "")))

(defn- file-name [path]
  (subs path (inc (str/last-index-of path "/"))))

(defn download [store path]
  (if-let [f (get-in store [:files (lower path)])]
    {:ok (select-keys f [:text :rev])}
    {:error "path/not_found/."}))

(defn- write [store path text]
  (let [rev (rev-string (:next-rev store))]
    [(-> store
         (assoc-in [:files (lower path)] {:path path :text text :rev rev})
         (update :next-rev inc))
     {:ok {:rev rev}}]))

(defn upload
  "Upload `text` to `path`. `rev` nil means mode add (never overwrite);
  otherwise mode update (only over that rev). Identical content is never a
  conflict (strict_conflict is off)."
  [store path text rev]
  (let [existing (get-in store [:files (lower path)])]
    (cond
      (and existing (= text (:text existing)))
      [store {:ok {:rev (:rev existing)}}]

      (nil? rev)
      (if existing [store {:error "path/conflict/file/."}] (write store path text))

      (and existing (= rev (:rev existing)))
      (write store path text)

      :else
      [store {:error "path/conflict/file/."}])))

(defn delete [store path]
  (if (get-in store [:files (lower path)])
    [(update store :files dissoc (lower path)) {:ok nil}]
    [store {:error "path_lookup/not_found/."}]))

(defn list-folder
  "The files directly in `folder` (only files are stored), sorted by name."
  [store folder]
  (let [folder (lower folder)
        files  (filter #(= folder (parent (key %))) (:files store))]
    (if (or (seq files) (some #(str/starts-with? (key %) (str folder "/")) (:files store)))
      {:ok (vec (sort-by :name (for [[p f] files]
                                 {:name (file-name (:path f)) :path p :rev (:rev f)})))}
      {:error "path/not_found/."})))

(defn account [store]
  {:ok (:account store)})
