(ns clojobuf-codec.deserialize
  (:require [cljc-long.constants :as long-const]
            [clojobuf-codec.io.reader :refer
             #?(:clj  [ByteReader read-byte read-bytearray])
             #?(:cljs [ByteReader read-byte])]
            [clojobuf-codec.util :refer
             #?(:clj  [>>> << raise])
             #?(:cljs [>>> << >> make-HL32 HL32->n64 zigzag-decode-HL32])])
  #?(:clj (:import [java.nio.charset StandardCharsets])))

(declare read-bytes)

;-----------------------------------------------------------------------
; CLJ
;-----------------------------------------------------------------------
#?(:clj (defn- read-varint [in]
          (loop [char (read-byte ^ByteReader in)
                 num 0
                 result 0]
            (if (= char -1) (raise "Unexpected end of stream when reading varint.")
                (let [merged-int (-> char
                                     (bit-and 2r01111111)
                                     (<< (* num 7))
                                     (bit-or result))]
                  (if (= 0 (bit-and char 2r10000000))
                    merged-int
                    (recur (read-byte in)
                           (inc num)
                           merged-int)))))))

#?(:clj (defn read-varint32 [in _signed_]
          (read-varint in)))

#?(:clj (defn read-varint64 [in _signed_]
          (read-varint in)))

#?(:clj (defn- read-zigzag-varint
          "Reads varint with zigzag encoding, i.e. sint32 and sint64."
          [in]
          (let [zigzag-varint (read-varint ^ByteReader in)
                magnitude (>>> zigzag-varint 1)]
            (if (not= 0 (bit-and zigzag-varint 2r1)) ; negative
              (if (= magnitude 0) long-const/min-value (- (- magnitude) 1))
              magnitude))))

#?(:clj (defn- read-zigzag-varint32 [in]
          (read-zigzag-varint in)))

#?(:clj (defn- read-zigzag-varint64 [in]
          (read-zigzag-varint in)))

#?(:clj (defn read-float [in]
          (let [data (read-bytes in 4)
                sign (not= (bit-and data 0x80000000) 0)
                value (if sign
                        (-> data (bit-not) (bit-and 0xFFFFFFFF) (+ 1) (-))
                        data)]
            (Float/intBitsToFloat value))))

#?(:clj (defn read-double [in]
          (let [data (read-bytes in 8)
                sign (not= (>>> data 63) 0)
                value (if sign
                        (-> data (bit-not) (+ 1) (-))
                        data)]
            (Double/longBitsToDouble value))))

#?(:clj (defn read-text [in]
          (let [len (read-varint32 in false)
                ba (read-bytearray in len)]
            (String. ba StandardCharsets/UTF_8))))

;-----------------------------------------------------------------------
; CLJS
;-----------------------------------------------------------------------
#?(:cljs (defn- read-varint-as-hi32 [in start-val]
           (loop [data (read-byte ^ByteReader in)
                  lshift 3 ; +7 on recur
                  hi32 start-val] ; start-val has 3 bits value
             (let [hi32m (-> data
                             (bit-and 127)
                             (<< lshift)
                             (bit-or hi32)
                             (>>> 0))]
               (if (= 0 (bit-and data 128))
                 hi32m
                 (if (= lshift 31) ; 3 + 7 * 0..4 => [3, 10, 17, 24, 31]. at 5th data, recur will exceeded 10 bytes
                   (raise "Invalid varint encoding (exceeded max 10 bytes)")
                   (recur (read-byte in) (+ lshift 7) hi32m)))))))

