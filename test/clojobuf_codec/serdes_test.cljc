(ns clojobuf-codec.serdes-test
  (:require [cljc-long.constants :as long-const]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.io.reader :refer [make-reader]]
            #?(:cljs [clojobuf-codec.util :refer [make-HL32 n64->HL32 HL32->n64]])
            [clojobuf-codec.serialize :as ser]
            [clojobuf-codec.deserialize :as des]
            [clojobuf-codec.test.util :refer [int32-max-value
                                              int32-min-value
                                              int53-max-value
                                              int53-min-value
                                              int64-max-value
                                              int64-min-value
                                              uint32-max-value
                                              uint64-max-value]]
            [clojure.test :refer [is deftest run-tests]]))

#?(:cljs (deftest test-HL32-roundtrip-signed
           (is (= (HL32->n64 (n64->HL32 0) true) 0))
           (is (= (HL32->n64 (n64->HL32 1) true) 1))
           (is (= (HL32->n64 (n64->HL32 123) true) 123))
           (is (= (HL32->n64 (n64->HL32 1234) true) 1234))
           (is (= (HL32->n64 (n64->HL32 -1) true) -1))
           (is (= (HL32->n64 (n64->HL32 -123) true) -123))
           (is (= (HL32->n64 (n64->HL32 -1234) true) -1234))
           (is (= (HL32->n64 (n64->HL32 int32-max-value) true) int32-max-value))
           (is (= (HL32->n64 (n64->HL32 int32-min-value) true) int32-min-value))
           (is (= (HL32->n64 (n64->HL32 int53-max-value) true) int53-max-value))
           (is (= (HL32->n64 (n64->HL32 int53-min-value) true) int53-min-value))
           #_(is (= (HL32->n64 (n64->HL32 int64-max-value) true) int64-max-value))
           (is (= (HL32->n64 (n64->HL32 int64-min-value) true) int64-min-value))))

#?(:cljs (deftest test-HL32-roundtrip-unsigned
           (is (= (HL32->n64 (n64->HL32 0) false) 0))
           (is (= (HL32->n64 (n64->HL32 1) false) 1))
           (is (= (HL32->n64 (n64->HL32 123) false) 123))
           (is (= (HL32->n64 (n64->HL32 1234) false) 1234))
           (is (= (HL32->n64 (n64->HL32 int32-max-value) false) int32-max-value))
           (is (= (HL32->n64 (n64->HL32 uint32-max-value) false) uint32-max-value))
           (is (= (HL32->n64 (n64->HL32 int53-max-value) false) int53-max-value))
           #_(is (= (HL32->n64 (n64->HL32 (- uint64-max-value 1)) false) (- uint64-max-value 1)))
           #_(is (= (HL32->n64 (n64->HL32 (- uint64-max-value)) false) (- uint64-max-value)))))

#?(:cljs (deftest test-HL32-serialize
           (is (= (n64->HL32 0) (make-HL32 0 0)))
           (is (= (n64->HL32 1) (make-HL32 0 1)))))

(defn roundtrip [fwd-fn bwd-fn & args]
  (let [writer (make-writer)]
    (apply fwd-fn writer args)
    (-> writer (->bytes) (make-reader) (bwd-fn))))

(defmulti serdes :tag)
(defmethod serdes :int32 [opts]         (roundtrip ser/write-int32 des/read-int32 (:data opts)))
(defmethod serdes :int64 [opts]         (roundtrip ser/write-int64 des/read-int64 (:data opts)))
(defmethod serdes :uint32 [opts]        (roundtrip ser/write-uint32 des/read-uint32 (:data opts)))
(defmethod serdes :bool [opts]          (roundtrip ser/write-bool des/read-bool (:data opts)))
(defmethod serdes :enum [opts]          (roundtrip ser/write-enum des/read-enum (:data opts)))
(defmethod serdes :sint32 [opts]        (roundtrip ser/write-sint32 des/read-sint32 (:data opts)))
(defmethod serdes :sint64 [opts]        (roundtrip ser/write-sint64 des/read-sint64 (:data opts)))
(defmethod serdes :fixed32 [opts]       (roundtrip ser/write-fixed32 des/read-fixed32 (:data opts)))
(defmethod serdes :sfixed32 [opts]      (roundtrip ser/write-sfixed32 des/read-sfixed32 (:data opts)))
(defmethod serdes :float [opts]         (roundtrip ser/write-float des/read-float (:data opts)))
(defmethod serdes :fixed64 [opts]       (roundtrip ser/write-fixed64 des/read-fixed64 (:data opts)))
(defmethod serdes :sfixed64 [opts]      (roundtrip ser/write-sfixed64 des/read-sfixed64 (:data opts)))
(defmethod serdes :double [opts]        (roundtrip ser/write-double des/read-double (:data opts)))
(defmethod serdes :size [opts]          (roundtrip ser/write-size des/read-size (:data opts)))
(defmethod serdes :text [opts]          (roundtrip ser/write-text des/read-text (:data opts)))
(defmethod serdes :tag [opts]           (roundtrip ser/write-tag des/read-tag (:field opts) (:wire-type opts)))

