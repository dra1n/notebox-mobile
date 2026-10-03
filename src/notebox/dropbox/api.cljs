(ns notebox.dropbox.api
  "What the rest of the app needs from Dropbox. Implemented by the HTTP client
  (notebox.dropbox.client) and the in-memory fake (notebox.dropbox.fake), which
  pass the same contract tests. Every call returns a Promise; failures reject
  with ex-data as in notebox.dropbox.errors.")

(defprotocol DropboxApi
  (download [this path]
    "{:data parsed-json :rev r}; {:data nil :rev nil} if there's no such file.")
  (upload [this path data opts]
    "Writes `data` as JSON. opts {:mode :overwrite}: replace whatever is there.
    {:rev r}: only over that revision. Neither: only if the file doesn't exist.
    Identical content is never a conflict. Resolves {:rev new-rev}; a lost race
    rejects with :conflict.")
  (delete [this path]
    "Deletes the file; nil, also when it was already gone.")
  (list-folder [this path]
    "[{:name :path :rev} ...] of the files in folder `path`; [] if it's missing.")
  (current-account [this]
    "{:account-id :email :name}."))