#?(:cljs (defn- read-varint-as-HL32 [in]
           (loop [data (read-byte ^ByteReader in)
                  lshift 0 ; +7 on recur
                  lo32 0]
             (let [lo32m (-> data ; lo32m: is lo32 merged with right-most 7 bits of data
                             (bit-and 127)
                             (<< lshift)
                             (bit-or lo32)
                             (>>> 0))]
               (if (= lshift 28) ; 7 * 0..4 => [0, 7, 14, 21, 28]. at 5th data, start handle hi32
                 (let [hi32 (-> data (bit-and 127) (>> 4) (>>> 0))] ; top 3 bits of current data go to hi32
                   (if (= 0 (bit-and data 128))
                     (make-HL32 hi32 lo32m)
                     (make-HL32 (read-varint-as-hi32 in hi32) lo32m)))
                 (if (= 0 (bit-and data 128))
                   (make-HL32 0 lo32m)
                   (recur (read-byte in) (+ lshift 7) lo32m)))))))

#?(:cljs (defn read-varint64
           ([in signed]
            (let [hl32 (read-varint-as-HL32 in)]
              (HL32->n64 hl32 signed)))))

#?(:cljs (defn- read-varint32 [in signed]
           (loop [char (read-byte ^ByteReader in)
                  lshift 0 ; +7 on recur
                  result 0]
             (let [merged-int (-> char
                                  (bit-and (if (= lshift 28) 15 127))
                                  (<< lshift)
                                  (bit-or result)
                                  (>>> 0))]
               ;(println (cl-format nil "2r~64,'0',B" merged-int) merged-int)
               (if (< char 128)
                 (if signed
                   (>> merged-int 0)
                   merged-int)
                 (recur (read-byte in)
                        (+ lshift 7)
                        merged-int))))))

#?(:cljs (defn- read-zigzag-varint64
           "Reads varint with zigzag encoding, i.e. sint32 and sint64."
           [in]
           (let [hl32 (-> in (read-varint-as-HL32) (zigzag-decode-HL32))]
             (HL32->n64 hl32 true))))

#?(:cljs (defn- read-zigzag-varint32
           "Reads varint with zigzag encoding, i.e. sint32 and sint64."
           [in]
           (let [zigzag-varint (read-varint32 ^ByteReader in false)
                 magnitude (>>> zigzag-varint 1)]
             (if (not= 0 (bit-and zigzag-varint 2r1)) ; negative
               (- (- magnitude) 1)
               magnitude))))

#?(:cljs (defn read-float [in]
           (let [uint (read-bytes in 4)
                 sign (-> uint (>> 31) (* 2) (+ 1))
                 exponent (-> uint (>>> 23) (bit-and 255))
                 mantissa (-> uint (bit-and 8388607))]
             (if (= exponent 255)
               (if (not= mantissa 0)
                 js/NaN
                 (* sign js/Infinity))
               (if (== exponent 0)
                 (-> sign (* 1.401298464324817e-45) (* mantissa))
                 (-> sign (* (js/Math.pow 2 (- exponent 150))) (* (+ mantissa 8388608))))))))

#?(:cljs (defn read-double [in]
           (let [lo (read-bytes in 4)
                 hi (read-bytes in 4)
                 sign (-> hi (>> 31) (* 2) (+ 1))
                 exponent (-> hi (>>> 20) (bit-and 2047))
                 mantissa (-> hi (bit-and 1048575) (* 4294967296) (+ lo))]
             (if (== exponent 2047)
               (if (not= mantissa 0)
                 js/NaN
                 (* sign js/Infinity))
               (if (== exponent 0)
                 (-> sign (* 5e-324) (* mantissa))
                 (-> sign (* (js/Math.pow 2 (- exponent 1075))) (* (+ mantissa 4503599627370496))))))))

