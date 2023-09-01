(ns clojobuf-codec.serialize
  (:require [clojobuf-codec.io.writer :refer
             #?(:clj  [ByteWriter write-byte write-bytearray])
             #?(:cljs [ByteWriter write-byte])]
            #?(:cljs [clojobuf-codec.io.reader :refer [available? make-reader read-byte]])
            [clojobuf-codec.util :refer
             #?(:clj  [>>> <<])
             #?(:cljs [>>> << >>  make-HL32 n64->HL32 zigzag-encode-HL32])])
  #?(:clj (:import [java.nio.charset StandardCharsets])))

;-----------------------------------------------------------------------
; CLJ
;-----------------------------------------------------------------------
#?(:clj (defn- write-varint [out data]
          (loop [value data]
            (let [upper (>>> value 7)
                  uint8 (bit-and value 127)]
              (if (or (= upper 0) (= upper value))
                (write-byte ^ByteWriter out uint8)

                (do (write-byte ^ByteWriter out (bit-or uint8 128))
                    (recur upper)))))))

#?(:clj (defn- write-varint64 [out data]
          (write-varint out data)))

#?(:clj (defn- write-varint32 [out data]
          (write-varint out data)))

#?(:clj (defn- write-zigzag-varint [out data]
          (let [zigzag (-> (if (< data 0) (- (+ data 1)) data)
                           (<< 1)
                           (bit-or (if (< data 0) 1 0)))]
            (write-varint out zigzag))))

#?(:clj (defn- write-zigzag-varint64 [out data]
          (write-zigzag-varint out data)))

#?(:clj (defn- write-zigzag-varint32 [out data]
          (write-zigzag-varint out data)))

#?(:clj (defn- write-bytes4 [out data]
          (dotimes [i 4]
            (write-byte out (-> data
                                (>>> (* i 8))
                                (bit-and 255))))))

#?(:clj (defn- write-bytes8 [out data]
          (dotimes [i 8]
            (write-byte out (-> data
                                (>>> (* i 8))
                                (bit-and 2r11111111))))))

#?(:clj (defn write-float [out data]
          (let [fval (float data)
                int-bits (Float/floatToIntBits fval)]
            (write-bytes4 out int-bits))))

#?(:clj (defn write-double [out data]
          (let [dval (double data)
                long-bits (Double/doubleToLongBits dval)]
            (write-bytes8 out long-bits))))

#?(:clj (defn write-text [out data]
          (let [ba (.getBytes data StandardCharsets/UTF_8)]
            (write-varint32 out (count ba))
            (write-bytearray out ba))))

#?(:clj (defn write-bytes [out data]
          (write-bytearray out data)))

;-----------------------------------------------------------------------
; CLJS
;-----------------------------------------------------------------------
#?(:cljs (defn- write-HL32 [out hl32]
           (loop [hl32 hl32]
             (let [hi (:hi hl32)]
               (if (not= hi 0)
                 (do (write-byte out (-> (:lo hl32) (bit-and 127) (bit-or 128)))
                     (let [new-lo (-> (:lo hl32) (>>> 7) (bit-or (<< hi 25)) (>>> 0))]
                       (recur (make-HL32 (>>> hi 7) new-lo))))
                 (loop [lo (:lo hl32)]
                   (if (> lo 127)
                     (do (write-byte out (-> lo (bit-and 127) (bit-or 128)))
                         (recur (>>> lo 7)))
                     (write-byte out (-> lo (bit-and 127))))))))))

#?(:cljs (defn- write-varint64 [out data]
           (write-HL32 out (n64->HL32 data))))

#?(:cljs (defn- write-varint32 [out data]
           (loop [val (>>> data 0)]
             (if (<= val 127)
               (write-byte out (>>> val 0))
               (do (write-byte out (-> val (bit-and 127) (bit-or 128) (>>> 0)))
                   (recur (>>> val 7)))))))

#?(:cljs (defn- write-zigzag-varint64 [out data]
           (let [hl32 (n64->HL32 data)
                 hl32zz (zigzag-encode-HL32 hl32)]
             (write-HL32 out hl32zz))))

; TODO ok to just call 64bits version?
#?(:cljs (defn- write-zigzag-varint32 [out data]
           (write-zigzag-varint64 out data)))

#?(:cljs (defn- write-bytes4 [out data]
           (dotimes [i 4]
             (write-byte out (-> data
                                 (>>> (* i 8))
                                 (bit-and 2r11111111))))))

#?(:cljs (defn- write-bytes8 [out data]
           (let [hl32 (n64->HL32 data)]
             (write-bytes4 out (:lo hl32))
             (write-bytes4 out (:hi hl32)))))

#?(:cljs (defn write-float [out js-number]
           (let [sign-bit (if (< js-number 0) (<< 1 31) 0)
                 magnitude (if (not= sign-bit 0) (- js-number) js-number)]
             (cond
               (= magnitude 0)                      (write-bytes4 out 0)
               (js/isNaN magnitude)                 (write-bytes4 out 2143289344)
               (> magnitude 3.4028234663852886e+38) (write-bytes4 out (-> 2139095040 (bit-or sign-bit) (>>> 0)))
               (< magnitude 1.1754943508222875e-38) (write-bytes4 out (-> magnitude (/ 1.401298464324817e-45) (js/Math.round) (bit-or sign-bit) (>>> 0)))
               :else (let [exponent (-> magnitude (js/Math.log) (/ js/Math.LN2) (js/Math.floor))
                           mantissa (->> exponent (-) (js/Math.pow 2) (* magnitude) (* 8388608) (js/Math.round) (bit-and 8388607))]
                       (write-bytes4 out (-> exponent (+ 127) (<< 23) (bit-or sign-bit) (bit-or mantissa) (>>> 0))))))))