(defn test-serdes-u32 [typ]
  (is (= 0 (serdes {:tag typ :data 0})))
  (is (= 123 (serdes {:tag typ :data 123})))
  (is (= 12345 (serdes {:tag typ :data 12345})))
  (is (= uint32-max-value (serdes {:tag typ :data uint32-max-value}))))

(defn test-serdes-32 [typ]
  (is (= 0 (serdes {:tag typ :data 0})))
  (is (= 1 (serdes {:tag typ :data 1})))
  (is (= -1 (serdes {:tag typ :data -1})))
  (is (= 123 (serdes {:tag typ :data 123})))
  (is (= -123 (serdes {:tag typ :data -123})))
  (is (= 12345 (serdes {:tag typ :data 12345})))
  (is (= -12345 (serdes {:tag typ :data -12345})))
  (is (= int32-max-value (serdes {:tag typ :data int32-max-value})))
  (is (= int32-min-value (serdes {:tag typ :data int32-min-value})))
  (is (= (- int32-max-value 1) (serdes {:tag typ :data (- int32-max-value 1)})))
  (is (= (+ int32-min-value 1) (serdes {:tag typ :data (+ int32-min-value 1)}))))

(defn test-serdes-u64 [typ]
  (test-serdes-u32 typ)
  (is (= int53-max-value (serdes {:tag typ :data int53-max-value})))
  #?(:clj (is (= long-const/max-value (serdes {:tag typ :data long-const/max-value}))))
  #?(:clj (is (= (- long-const/max-value 1) (serdes {:tag typ :data (- long-const/max-value 1)})))))

(defn test-serdes-64 [typ]
  (test-serdes-32 typ)
  (is (= int53-max-value (serdes {:tag typ :data int53-max-value})))
  (is (= int53-min-value (serdes {:tag typ :data int53-min-value})))
  #?(:clj (is (= long-const/max-value (serdes {:tag typ :data long-const/max-value}))))
  #?(:clj (is (= long-const/min-value (serdes {:tag typ :data long-const/min-value}))))
  #?(:clj (is (= (- long-const/max-value 1) (serdes {:tag typ :data (- long-const/max-value 1)}))))
  #?(:clj (is (= (+ long-const/min-value 1) (serdes {:tag typ :data (+ long-const/min-value 1)})))))

(deftest test-serdes-int32 (test-serdes-32 :int32))
(deftest test-serdes-int64 (test-serdes-64 :int64))
(deftest test-serdes-uint32 (test-serdes-u32 :uint32))

(deftest test-serdes-enum (test-serdes-32 :enum))
(deftest test-serdes-sint32 (test-serdes-32 :sint32))
(deftest test-serdes-sint64 (test-serdes-64 :sint64))
(deftest test-serdes-fixed32 (test-serdes-u32 :fixed32))
(deftest test-serdes-sfixed32 (test-serdes-32 :sfixed32))
(deftest test-serdes-fixed64 (test-serdes-u64 :fixed64))
(deftest test-serdes-sfixed64 (test-serdes-64 :sfixed64))

; (deftest test-serdes-size (test-serdes-32 :size))

(deftest test-serdes-bool
  (is (= true (serdes {:tag :bool :data true})))
  (is (= false (serdes {:tag :bool :data false}))))

#?(:clj (deftest test-serdes-float
          (is (= 0.0 (serdes {:tag :float :data 0})))
          (is (= 1.0 (serdes {:tag :float :data 1})))
          (is (= 123.0 (serdes {:tag :float :data 123})))
          (is (= 1234.0 (serdes {:tag :float :data 1234})))
          (is (= (float 123.4) (serdes {:tag :float :data 123.4})))
          (is (= (float 3.402823466E+38) (serdes {:tag :float :data 3.402823466E+38})))
          (is (= (float 1.175494351E-38) (serdes {:tag :float :data 1.175494351E-38})))
          (is (= -1.0 (serdes {:tag :float :data -1})))
          (is (= -123.0 (serdes {:tag :float :data -123})))
          (is (= -1234.0 (serdes {:tag :float :data -1234})))
          (is (= (float -123.4) (serdes {:tag :float :data -123.4})))
          (is (= (float -3.402823466E+38) (serdes {:tag :float :data -3.402823466E+38})))
          (is (= (float -1.175494351E-38) (serdes {:tag :float :data -1.175494351E-38})))))