#?(:cljs (defn read-text [in]
           (loop [text (str)
                  remain (read-varint32 in false)]
             (if (> remain 0)
               (let [c1 (read-byte in)
                     [t2 r2] (cond
                               (<= c1 0x7F)
                               [(str text (String/fromCharCode c1))
                                (dec remain)]

                               (and (>= c1 0xC0) (< c1 0xE0))
                               (let [c2 (read-byte in)
                                     s (String/fromCharCode (-> c1 (bit-and 0x1F) (<< 6)
                                                                (bit-or (-> c2 (bit-and 0x3F)))))]
                                 [(str text s)
                                  (- remain 2)])

                               (and (>= c1 0xE0) (< c1 0xF0))
                               (let [c2 (read-byte in)
                                     c3 (read-byte in)
                                     s (String/fromCharCode (-> c1 (bit-and 0xF)  (<< 12)
                                                                (bit-or (-> c2 (bit-and 0x3F) (<< 6)))
                                                                (bit-or (-> c3 (bit-and 0x3F)))))]
                                 [(str text s)
                                  (- remain 3)])

                               (>= c1 0xF0)
                               (let [c2 (read-byte in)
                                     c3 (read-byte in)
                                     c4 (read-byte in)
                                     s (-> c1 (bit-and 7)    (<< 18)
                                           (bit-or (-> c2 (bit-and 0x3F) (<< 12)))
                                           (bit-or (-> c3 (bit-and 0x3F) (<< 6)))
                                           (bit-or (-> c4 (bit-and 0x3F)))
                                           (- 0x10000))
                                     s1 (String/fromCharCode (-> s (>> 10) (+ 0xD800)))
                                     s2 (String/fromCharCode (-> s (bit-and 0x3FF)))]
                                 [(str text s1 s2)
                                  (- remain 4)]))]
                 (recur t2 r2))
               text))))

;-----------------------------------------------------------------------
; Common
;-----------------------------------------------------------------------
(defn- read-bytes [in num-bytes]
  (loop [n 0
         result 0]
    (if (< n num-bytes)
      (recur (inc n)
             (-> (read-byte in)
                 (<< (* n 8))
                 (>>> 0)
                 (bit-or result)))
      result)))

(defn- read-fixed32bits [in] (read-bytes in 4))
#?(:clj (defn- read-fixed64bits [in] (read-bytes in 8)))

(defn read-int32 [in] (read-varint32 ^ByteReader in true))
(defn read-int64 [in] (read-varint64 ^ByteReader in true))
(defn read-uint32 [in] (read-varint32 ^ByteReader in false))

(defn read-uint64 [in] (read-varint64 ^ByteReader in false))
(defn read-bool [in] (-> ^ByteReader in (read-byte) (not= 0)))
(defn read-enum [in] (read-varint32 ^ByteReader in true))
(defn read-sint32 [in] (read-zigzag-varint32 ^ByteReader in))
(defn read-sint64 [in] (read-zigzag-varint64 ^ByteReader in))

#?(:clj  (defn read-fixed32 [in] (read-fixed32bits ^ByteReader in)))
#?(:cljs (defn read-fixed32 [in]
           (let [value (read-fixed32bits ^ByteReader in)]
             (if (not= 0 (bit-and value 0x80000000))
               (-> value (bit-xor 0x80000000) (+ 0x80000000))
               value))))

(defn read-sfixed32 [in]
  (let [value (read-fixed32bits ^ByteReader in)]
    (if (not= 0 (bit-and value 0x80000000))
      (-> value (bit-not) (bit-and 0xFFFFFFFF) (+ 1) (-))
      value)))

#?(:clj (defn read-fixed64 [in] (read-fixed64bits ^ByteReader in)))

#?(:cljs (defn read-fixed64 [in]
           (let [lo (read-fixed32 in)
                 hi (read-fixed32 in)
                 hl32 (make-HL32 hi lo)]
             (HL32->n64 hl32 false))))

#?(:clj (defn read-sfixed64 [in] (read-fixed64bits ^ByteReader in)))

#?(:cljs (defn read-sfixed64 [in]
           (let [lo (read-fixed32 in)
                 hi (read-fixed32 in)
                 hl32 (make-HL32 hi lo)]
             (HL32->n64 hl32 true))))

(defn read-size [in] (read-varint32 ^ByteReader in false))

(defn read-tag [in]
  (let [tag (read-varint32 ^ByteReader in false)
        wire-type (-> tag (bit-and 7))
        field (-> tag (>>> 3))]
    [field wire-type]))