#?(:cljs (defn write-double [out js-number]
           (let [sign-bit (if (< js-number 0) (<< 1 31) 0)
                 magnitude (if (not= sign-bit 0) (- js-number) js-number)]
             (cond
               (= magnitude 0)  (do (write-bytes4 out 0)
                                    (write-bytes4 out (if (> (/ 1 magnitude) 0) 0 2147483648)))
               (js/isNaN magnitude) (do (write-bytes4 out 0)
                                        (write-bytes4 out 2146959360))
               (> magnitude 1.7976931348623157e+308) (do (write-bytes4 out 0)
                                                         (write-bytes4 out (bit-or sign-bit 2146435072)))
               (< magnitude 2.2250738585072014e-308) (let [mantissa (/ magnitude 5e-324)
                                                           exponent (/ mantissa 4294967296)]
                                                       (write-bytes4 out (>>> mantissa 0))
                                                       (write-bytes4 out (-> exponent (bit-or sign-bit) (>>> 0))))
               :else (let [exp (-> magnitude (js/Math.log) (/ js/Math.LN2) (js/Math.floor))
                           exponent (if (= exp 1024) 1023 exp)
                           mantissa (->> exponent (-) (js/Math.pow 2) (* magnitude))
                           m1 (-> mantissa (* 4503599627370496) (>>> 0))
                           m2 (-> mantissa (* 1048576) (bit-and 1048575))]
                       (write-bytes4 out m1)
                       (write-bytes4 out (-> exponent (+ 1023) (<< 20) (bit-or sign-bit) (bit-or m2))))))))

#?(:cljs (defn- utf8-length [data]
           (loop [len 0
                  i 0]
             (let [c1 (-> data (.charCodeAt i))]
               (if-not (js/isNaN c1)
                 (let [[new-len new-i] (cond
                                         (< c1 128) [(inc len)
                                                     (inc i)]
                                         (< c1 2048) [(+ len 2)
                                                      (inc i)]
                                         :else (let [c2 (.charCodeAt data (+ i 1))]
                                                 (if (and (== (bit-and c1 0xFC00) 0xD800)
                                                          (== (bit-and c2 0xFC00) 0xDC00))
                                                   [(+ len 4)
                                                    (+ i 2)]
                                                   [(+ len 3)
                                                    (inc i)])))]
                   (recur new-len new-i))
                 len)))))

#?(:cljs (defn write-text [out data]
           (write-varint32 out (utf8-length data))
           (loop [i 0]
             (let [c1 (-> data (.charCodeAt i))]
               (when-not (js/isNaN c1)
                 (recur (cond
                          (< c1 128) (do (write-byte out c1)
                                         (inc i))
                          (< c1 2048) (do (write-byte out (-> c1 (>> 6) (bit-or 192)))
                                          (write-byte out (-> c1 (bit-and 63) (bit-or 192)))
                                          (inc i))
                          :else (let [c2 (.charCodeAt data (+ i 1))]
                                  (if (and (== (bit-and c1 0xFC00) 0xD800)
                                           (== (bit-and c2 0xFC00) 0xDC00))
                                    (let [out4 (-> c1 (bit-and 0x03FF) (<< 10) (+ 0x10000) (+ (bit-and c2 0x03FF)))]
                                      (write-byte out (-> out4 (>> 18)              (bit-or 240)))
                                      (write-byte out (-> out4 (>> 12) (bit-and 63) (bit-or 128)))
                                      (write-byte out (-> out4 (>> 6)  (bit-and 63) (bit-or 128)))
                                      (write-byte out (-> out4         (bit-and 63) (bit-or 128)))
                                      (+ i 2))
                                    (do
                                      (write-byte out (-> c1 (>> 12) (bit-or 224)))
                                      (write-byte out (-> c1 (>> 6)  (bit-and 63) (bit-or 128)))
                                      (write-byte out (-> c1         (bit-and 63) (bit-or 128)))
                                      (inc i)))))))))))

; TODO make it more efficient
#?(:cljs (defn write-bytes [out ^js/Array data]
           (let [reader (make-reader data)]
             (loop []
               (write-byte out (read-byte reader))
               (when (available? reader) (recur))))))

;-----------------------------------------------------------------------
; Common
;-----------------------------------------------------------------------
(defn- write-fixed32bits [out data] (write-bytes4 out data))
(defn- write-fixed64bits [out data] (write-bytes8 out data))

(defn write-int32 [out data] (write-varint32 ^ByteWriter out data))
(defn write-int64 [out data] (write-varint64 ^ByteWriter out data))
(defn write-uint32 [out data] (write-varint32 ^ByteWriter out data))
(defn write-uint64 [out data] (write-varint64 ^ByteWriter out data))
(defn write-bool [out data] (write-byte out (if (true? data) 1 0)))
(defn write-enum [out data] (write-varint32 ^ByteWriter out data))
(defn write-sint32 [out data] (write-zigzag-varint32 ^ByteWriter out data))
(defn write-sint64 [out data] (write-zigzag-varint64 ^ByteWriter out data))

(defn write-fixed32 [out data] (write-fixed32bits ^ByteWriter out data))
(defn write-sfixed32 [out data] (write-fixed32bits ^ByteWriter out data))

(defn write-fixed64 [out data] (write-fixed64bits ^ByteWriter out data))
(defn write-sfixed64 [out data] (write-fixed64bits ^ByteWriter out data))

(defn write-size [out data] (write-varint32 ^ByteWriter out data))

(defn write-tag [out field wire-type]
  (let [tag (-> field (<< 3) (bit-or wire-type))]
    (write-varint32 ^ByteWriter out tag)))
