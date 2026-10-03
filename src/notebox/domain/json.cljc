(ns notebox.domain.json
  "JSON codec for the Dropbox files, compatible with the web and desktop clients.

  - `encode` produces exactly what JavaScript's `JSON.stringify` produces for the
    same value: compact, object keys in map order, the same string escaping
    (ES2019 well-formed: lone surrogates as \\udxxx), integral numbers without a
    fraction.
  - `decode` keeps object keys in source order (objects become array maps of any
    size) and turns keys into keywords, as the web (`js->clj :keywordize-keys`)
    and desktop (`cheshire/parse-string s true`) clients do. Keys are encoded
    back from `(subs (str k) 1)`, so any key string round-trips, even \"a/b\".

  Key order follows JavaScript exactly: like `JSON.parse`, `decode` puts
  integer-like keys (\"0\", \"42\": array indices) first, in numeric order, then
  the other keys in source order. So both platforms decode identically.

  Together: `(encode (decode s))` is byte-identical to `s` for anything the web
  client wrote (JSON.stringify never writes integer-like keys after others).
  Use `notebox.domain.ordered` to change maps without losing key order.

  Not supported (never present in our data): non-finite numbers, and on the
  JVM, non-integral doubles are printed by Java rather than JS rules."
  (:require [clojure.string :as str]))

;; --- encode -------------------------------------------------------------------

(defn- code-unit [s i]
  #?(:clj  (int (.charAt ^String s (int i)))
     :cljs (.charCodeAt s i)))

