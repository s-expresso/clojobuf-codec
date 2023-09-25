(ns clojobuf-codec.test_decode_ref_bin
  (:require [clojobuf-codec.decode :as dec]
            [clojobuf-codec.io.reader :refer [make-reader]]
            [clojure.core :refer [byte-array]]
            [clojure.test :refer [is deftest run-tests]]
            [clojure.java.io :as io]))

(defn- decode1 [binary func & args]
  (let [reader (make-reader binary)
        [field-id wire-type] (dec/read-tag reader)
        value (apply func reader args)]
    [field-id wire-type value]))

; see resources/protobuf/makefile on how these .protobin files are generated
(defn =protobin
  [field-num wire-type field-type field-value]
  (let [val->str (fn [val] (if (string? val) (str \" val \") (str val)))]
    (is (= (-> (str "resources/protobuf/generated-bin/singular/"
                    (name field-type) "_" (val->str field-value) ".protobin")
               io/file .toPath java.nio.file.Files/readAllBytes
               (decode1 dec/read-pri field-type))
           [field-num wire-type field-value]))))

(defn =protobin-str
  [field-num wire-type field-type field-value filename]
  (is (= (-> (str "resources/protobuf/generated-bin/singular/"
                  filename)
             io/file .toPath java.nio.file.Files/readAllBytes
             (decode1 dec/read-pri field-type))
         [field-num wire-type field-value])))

(deftest decode-singular-binary
  ; wire-type 0
  (=protobin 1 0 :int32 2147483647)
  (=protobin 1 0 :int32 -2147483648)
  (=protobin 2 0 :int64 9223372036854775807)
  (=protobin 2 0 :int64 -9223372036854775808)
  (=protobin 3 0 :uint32 4294967295)
  #_(=protobin 4 0 :uint64 18446744073709551615)
  (=protobin 5 0 :sint32 2147483647)
  (=protobin 5 0 :sint32 -2147483648)
  (=protobin 6 0 :sint64 9223372036854775807)
  (=protobin 6 0 :sint64 -9223372036854775808)
  (=protobin 7 0 :bool true)
  (=protobin 7 0 :bool false)
  (=protobin 8 0 :enum 3)
  (=protobin 8 0 :enum -1)

  ; wire-type 1
  #_(=protobin 9 1 :fixed64 18446744073709551615)
  (=protobin 10 1 :sfixed64 9223372036854775807)
  (=protobin 10 1 :sfixed64 -9223372036854775808)
  (=protobin 11 1 :double 123.456)
  (=protobin 11 1 :double -123.456)

  ; wire-type 2
  (=protobin-str 12 2 :string "the quick brown fox", "string_the_quick_brown_fox.protobin")
  (=protobin-str 12 2 :string "一二三四五",            "string_one_two_three_four_five.protobin")

  ; wire-type 5
  (=protobin 14 5 :fixed32 4294967295)
  (=protobin 15 5 :sfixed32 2147483647)
  (=protobin 15 5 :sfixed32 -2147483648)
  #_(=protobin 16 5 :float 1.23)
  #_(=protobin 16 5 :float -1.23))

; see resources/protobuf/makefile on how these .protobin files are generated
(defn =packed-protobin
  [field-type [field-num wire-type values]]
  (is (= (-> (str "resources/protobuf/generated-bin/packed/" (name field-type) ".protobin")
             io/file .toPath java.nio.file.Files/readAllBytes
             (decode1 dec/read-packed field-type))
         [field-num wire-type values])))

(deftest decode-packed-binary
  (=packed-protobin :int32 [1 2 [0, 12345, -12345, 2147483647, -2147483648]])
  (=packed-protobin :int64 [2 2 [0, 12345, -12345, 9223372036854775807, -9223372036854775808]])
  (=packed-protobin :uint32 [3 2 [0, 12345, 23456, 34567, 4294967295]])
  #_(=packed-protobin :uint64 [4 2 [0, 12345, 23456, 34567, 18446744073709551615]])
  (=packed-protobin :sint32 [5 2 [0, 12345, -12345, 2147483647, -2147483648]])
  (=packed-protobin :sint64 [6 2 [0, 12345, -12345, 9223372036854775807, -9223372036854775808]])
  (=packed-protobin :bool [7 2 [true, false, true, false, true]])
  (=packed-protobin :enum [8 2 [-1, 0, 1, 2, 3]])
  #_(=packed-protobin :fixed64 [9 2 [0, 12345, 23456, 34567, 18446744073709551615]])
  (=packed-protobin :sfixed64 [10 2 [0, 12345, -12345, 9223372036854775807, -9223372036854775808]])
  (=packed-protobin :double [11 2 [0.0, 123.45, -123.45, 12345.678, -12345.678]])
  ; string type cannot be packed
  ; binary type cannot be packed
  (=packed-protobin :fixed32 [14 2 [0, 12345, 23456, 34567, 4294967295]])
  (=packed-protobin :sfixed32 [15 2 [0, 12345, -12345, 2147483647, -2147483648]])
  (=packed-protobin :float [16 2 [0.0, 12.0, -12.0, 123.0, -123.0]]))

(run-tests)
