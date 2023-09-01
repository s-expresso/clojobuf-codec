(ns clojobuf-codec.decode
  "Read protobuf's Tag-Len-Val or Tag-Val encoded data, where:
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
     
     For more info https://developers.google.com/protocol-buffers/docs/encoding
   
  Decoding a protobuf encoded binary is a 2 steps process performed repeatedly:
    (1) call `(read-tag rr)` to get `field-id` and `wire-type`
    (2) look up `field-id` in your protobuf schema to determine `field-type`
        (A) `field-type` == primitive
            (a) not :bytes && wire-type 2 => `(read-packed rr field-type)`
            (b) all other cases           => `(read-pri    rr field-type)`
        (B) `field-type` == map<key-type, value-type>
            (a) value-type is msg         => `(read-kv-msg  rr key-type)`
            (b) value-type is enum        => `(read-kv-enum rr key-type)`
            (c) value-type is primitive   => `(read-kv-pri  rr key-type)`
        (C) `field-type` == message     => `(read-len-coded-bytes rr)`
  where `rr` is `clojobuf-codec.io.reader.ByteReader` reading the binary.

  If look up of `field-id` fails, then sender is using a schema with additional
  fields. Use `read-raw-wire` to extract the value generically and continue.
     
  If look up of `field-id` yields a `field-type` incompatible with the `wire-type`,
  then sender is using a schema that has breaking change. If you want to continue
  decoding, you must honour the wire-type and use `read-raw-wire` to preserve the
  correctness of Tag-(Len-)Value boundary."
  (:require [clojobuf-codec.io.reader :refer
             [available? read-bytearray make-reader]]
            [clojobuf-codec.deserialize :as des]
            [clojobuf-codec.util :refer [raise]]))

(defn- read-len-coded-bytes
  "Read the next value as varint N, and return the next N bytes as binary."
  [reader] (->> reader (des/read-int32) (read-bytearray reader)))

(defn read-tag
  "Read the next value as varint and unpack it as [field-id wire-type] by
   assuming wire-type occupies lowest 3 bits. Returns [field-id wire-type]."
  [reader] (des/read-tag reader))

(defn read-pri
  "Read a single primitive value, where
   field-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool | :enum
                :fixed32 | :sfixed32 | :float |
                :fixed64 | :sfixed64 | :double |
                :string | :bytes"
  [reader field-type]
  (case field-type
    :int32    (des/read-int32 reader)
    :int64    (des/read-int64 reader)
    :uint32   (des/read-uint32 reader)
    :uint64   (des/read-uint64 reader)
    :sint32   (des/read-sint32 reader)
    :sint64   (des/read-sint64 reader)
    :bool     (des/read-bool reader)
    :enum     (des/read-enum reader)
    :fixed64  (des/read-fixed64 reader)
    :sfixed64 (des/read-sfixed64 reader)
    :double   (des/read-double reader)
    :string   (des/read-text reader)
    :bytes    (read-len-coded-bytes reader)
    :fixed32  (des/read-fixed32 reader)
    :sfixed32 (des/read-sfixed32 reader)
    :float    (des/read-float reader)
    (raise (str "Unexpected read-pri type:" field-type))))

(defn read-kv-pri
  "Read and return [key value] where value is a primitive type."
  [reader key-type val-type]
  (let [bin (read-len-coded-bytes reader)
        bin-reader (make-reader bin)
        _ (read-tag bin-reader)
        k (read-pri bin-reader key-type)
        _ (read-tag bin-reader)
        v (read-pri bin-reader val-type)]
    [k v]))

(defn read-kv-enum
  "Read and return [key value] where value is integer representation of enum."
  [reader key-type]
  (let [bin (read-len-coded-bytes reader)
        bin-reader (make-reader bin)
        _ (read-tag bin-reader)
        k (read-pri bin-reader key-type)
        _ (read-tag bin-reader)
        v (read-pri bin-reader :enum)]
    [k v]))

(defn read-kv-msg
  "Read and return [key value] where value is binary representation of protobuf message."
  [reader key-type]
  (let [bin (read-len-coded-bytes reader)
        bin-reader (make-reader bin)
        _ (read-tag bin-reader)
        k (read-pri bin-reader key-type)
        _ (read-tag bin-reader)
        v (read-len-coded-bytes bin-reader)]
    [k v]))

(defn read-packed
  "Read length encoded packed data return it as a vector.
     packed-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool |
                   :fixed32 | :sfixed32 | float |
                   :fixed64 | :sfixed64 | double |
                   :string"
  [reader packed-type]
  (let [bin (read-len-coded-bytes reader)
        bin-reader (make-reader bin)]
    (loop [values [(read-pri bin-reader packed-type)]]
      (if (not (available? bin-reader))
        values
        (recur (conj values (read-pri bin-reader packed-type)))))))

(defn read-raw-wire
  "Read next value generically based on wire-type. This function is typically used iff
   the actual field type is unknown or incompatible with the wire-type.
     wire-type 0 => read as varint64, return as int64
     wire-type 1 => read as sfixed64, return as int64
     wire-type 2 => read as len-value, return as binary
     wire-type 5 => read as sfixed32, return as int32"
  [reader wire-type]
  (case wire-type
    0 (des/read-varint64 reader true)
    1 (des/read-sfixed64 reader)
    2 (read-len-coded-bytes reader)
    5 (des/read-sfixed32 reader)))
