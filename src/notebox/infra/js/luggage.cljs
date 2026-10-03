(ns notebox.infra.js.luggage
  "Luggage (@luggage/core), the storage abstraction the web client uses: the one
  namespace that touches it (roadmap §5.2 item 7, §6.3).

  - We load Luggage's core only (build/Luggage), not the package index, so the
    Dropbox SDK and its Blob/FileReader shims stay out of the app.
  - Luggage's backend contract is implemented here, in ClojureScript, over our
    Dropbox client (notebox.dropbox.api): login, token refresh, retries and
    error types come with it. Writes overwrite, as Luggage's own backend does.
  - Everything this namespace exposes takes and returns ClojureScript data
    (ordered maps, see notebox.domain.json) and Promises; JS objects don't leak.

  Luggage's find(...).update/delete aren't exposed: they have the index -1 bug
  (roadmap §3.4 hazard 3). Changes are computed by notebox.domain.ops and
  written as whole files."
  (:require ["@luggage/core/build/Luggage" :as luggage-module]
            [integrant.core :as ig]
            [notebox.domain.json :as json]
            [notebox.dropbox.api :as api]))

(def ^:private Luggage (.-default luggage-module))

(def collections-name "notes")

(defn- file-path [collection-name] (str "/" collection-name ".json"))
(defn- meta-path [collections-name] (str "/" collections-name "/.meta.json"))

(defn- read-json [dbx path missing]
  (.then (api/download dbx path)
         (fn [{:keys [data]}] (json/to-js (if (nil? data) missing data)))))

(defn- write-json [dbx path ^js data]
  (.then (api/upload dbx path (json/from-js data) {:mode :overwrite})
         (constantly data)))

(defn backend
  "A Luggage backend (its five-method contract) over DropboxApi `dbx`."
  [dbx]
  #js {:collection
       (fn [name]
         (let [path (file-path name)]
           #js {:read   (fn [] (read-json dbx path []))
                :write  (fn [data] (write-json dbx path (or data #js [])))
                :delete (fn [] (api/delete dbx path))}))
       :collections
       (fn [name]
         (let [path (meta-path name)]
           #js {:readMetaInfo  (fn [] (read-json dbx path {}))
                :writeMetaInfo (fn [data] (write-json dbx path data))}))})

(defn luggage
  "{:collections <Luggage Collections of /notes> :dbx dbx}."
  [dbx]
  {:dbx dbx
   :collections (.collections (Luggage. (backend dbx)) collections-name)})

;; --- the CLJS API ---------------------------------------------------------------

(defn- ^js colls [lug] (:collections lug))

(defn read-meta
  "Promise of the whole meta file ({} if missing). Read through the same backend
  Luggage uses; Luggage itself only reads one property at a time."
  [lug]
  (.then (api/download (:dbx lug) (meta-path collections-name))
         (fn [{:keys [data]}] (or data (array-map)))))

(defn write-meta-property!
  "Luggage writeMetaProperty: re-reads the meta, sets one property, writes it."
  [lug k value]
  (.then (.writeMetaProperty (colls lug) (name k) (json/to-js value))
         (constantly nil)))

(defn read-book
  "Promise of the book's notes ([] if the file is missing)."
  [lug slug]
  (.then (.read (.getInstance (colls lug) slug)) json/from-js))

(defn write-book!
  [lug slug notes]
  (.then (.write (.getInstance (colls lug) slug) (json/to-js (vec notes)))
         (constantly nil)))

(defn create-book!
  "Luggage create: writes [] and appends the slug to collectionsList."
  [lug slug]
  (.then (.create (colls lug) slug) (constantly nil)))

(defn delete-book!
  "Luggage delete: removes the slug from collectionsList, then the file."
  [lug slug]
  (.then (.delete (colls lug) slug) (constantly nil)))

(defmethod ig/init-key :notebox.infra.js/luggage [_ {:keys [client]}]
  (luggage client))
