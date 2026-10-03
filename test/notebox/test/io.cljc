(ns notebox.test.io
  "File access for tests on the JVM and in node (paths relative to the project
  root, where both runners start).")

#?(:cljs (def ^:private fs (js/require "fs")))

(defn slurp-utf8 [path]
  #?(:clj  (slurp path :encoding "UTF-8")
     :cljs (.readFileSync fs path "utf8")))

(defn list-dir
  "File names in `dir`, sorted."
  [dir]
  (sort #?(:clj  (vec (.list (java.io.File. ^String dir)))
           :cljs (vec (.readdirSync fs dir)))))

(defn file-exists? [path]
  #?(:clj  (.exists (java.io.File. ^String path))
     :cljs (.existsSync fs path)))