(defn- hex4 [n]
  (let [h #?(:clj (Integer/toHexString n) :cljs (.toString n 16))]
    (str (subs "0000" (count h)) h)))

(defn- high-surrogate? [c] (<= 0xD800 c 0xDBFF))
(defn- low-surrogate? [c] (<= 0xDC00 c 0xDFFF))

(def ^:private needs-escape
  "Matches a string with anything JSON.stringify escapes (or a surrogate, which
  might be lone)."
  #"[\"\\\x00-\x1F\uD800-\uDFFF]")

(defn- escape-char
  "The escape for code unit `c`, or nil if it's written as is."
  [c]
  (cond
    (== c 0x22) "\\\""
    (== c 0x5C) "\\\\"
    (== c 0x08) "\\b"
    (== c 0x0C) "\\f"
    (== c 0x0A) "\\n"
    (== c 0x0D) "\\r"
    (== c 0x09) "\\t"
    (< c 0x20) (str "\\u" (hex4 c))))

(defn- escape-string
  "The JSON.stringify quoting of string `s`. Unescaped runs are copied whole."
  [s]
  (if-not (re-find needs-escape s)
    (str "\"" s "\"")
    (let [n (count s)
          sb #?(:clj (StringBuilder. (+ n 8)) :cljs (array))
          add! (fn [x] #?(:clj (.append sb ^String x) :cljs (.push sb x)))]
      (add! "\"")
      (loop [i 0 run 0]
        (if (< i n)
          (let [c     (code-unit s i)
                pair? (and (high-surrogate? c) (< (inc i) n)
                           (low-surrogate? (code-unit s (inc i))))
                esc   (cond
                        pair? nil
                        (or (high-surrogate? c) (low-surrogate? c)) (str "\\u" (hex4 c))
                        :else (escape-char c))]
            (if esc
              (do (when (< run i) (add! (subs s run i)))
                  (add! esc)
                  (recur (inc i) (inc i)))
              (recur (if pair? (+ i 2) (inc i)) run)))
          (when (< run n) (add! (subs s run n)))))
      (add! "\"")
      #?(:clj (.toString sb) :cljs (.join sb "")))))

(defn- encode-number [x]
  #?(:clj  (cond
             (integer? x) (str x)
             (and (Double/isFinite (double x))
                  (== x (Math/rint (double x)))
                  (< (Math/abs (double x)) 1e21))
             (str (long x))
             (Double/isFinite (double x)) (str (double x))
             :else (throw (ex-info "JSON can't encode a non-finite number" {:value x})))
     :cljs (if (js/isFinite x)
             (str x)
             (throw (ex-info "JSON can't encode a non-finite number" {:value x})))))

(defn- key-string [k]
  (cond
    (keyword? k) (subs (str k) 1)
    (string? k)  k
    :else (throw (ex-info "JSON object keys must be keywords or strings" {:key k}))))

(defn encode
  "The compact JSON text of `x`, as JSON.stringify would write it."
  [x]
  (cond
    (nil? x)        "null"
    (true? x)       "true"
    (false? x)      "false"
    (string? x)     (escape-string x)
    (number? x)     (encode-number x)
    (map? x)        (str "{"
                         (str/join "," (map (fn [[k v]]
                                              (str (escape-string (key-string k)) ":" (encode v)))
                                            x))
                         "}")
    (sequential? x) (str "[" (str/join "," (map encode x)) "]")
    :else (throw (ex-info "JSON can't encode this value" {:value x}))))

;; --- decode -------------------------------------------------------------------

#?(:clj
   (defn- array-index
     "The array index a key string stands for in JavaScript, or nil."
     [^String k]
     (when (re-matches #"0|[1-9][0-9]{0,9}" k)
       (let [n (Long/parseLong k)]
         (when (< n 4294967295) n)))))

#?(:clj
   (defn- js-key-order
     "Key-value pairs in the order a JS object enumerates them: array-index keys
     first, ascending, then the rest in insertion order."
     [kvs]
     (let [pairs (partition 2 kvs)
           index (fn [[k]] (array-index (subs (str k) 1)))
           {ints true others false} (group-by #(boolean (index %)) pairs)]
       (if (seq ints)
         (into [] cat (concat (sort-by index ints) others))
         kvs))))

(defn- obj
  "An array map of key-value pairs in order; a repeated key keeps its first
  position and its last value (as JSON.parse does)."
  [kvs]
  (let [arr #?(:clj (object-array kvs) :cljs (into-array kvs))]
    #?(:clj  (clojure.lang.PersistentArrayMap/createAsIfByAssoc arr)
       :cljs (.createAsIfByAssoc PersistentArrayMap arr))))

#?(:cljs
   (defn from-js
     "Plain JS data (as JSON.parse returns it) → the same data as `decode` gives:
     key-ordered maps with keyword keys, vectors. For JS interop edges."
     [x]
     (cond
       (array? x) (mapv from-js x)
       (and (some? x) (identical? (type x) js/Object))
       (obj (mapcat (fn [k] [(keyword k) (from-js (unchecked-get x k))])
                    (js-keys x)))
       :else x)))

#?(:cljs
   (defn to-js
     "The inverse of `from-js`: plain JS objects and arrays whose keys are in map
     order, so JSON.stringify of the result equals `encode`."
     [x]
     (cond
       (map? x)        (let [o (js-obj)]
                         (doseq [[k v] x] (unchecked-set o (key-string k) (to-js v)))
                         o)
       (sequential? x) (into-array (map to-js x))
       :else x)))

#?(:clj
   (defn- parse
     "A strict JSON parser (RFC 8259) for the JVM; the app uses JSON.parse."
     [^String s]
     (let [n   (.length s)
           pos (int-array 1)
           fail (fn [msg] (throw (ex-info (str "Invalid JSON: " msg " at " (aget pos 0))
                                          {:type :json/invalid :position (aget pos 0)})))
           peek (fn [] (if (< (aget pos 0) n) (.charAt s (aget pos 0)) \u0000))
           adv  (fn [] (aset pos 0 (inc (aget pos 0))))
           ws   (fn [] (while (and (< (aget pos 0) n)
                                   (#{\space \tab \newline \return} (peek)))
                         (adv)))
           expect (fn [^String lit]
                    (if (.startsWith s lit (aget pos 0))
                      (aset pos 0 (+ (aget pos 0) (.length lit)))
                      (fail (str "expected " lit))))]
       (letfn [(value []
                 (ws)
                 (let [c (peek)]
                   (cond
                     (= c \{) (object)
                     (= c \[) (array)
                     (= c \") (string)
                     (= c \t) (do (expect "true") true)
                     (= c \f) (do (expect "false") false)
                     (= c \n) (do (expect "null") nil)
                     (or (= c \-) (Character/isDigit c)) (number)
                     :else (fail "unexpected character"))))
               (object []
                 (adv) (ws)
                 (if (= (peek) \})
                   (do (adv) (obj []))
                   (loop [kvs (transient [])]
                     (ws)
                     (when-not (= (peek) \") (fail "expected a key"))
                     (let [k (string)]
                       (ws)
                       (when-not (= (peek) \:) (fail "expected :"))
                       (adv)
                       (let [kvs (conj! (conj! kvs (keyword k)) (value))]
                         (ws)
                         (case (peek)
                           \, (do (adv) (recur kvs))
                           \} (do (adv) (obj (js-key-order (persistent! kvs))))
                           (fail "expected , or }")))))))
               (array []
                 (adv) (ws)
                 (if (= (peek) \])
                   (do (adv) [])
                   (loop [xs (transient [])]
                     (let [xs (conj! xs (value))]
                       (ws)
                       (case (peek)
                         \, (do (adv) (recur xs))
                         \] (do (adv) (persistent! xs))
                         (fail "expected , or ]"))))))
               (string []
                 (adv)
                 (let [sb (StringBuilder.)]
                   (loop []
                     (when (>= (aget pos 0) n) (fail "unterminated string"))
                     (let [c (peek)]
                       (adv)
                       (cond
                         (= c \") (.toString sb)
                         (= c \\)
                         (let [e (peek)]
                           (adv)
                           (case e
                             \" (.append sb \")
                             \\ (.append sb \\)
                             \/ (.append sb \/)
                             \b (.append sb \backspace)
                             \f (.append sb \formfeed)
                             \n (.append sb \newline)
                             \r (.append sb \return)
                             \t (.append sb \tab)
                             \u (let [p (aget pos 0)]
                                  (when (> (+ p 4) n) (fail "bad \\u escape"))
                                  (let [h (subs s p (+ p 4))]
                                    (when-not (re-matches #"[0-9a-fA-F]{4}" h)
                                      (fail "bad \\u escape"))
                                    (.append sb (char (Integer/parseInt h 16)))
                                    (aset pos 0 (+ p 4))))
                             (fail "bad escape"))
                           (recur))
                         (< (int c) 0x20) (fail "control character in string")
                         :else (do (.append sb c) (recur)))))))
               (number []
                 (let [start (aget pos 0)
                       m (re-find #"^-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?"
                                  (subs s start))]
                   (when-not m (fail "bad number"))
                   (aset pos 0 (+ start (count (first m))))
                   (if (or (nth m 2) (nth m 3))
                     (Double/parseDouble (first m))
                     (let [b (bigint (first m))]
                       (if (<= Long/MIN_VALUE b Long/MAX_VALUE) (long b) (double b))))))]
         (let [v (value)]
           (ws)
           (when (< (aget pos 0) n) (fail "trailing characters"))
           v)))))

(defn decode
  "The data in JSON text `s`: objects as key-ordered maps with keyword keys,
  arrays as vectors. Throws ex-info {:type :json/invalid} on bad input."
  [s]
  #?(:clj  (parse s)
     :cljs (let [parsed (try (js/JSON.parse s)
                             (catch :default e
                               (throw (ex-info (str "Invalid JSON: " (.-message e))
                                               {:type :json/invalid}))))]
             (from-js parsed))))
