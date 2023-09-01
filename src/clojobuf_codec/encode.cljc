(ns clojobuf-codec.encode
  "Encode data into protobuf's Tag-Len-Val or Tag-Val format, where:
   (1) Tag is varint encoded containing wire-type (lowest 3-bits) packed with
       field number
   (2) Len is varint encoded containing number of bytes that Val occupy; only
       present of wire-type 2
   (3) Val is varint encoded for wire-type 0, or a fixed length for wire-type
       1 and 5, or a continuous length of bytes for wire-type 2
   
   Wire-Type
   0	VARINT  int32, int64, uint32, uint64, sint32, sint64, bool, enum
   1	I64	    fixed64, sfixed64, double
   2	LEN     string, bytes, embedded messages, packed repeated fields
   3	SGROUP  group start (deprecated)
   4	EGROUP  group end (deprecated)
   5	I32     fixed32, sfixed32, float
   Note SGROUP and EGROUP are not supported
   
   For more info https://developers.google.com/protocol-buffers/docs/encoding"
  (:require [clojobuf-codec.serialize :as ser]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.util :refer [raise]]))

(declare write-bytes)

(defn write-pri
  "Write a primitive field where
    writer     = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num  = field number
    field-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool |
                 :fixed32 | :sfixed32 | :float |
                 :fixed64 | :sfixed64 | :double |
                 :string | :bytes
    value      = value corresponding to field-type above"
  ([writer field-num field-type value]
   (let [encode1 (fn [wire-type enc] (doto writer
                                       (ser/write-tag field-num wire-type)
                                       (enc value)))
         encode-bytes #(write-bytes writer field-num value)]
     (case field-type
       :int32    (encode1 0 ser/write-int32)
       :int64    (encode1 0 ser/write-int64)
       :uint32   (encode1 0 ser/write-uint32)
       :uint64   (encode1 0 ser/write-uint64)
       :sint32   (encode1 0 ser/write-sint32)
       :sint64   (encode1 0 ser/write-sint64)
       :bool     (encode1 0 ser/write-bool)
       :enum     (encode1 0 ser/write-enum)
       :fixed64  (encode1 1 ser/write-fixed64)
       :sfixed64 (encode1 1 ser/write-sfixed64)
       :double   (encode1 1 ser/write-double)
       :string   (encode1 2 ser/write-text) ; wire-type 2: len encoding
       :bytes    (encode-bytes)
       :fixed32  (encode1 5 ser/write-fixed32)
       :sfixed32 (encode1 5 ser/write-sfixed32)
       :float    (encode1 5 ser/write-float)
       (raise (str "Unexpected primitive type:" field-type))))))

(defn write-bytes
  "Write binary
    writer    = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num = field number
    binary    = binary data to be copied by writer"
  [writer field-num binary]
  (doto writer
    (ser/write-tag field-num 2)
    (ser/write-size #?(:clj (count binary))
                    #?(:cljs (.-length binary)))
    (ser/write-bytes binary)))

(defn write-kv-pri
  "Write key-value where value is a primitive type
    writer    = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num = field number
    key-type  = key type
    k         = key value
    val-type  = value type
    v         = value"
  [writer field-num key-type k val-type v]
  (let [msg-writer (make-writer)]
    (write-pri msg-writer 1 key-type k)
    (write-pri msg-writer 2 val-type v)
    (write-bytes writer field-num (->bytes msg-writer))))

(defn write-kv-enum
  "Write key-value where value is enum
    writer    = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num = field number
    key-type  = key type
    k         = key value
    int-val   = integer value (of enum field)"
  [writer field-num key-type k int-val]
  (let [msg-writer (make-writer)]
    (write-pri msg-writer 1 key-type k)
    (write-pri msg-writer 2 :enum int-val)
    (write-bytes writer field-num (->bytes msg-writer))))

(defn write-kv-msg
  "Write key-value where value is binary representation of protobuf message
    writer    = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num = field number
    key-type  = key type
    k         = key value
    binary    = binary representation of protobuf message"
  [writer field-num key-type k binary]
  (let [msg-writer (make-writer)]
    (write-pri msg-writer 1 key-type k)
    (write-bytes msg-writer 2 binary)
    (write-bytes writer field-num (->bytes msg-writer))))

(defn write-packed
  "Write a sequence as packed
    writer     = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num  = field number
    field-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool |
                 :fixed32 | :sfixed32 | float |
                 :fixed64 | :sfixed64 | double |
                 :string
    values     = sequence to be encoded"
  [writer field-num field-type values]
  (let [encode-fn (case field-type
                    :int32    ser/write-int32
                    :int64    ser/write-int64
                    :uint32   ser/write-uint32
                    :uint64   ser/write-uint64
                    :sint32   ser/write-sint32
                    :sint64   ser/write-sint64
                    :bool     ser/write-bool
                    :fixed64  ser/write-fixed64
                    :sfixed64 ser/write-sfixed64
                    :double   ser/write-double
                    :fixed32  ser/write-fixed32
                    :sfixed32 ser/write-sfixed32
                    :float    ser/write-float
                    (raise (str "field type cannot be packed:" field-type)))
        packer (make-writer)]
    (doall (map #(encode-fn packer %) values))
    (write-bytes writer field-num (->bytes packer))))
