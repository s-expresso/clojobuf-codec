(ns clojobuf-codec.test_codec
  (:require [clojobuf-codec.io.writer :refer [make-writer write-byte ->bytes]]
            [clojobuf-codec.io.reader :refer [make-reader]]
            [clojobuf-codec.encode :as enc]
            [clojobuf-codec.decode :as dec]
            #?(:clj [clojure.core :refer [byte-array]])
            [clojure.test :refer [is deftest run-tests]]))

#?(:cljs (defn byte-array [v]
           (let [writer (make-writer)]
             (doall (map #(write-byte writer %) v))
             (->bytes writer))))

(defn- encode1 [func & args]
  (let [writer (make-writer)]
    (apply func writer args)
    (->bytes writer)))

(defn- decode1 [binary func & args]
  (let [reader (make-reader binary)
        [field-id wire-type] (dec/read-tag reader)
        value (apply func reader args)]
    [field-id wire-type value]))

(defn- codec1-pri [field-num field-type value]
  (-> (encode1 enc/write-pri field-num field-type value)
      (decode1 dec/read-pri field-type)))

(defn- codec1-packed [field-num field-type value]
  (-> (encode1 enc/write-packed field-num field-type value)
      (decode1 dec/read-packed field-type)))

(deftest codec1-wire-type-0
  (is (= (codec1-pri 1 :int32 -23)
         [1 0 -23]))
  (is (= (codec1-pri 2 :int64 -34)
         [2 0 -34]))
  (is (= (codec1-pri 3 :uint32 45)
         [3 0 45]))
  (is (= (codec1-pri 4 :uint64 56)
         [4 0 56]))
  (is (= (codec1-pri 5 :sint32 -1)
         [5 0 -1]))
  (is (= (codec1-pri 5 :sint32 -2)
         [5 0 -2]))
  (is (= (codec1-pri 5 :sint32 -67)
         [5 0 -67]))
  (is (= (codec1-pri 6 :sint64 -1)
         [6 0 -1]))
  (is (= (codec1-pri 6 :sint64 -2)
         [6 0 -2]))
  (is (= (codec1-pri 6 :sint64 -78)
         [6 0 -78]))
  (is (= (codec1-pri 7 :bool true)
         [7 0 true]))
  (is (= (codec1-pri 8 :bool false)
         [8 0 false])))

(deftest codec1-wire-type-1
  (is (= (codec1-pri 9 :fixed64 89)
         [9 1 89]))
  (is (= (codec1-pri 10 :sfixed64 -90)
         [10 1 -90]))
  (is (= (codec1-pri 11 :double 123.456)
         [11 1 123.456]))
  (is (= (codec1-pri 12 :double -123.456)
         [12 1 -123.456])))

(deftest codec1-wire-type-2
  (is (= (codec1-pri 13 :string "The brown fox")
         [13 2 "The brown fox"]))
  (is (= (let [[field-id wire-type bin]
               (codec1-pri 14 :bytes (byte-array [1 2 3 4 5]))]
           [field-id wire-type (seq bin)])
         [14 2 (seq (byte-array [1 2 3 4 5]))])))

(deftest codec1-wire-type-5
  (is (= (codec1-pri 15 :fixed32 91)
         [15 5 91]))
  (is (= (codec1-pri 16 :sfixed32 -92)
         [16 5 -92]))
  (is (= (codec1-pri 17 :float 123)
         [17 5 123.0]))
  (is (= (codec1-pri 18 :float -123)
         [18 5 -123.0])))

(deftest codec-packed
  (is (= (codec1-packed 19 :int32 [0 1234 -1234])
         [19 2 [0 1234 -1234]]))
  (is (= (codec1-packed 20 :int64 [0 1234567890 -1234567890])
         [20 2 [0 1234567890 -1234567890]]))
  (is (= (codec1-packed 21 :uint32 [0 1234 5678])
         [21 2 [0 1234 5678]]))
  (is (= (codec1-packed 22 :uint64 [0 1234567890 2345678901])
         [22 2 [0 1234567890 2345678901]]))
  (is (= (codec1-packed 23 :sint32 [0 1234 -1234])
         [23 2 [0 1234 -1234]]))
  (is (= (codec1-packed 24 :sint64 [0 1234567890 -1234567890])
         [24 2 [0 1234567890 -1234567890]]))
  (is (= (codec1-packed 25 :bool [true false true false])
         [25 2 [true false true false]]))
  (is (= (codec1-packed 26 :enum [-2 -1 0 1 2])
         [26 2 [-2 -1 0 1 2]]))
  (is (= (codec1-packed 27 :fixed64 [0 1234567890 2345678901])
         [27 2 [0 1234567890 2345678901]]))
  (is (= (codec1-packed 28 :sfixed64 [0 1234567890 -1234567890])
         [28 2 [0 1234567890 -1234567890]]))
  (is (= (codec1-packed 29 :double [0 123.456 -123.456 787878 -787878])
         [29 2 [0.0 123.456 -123.456 787878.0 -787878.0]])))

(defn- decode-wire1 [binary]
  (let [reader (make-reader binary)
        [field-id wire-type] (dec/read-tag reader)
        value (dec/read-raw-wire reader wire-type)]
    [field-id wire-type value]))

(defn- codec-wire1 [field-num field-type value]
  (-> (encode1 enc/write-pri field-num field-type value)
      (decode-wire1)))

; test encode normally, decode with (dec/read-raw-wire ...)
(deftest raw-decode-wire-type-0
  (is (= (codec-wire1 123 :int32 1)
         [123 0 1]))
  (is (= (codec-wire1 123 :int64 1)
         [123 0 1]))
  (is (= (codec-wire1 123 :sint32 1)
         [123 0 2])) ; zigzag
  (is (= (codec-wire1 123 :sint64 1)
         [123 0 2])) ; zigzag
  (is (= (codec-wire1 123 :uint32 1)
         [123 0 1]))
  (is (= (codec-wire1 123 :uint64 1)
         [123 0 1]))
  (is (= (codec-wire1 123 :bool false)
         [123 0 0]))
  (is (= (codec-wire1 123 :bool true)
         [123 0 1]))
  (is (= (codec-wire1 123 :enum 1)
         [123 0 1])))

(deftest raw-decode-wire-type-1
  (is (= (codec-wire1 123 :fixed64 1)
         [123 1 1]))
  (is (= (codec-wire1 123 :sfixed64 1)
         [123 1 1]))
  #_(is (= (codec-wire1 123 :double 1.0)
           [123 1 2])))

(deftest raw-decode-wire-type-2
  (is (= (let [[field-id wire-type bin]
               (codec-wire1 123 :string "the quick brown fox")]
           [field-id wire-type (apply str (map char bin))])
         [123 2 "the quick brown fox"])))

(deftest raw-decode-wire-type-5
  (is (= (codec-wire1 123 :fixed32 1)
         [123 5 1]))
  (is (= (codec-wire1 123 :sfixed32 1)
         [123 5 1]))
  #_(is (= (codec-wire1 123 :float 1.0)
           [123 1 2])))


(run-tests)
