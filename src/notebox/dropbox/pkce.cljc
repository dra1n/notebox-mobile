(ns notebox.dropbox.pkce
  "PKCE (RFC 7636) for the OAuth code flow: a random verifier and its S256
  challenge, both base64url without padding."
  #?(:cljs (:require [goog.crypt :as crypt]
                     [goog.crypt.base64 :as b64]
                     [goog.crypt.Sha256])))

(defn- base64url [bytes]
  #?(:clj  (.encodeToString (.withoutPadding (java.util.Base64/getUrlEncoder))
                            (byte-array (map unchecked-byte bytes)))
     :cljs (b64/encodeByteArray (clj->js (map #(bit-and % 0xFF) bytes))
                                b64/Alphabet.WEBSAFE_NO_PADDING)))

(defn verifier-from-bytes
  "The code verifier for `bytes` (32 random octets → 43 characters)."
  [bytes]
  (base64url bytes))

(defn random-bytes [n]
  #?(:clj  (let [b (byte-array n)] (.nextBytes (java.security.SecureRandom.) b) (vec b))
     :cljs (vec (js/crypto.getRandomValues (js/Uint8Array. n)))))

(defn new-verifier []
  (verifier-from-bytes (random-bytes 32)))

(defn challenge
  "The S256 code challenge of `verifier`: base64url(SHA-256(ASCII verifier))."
  [verifier]
  #?(:clj  (base64url (.digest (java.security.MessageDigest/getInstance "SHA-256")
                               (.getBytes ^String verifier "US-ASCII")))
     :cljs (let [h (goog.crypt.Sha256.)]
             (.update h (crypt/stringToByteArray verifier))
             (base64url (vec (.digest h))))))