#?(:clj (deftest test-serdes-double
          (is (= 0.0 (serdes {:tag :double :data 0})))
          (is (= 1.0 (serdes {:tag :double :data 1})))
          (is (= 123.0 (serdes {:tag :double :data 123})))
          (is (= 1234.0 (serdes {:tag :double :data 1234})))
          (is (= 123.4 (serdes {:tag :double :data 123.4})))
          (is (= (double 1.7976931348623158E+308) (serdes {:tag :double :data 1.7976931348623158E+308})))
          (is (= (double 2.2250738585072014E-308) (serdes {:tag :double :data 2.2250738585072014E-308})))
          (is (= -1.0 (serdes {:tag :double :data -1})))
          (is (= -123.0 (serdes {:tag :double :data -123})))
          (is (= -1234.0 (serdes {:tag :double :data -1234})))
          (is (= -123.4 (serdes {:tag :double :data -123.4})))
          (is (= (double -1.7976931348623158E+308) (serdes {:tag :double :data -1.7976931348623158E+308})))
          (is (= (double -2.2250738585072014E-308) (serdes {:tag :double :data -2.2250738585072014E-308})))))

#?(:cljs (deftest test-serdes-float
           (is (js/isNaN (serdes {:tag :float :data js/NaN})))
           (is (= 0.0 (serdes {:tag :float :data 0})))
           (is (= 1.0 (serdes {:tag :float :data 1})))
           (is (= 123.0 (serdes {:tag :float :data 123})))
           (is (= 1234.0 (serdes {:tag :float :data 1234})))
           (is (= 123.4000015258789 (serdes {:tag :float :data 123.4})))
           (is (= js/Number.POSITIVE_INFINITY (serdes {:tag :float :data js/Number.POSITIVE_INFINITY})))
           (is (= -1.0 (serdes {:tag :float :data -1})))
           (is (= -123.0 (serdes {:tag :float :data -123})))
           (is (= -1234.0 (serdes {:tag :float :data -1234})))
           (is (= -123.4000015258789 (serdes {:tag :float :data -123.4})))
           (is (= js/Number.NEGATIVE_INFINITY (serdes {:tag :float :data js/Number.NEGATIVE_INFINITY})))))

#?(:cljs (deftest test-serdes-double
           (is (js/isNaN (serdes {:tag :double :data js/NaN})))
           (is (= 0.0 (serdes {:tag :double :data 0})))
           (is (= 1.0 (serdes {:tag :double :data 1})))
           (is (= 123.0 (serdes {:tag :double :data 123})))
           (is (= 1234.0 (serdes {:tag :double :data 1234})))
           (is (= 123.39993896484376 (serdes {:tag :double :data 123.4})))
           (is (= js/Number.POSITIVE_INFINITY (serdes {:tag :double :data js/Number.POSITIVE_INFINITY})))
           (is (= -1.0 (serdes {:tag :double :data -1})))
           (is (= -123.0 (serdes {:tag :double :data -123})))
           (is (= -1234.0 (serdes {:tag :double :data -1234})))
           (is (= -123.39993896484376 (serdes {:tag :double :data -123.4})))
           (is (= js/Number.NEGATIVE_INFINITY (serdes {:tag :double :data js/Number.NEGATIVE_INFINITY})))))

(deftest test-serdes-tag
  (is (= [1 0] (serdes {:tag :tag :field 1 :wire-type 0})))
  (is (= [1 1] (serdes {:tag :tag :field 1 :wire-type 1})))
  (is (= [1 2] (serdes {:tag :tag :field 1 :wire-type 2})))
  (is (= [1 3] (serdes {:tag :tag :field 1 :wire-type 3})))
  (is (= [1 4] (serdes {:tag :tag :field 1 :wire-type 4})))
  (is (= [1 5] (serdes {:tag :tag :field 1 :wire-type 5})))
  (is (= [12345657 0] (serdes {:tag :tag :field 12345657 :wire-type 0})))
  (is (= [12345657 1] (serdes {:tag :tag :field 12345657 :wire-type 1})))
  (is (= [12345657 2] (serdes {:tag :tag :field 12345657 :wire-type 2})))
  (is (= [12345657 3] (serdes {:tag :tag :field 12345657 :wire-type 3})))
  (is (= [12345657 4] (serdes {:tag :tag :field 12345657 :wire-type 4})))
  (is (= [12345657 5] (serdes {:tag :tag :field 12345657 :wire-type 5}))))

(deftest test-serdes-text
  (is (= "abcdefg 1234567890" (serdes {:tag :text :data "abcdefg 1234567890"})))
  (is (= "知之为知之，不知为不知，是知也。" (serdes {:tag :text :data "知之为知之，不知为不知，是知也。"}))))
