(ns notebox.dropbox.pkce-test
  (:require [clojure.test :refer [deftest is]]
            [notebox.dropbox.pkce :as pkce]))

(def rfc-7636-bytes
  "RFC 7636 appendix B: the 32 octets of the example code verifier."
  [116 24 223 180 151 153 224 37 79 250 96 125 216 173 187 186
   22 212 37 77 105 214 191 240 91 88 5 88 83 132 141 121])

(deftest rfc-7636-appendix-b
  (is (= "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk" (pkce/verifier-from-bytes rfc-7636-bytes)))
  (is (= "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
         (pkce/challenge "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))))

(deftest random-verifiers
  (let [vs (repeatedly 200 pkce/new-verifier)]
    (is (every? #(re-matches #"[A-Za-z0-9_-]{43}" %) vs) "43 chars, base64url, no padding")
    (is (apply distinct? vs))
    (is (every? #(re-matches #"[A-Za-z0-9_-]{43}" (pkce/challenge %)) vs))))
