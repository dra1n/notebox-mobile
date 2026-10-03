(ns notebox.domain.time
  "Timestamps as the web client writes them: `Date.prototype.toISOString`,
  e.g. \"2021-01-01T00:00:00.000Z\" (UTC, milliseconds).")

(defn iso-string
  "The ISO string of `epoch-ms` (milliseconds since 1970, UTC)."
  [epoch-ms]
  #?(:clj  (.format (.withZone (java.time.format.DateTimeFormatter/ofPattern
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                               java.time.ZoneOffset/UTC)
                    (java.time.Instant/ofEpochMilli (long epoch-ms)))
     :cljs (.toISOString (js/Date. epoch-ms))))
