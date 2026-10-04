(ns notebox.e2e.seed
  "Compile-time access to the Phase 1 fixture library (test/resources/fixtures/
  notes-export), embedded into the e2e build only.")

(defmacro fixture-files
  "{\"/notes/<file>\" json-text} of the fixture folder, read when compiling."
  []
  (let [dir (java.io.File. "test/resources/fixtures/notes-export")]
    (into (sorted-map)
          (for [f (.listFiles dir) :when (.endsWith (.getName f) ".json")]
            [(str "/notes/" (.getName f)) (slurp f :encoding "UTF-8")]))))
