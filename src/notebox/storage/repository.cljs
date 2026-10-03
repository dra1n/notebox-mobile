(ns notebox.storage.repository
  "Reading and changing /notes through Luggage (roadmap §6.3). An op
  (notebox.domain.ops) becomes: read the files it needs, apply the op, write
  what changed. Every op writes the meta, so ops run one at a time, in the
  order they were submitted: mobile never races itself.

  Online only: a failed read or write rejects with the Dropbox error
  (notebox.dropbox.errors); callers roll back."
  (:require [integrant.core :as ig]
            [notebox.domain.ops :as ops]
            [notebox.infra.js.luggage :as luggage]))

(def ^:private meta-properties
  "Meta properties written with writeMetaProperty. collectionsList is kept by
  Luggage's own create/delete."
  [:notesInfo :tagsInfo])

(defn repository [lug]
  {:luggage   lug
   :queue     (atom (js/Promise.resolve nil))
   :in-flight (atom 0)})

(defn- enqueue!
  "Runs `(f)` after everything submitted before it; Promise of its result."
  [{:keys [queue in-flight]} f]
  (swap! in-flight inc)
  (let [p (.then @queue (fn [_] (f)))]
    (reset! queue (.catch p (fn [_] nil)))
    (.finally p #(swap! in-flight dec))))

(defn load-meta
  "Promise of the meta file."
  [{lug :luggage}]
  (luggage/read-meta lug))

(defn load-book
  "Promise of a book's notes."
  [{lug :luggage} slug]
  (luggage/read-book lug slug))

(defn- read-files [lug op]
  (-> (js/Promise.all #js [(luggage/read-meta lug)
                           ;; :create too: an existing book's notes keep its meta right
                           (if (#{:write :create} (ops/book-file op))
                             (luggage/read-book lug (:book op))
                             (js/Promise.resolve nil))])
      (.then (fn [[m notes]] {:meta m :notes notes}))))

(defn- write-book-file [lug op meta-before result]
  (let [slug (:book op)]
    (case (ops/book-file op)
      :write  (luggage/write-book! lug slug (:notes result))
      ;; create writes [] unconditionally: only for a book that isn't there yet
      :create (if (some #{slug} (:collectionsList meta-before))
                (js/Promise.resolve nil)
                (luggage/create-book! lug slug))
      :delete (luggage/delete-book! lug slug)
      (js/Promise.resolve nil))))

(defn- write-meta [lug meta-before meta-after]
  (reduce (fn [p k]
            (if (= (get meta-before k) (get meta-after k))
              p
              (.then p #(luggage/write-meta-property! lug k (get meta-after k)))))
          (js/Promise.resolve nil)
          meta-properties))

(defn apply-op!
  "Applies `op` to Dropbox. Promise of {:meta :notes :warnings} as computed by
  notebox.domain.ops/apply-op."
  [{lug :luggage :as repo} op]
  (enqueue! repo
            (fn []
              (-> (read-files lug op)
                  (.then (fn [{m :meta :as files}]
                           (let [result (ops/apply-op op files)]
                             (-> (write-book-file lug op m result)
                                 (.then #(write-meta lug m (:meta result)))
                                 (.then (constantly result))))))))))

(defmethod ig/init-key :notebox.storage/repository [_ {:keys [luggage]}]
  (repository luggage))
