(ns notebox.domain.json-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [notebox.domain.json :as json]
            [notebox.test.gen :as g]))

#?(:cljs
   (defn- js-stringify
     "What JSON.stringify writes for x."
     [x]
     (letfn [(to-js [v]
               (cond
                 (map? v) (let [o (js-obj)]
                            (doseq [[k val] v] (unchecked-set o (subs (str k) 1) (to-js val)))
                            o)
                 (sequential? v) (into-array (map to-js v))
                 :else v))]
       (js/JSON.stringify (to-js x)))))

(defspec encode-decode-round-trips 500
  (prop/for-all [x g/gen-json]
    (let [s (json/encode x)]
      (and (= x (json/decode s))
           (= s (json/encode (json/decode s)))))))

#?(:cljs
   (defspec encode-matches-js-json-stringify 500
     (prop/for-all [x g/gen-json]
       (= (js-stringify x) (json/encode x)))))

(deftest encodes-like-json-stringify
  (testing "escaping"
    (is (= "\"a\\\"b\\\\c/d\"" (json/encode "a\"b\\c/d")))
    (is (= "\"\\b\\f\\n\\r\\t\\u0000\\u001f\"" (json/encode "\b\f\n\r\t\u0000\u001f")))
    (is (= "\"\u2028\u2029\"" (json/encode "\u2028\u2029")) "line separators stay raw")
    (is (= "\"\\ud83d x \\ude00\"" (json/encode "\uD83D x \uDE00")) "lone surrogates")
    (is (= "\"😀\"" (json/encode "😀")) "pairs stay raw"))
  (testing "numbers"
    (is (= "[0,-1,9007199254740991,1,1.5]" (json/encode [0 -1 9007199254740991 1.0 1.5]))))
  (testing "structure"
    (is (= "{\"a\":[1,{\"b\":null}],\"c\":true,\"d\":false}"
           (json/encode (array-map :a [1 {:b nil}] :c true :d false))))
    (is (= "{\"a/b\":1,\"\":2,\"x y\":3}"
           (json/encode (array-map (keyword "a/b") 1 (keyword "") 2 (keyword "x y") 3)))
        "any key string survives as a keyword")
    (is (= "{\"s\":1}" (json/encode {"s" 1})) "string keys")
    (is (= "[]" (json/encode ())) "seqs are arrays"))
  (testing "unsupported values"
    (is (thrown? #?(:clj Exception :cljs :default) (json/encode {1 2})))
    (is (thrown? #?(:clj Exception :cljs :default) (json/encode #{1})))
    (is (thrown? #?(:clj Exception :cljs :default) (json/encode ##Inf)))
    (is (thrown? #?(:clj Exception :cljs :default) (json/encode ##NaN)))))

(deftest decodes-in-source-order
  (let [keys20 (map #(str "k" %) (range 20))
        text   (str "{" (apply str (interpose "," (map #(str "\"" % "\":0") (reverse keys20)))) "}")
        m      (json/decode text)]
    (is (= (map keyword (reverse keys20)) (keys m)) "more than 8 keys keep their order")
    (is (= text (json/encode m)))))

(deftest integer-like-keys-come-first-as-in-javascript
  (let [m (json/decode "{\"b\":1,\"10\":2,\"a\":3,\"2\":4,\"01\":5,\"4294967295\":6}")]
    (is (= [:2 :10 :b :a :01 :4294967295] (keys m))
        "array-index keys (< 2^32-1, canonical) first and ascending; the rest in order")
    (is (= "{\"2\":4,\"10\":2,\"b\":1,\"a\":3,\"01\":5,\"4294967295\":6}" (json/encode m)))))

(deftest decodes-json
  (is (= {:a [1 2.5 -3 1.0E21 true false nil "x"]}
         (json/decode " { \"a\" : [ 1 , 2.5, -3, 1e21, true, false, null, \"x\" ] } ")))
  (is (= "é😀/\b\f\n\r\t\"\\" (json/decode "\"\\u00e9\\ud83d\\ude00\\/\\b\\f\\n\\r\\t\\\"\\\\\"")))
  (is (= {:a 2} (json/decode "{\"a\":1,\"a\":2}")) "a repeated key: the last value wins")
  (is (= [(keyword "a/b")] (keys (json/decode "{\"a/b\":1}"))))
  (is (= [] (json/decode "[]")))
  (is (= {} (json/decode "{}")))
  (is (= 12345678901234567890.0 (json/decode "12345678901234567890"))))

(deftest rejects-invalid-json
  (doseq [bad ["" "{" "[1,]" "{\"a\" 1}" "{a:1}" "\"unterminated" "tru" "01" "1 2"
               "\"bad \\x escape\"" "\"bad \\u12 escape\"" "[1}" "{\"a\":1]"
               "\"raw\ncontrol\"" "-" "{\"a\":1,}" "nul"]]
    (testing (pr-str bad)
      (is (= :json/invalid
             (try (json/decode bad) nil
                  (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))))))
