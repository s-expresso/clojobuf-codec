clojobuf-codec
==============

Low level clojure(script) encoding and decoding library for [google's protobuf binary format](https://protobuf.dev/programming-guides/encoding/).

It can also be used as a general purpose codec of protobuf binary format for any custom scheme of your design.

## Usage
Add the following to deps.edn (or its equivalent for lein).
```edn
{:deps
 s-expresso/clojobuf-codec {:git/url "https://github.com/s-expresso/clojobuf-codec.git"
                            :git/sha "9503c903ebcb8d09b183b7c6349f765e98f1dcbc"
                            :git/tag "v0.1.5"}}
```

## Codec for protobuf 
Example usage:

```clojure
(ns clojobuf-codec.example.ex1
  (:require [clojobuf-codec.io.reader :refer [make-reader]]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.decode :refer [read-pri read-tag]]
            [clojobuf-codec.encode :refer [write-pri]]))

(def writer (make-writer))

(write-pri writer 1 :uint32 23) ; field num 1
(write-pri writer 2 :double 123.455) ; field num 2
(write-pri writer 3 :string "the quick brown fox") ; field num 3

(def out (->bytes writer))

(def reader (make-reader out))

(read-tag reader)         ; => [1 0] field num 1, wire type 0
(read-pri reader :uint32) ; => 23

(read-tag reader)         ; => [2 1] field num 2, wire type 1
(read-pri reader :double) ; => 123.456

(read-tag reader)         ; => [3 2] field num 3, wire type 2
(read-pri reader :string) ; => "the quick brown fox"
```

Protobuf messages uses [google's protobuf binary format](https://protobuf.dev/programming-guides/encoding/) that encodes each field as [Tag-Length-Value](https://en.wikipedia.org/wiki/Type%E2%80%93length%E2%80%93value).

### Functions to encode protobuf field

For encoding, use functions in `clojobuf-codec.encode`:
```clojure
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
  ...)

(defn write-pri
  "Write a primitive field where
    writer     = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num  = field number
    field-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool | :enum
                 :fixed32 | :sfixed32 | :float |
                 :fixed64 | :sfixed64 | :double |
                 :string | :bytes
    value      = value corresponding to field-type above"
  [writer field-num field-type value] ...)

(defn write-packed
  "Write a sequence as packed
    writer     = clojobuf-codec.io.writer.ByteWriter (defprotocol)
    field-num  = field number
    field-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool |
                 :fixed32 | :sfixed32 | float |
                 :fixed64 | :sfixed64 | double |
                 :string
    values     = sequence to be encoded"
  [writer field-num field-type values] ...)

```

### Functions to decode protobuf field

For decoding, use functions in `clojobuf-codec.decode`:
```clojure
(ns clojobuf-codec.decode
  "Decoding a protobuf encoded message is a 2 steps process performed repeatedly:
    (1) call `(read-tag reader)` to get `field-id` and `wire-type`
    (2) look up `field-id` in your protobuf schema to determine `field-type`
        (A) `field-type` == primitive
            (a) not :bytes/:string && wire-type 2 => `(read-packed reader field-type)`
            (b) all other cases                   => `(read-pri    reader field-type)`
        (B) `field-type` == message               => `(read-len-coded-bytes reader)`
        (C) `field-type` == map                   => decode it like message type
  where `reader` is `clojobuf-codec.io.reader.ByteReader` reading the binary.

  For (B), a binary representation of protobuf message is returned. Build a new reader
  by calling `make-reader` on the returned binary, then decode reader as a new message.

  For (C), you decode it like a message because a protobuf map is encoded as a 
  message using field-id 1 to represent key and field-id 2 to represent value.

  If (2)'s look up of `field-id` fails, then sender is using a schema with additional
  fields. Use `read-raw-wire` to extract the value generically and continue.
     
  If (2)'s look up of `field-id` yields a `field-type` incompatible with the `wire-type`,
  then sender is using a schema that has breaking change. If you want to continue
  decoding, you must honour sender's wire-type and use `read-raw-wire` to preserve the
  correctness of Tag-(Len-)Value boundary."
  ...)

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
  [reader field-type] ...)

(defn read-packed
  "Read length encoded packed data return it as a vector.
     packed-type = :int32 | :int64 | :uint32 :uint64 | :sint32 | :sint64 | :bool |
                   :fixed32 | :sfixed32 | float |
                   :fixed64 | :sfixed64 | double |
                   :string"
  [reader packed-type] ...)

(defn read-len-coded-bytes
  "Read the next value as varint N, and return the next N bytes as binary."
  [reader] ...)

(defn read-raw-wire
  "Read next value generically based on wire-type. This function is typically used iff
   the actual field type is unknown or incompatible with the wire-type.
     wire-type 0 => read as varint64, return as int64
     wire-type 1 => read as sfixed64, return as int64
     wire-type 2 => read as len-value, return as binary
     wire-type 5 => read as sfixed32, return as int32"
  [reader wire-type] ...)
```

 While it is possible to use the lower level functions in `clojobuf-codec.serialize` and `clojobuf-codec.deserialize` (see below) for protobuf, it is unnecessary and not recommended.

## Codec for custom schema (i.e. not protobuf)

This is useful if you have own data schema and need a library to help you serialize and deserialize it using protobuf binary format. Protobuf binary format is a good choice because it is platform agnostic and is well supported on many programming languages. Serialization/deserialization libraries are readily available shall cross-language interop is needed. You can of course also transfer binary data between your clojure backend and clojurescript frontend, for say performance reason if the data set is huge and mostly number.

Example usage:
```clojure
(ns my-ns.main
  (:require [clojobuf-codec.io.reader :refer [make-reader]]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.serialize :refer [write-int32 write-double write-text]]
            [clojobuf-codec.deserialize :refer [read-int32 read-double read-text]]))

(def writer (make-writer))

(write-int32 writer 23)
(write-double writer 123.456)
(write-text writer "the quick brown fox")

(def out (->bytes writer))

(def reader (make-reader out))

(println (read-int32 reader)) ; 23
(println (read-double reader)) ; 123.456
(println (read-text reader)) ; the quick brown fox"
```

You have the following choices of binary formats for different data types.

Integer type:
* *varint*: a variable length integer format that is space optimized for small positive numbers
* *zigzag varint*: similar to varint, but optimized for small positive and negative numbers
* *fixed32/64*: self explanatory

Decimal type:
* *double*: 64bit decimal representation
* *float*: 32bit decimal representation

String type:
* *utf-8*: auto converted into utf-8 and prepended with length

### Functions to serialize data

For encoding, use functions in `clojobuf-codec.serialize`
```clojure
(defn write-int32    [writer data] ...) ; write varint
(defn write-int64    [writer data] ...) ; write varint

(defn write-uint32   [writer data] ...) ; write varint
(defn write-uint64   [writer data] ...) ; write varint

(defn write-sint32   [writer data] ...) ; write zigzag varint
(defn write-sint64   [writer data] ...) ; write zigzag varint

(defn write-fixed32  [writer data] ...) ; write 32 bits
(defn write-fixed64  [writer data] ...) ; write 64 bits

(defn write-double   [writer data] ...) ; write 64 bits
(defn write-float    [writer data] ...) ; write 32 bits

(defn write-bool     [writer data] ...) ; write varint
(defn write-enum     [writer data] ...) ; write varint

(defn write-bytes    [writer data] ...) ; write binary data
(defn write-text     [writer data] ...) ; write data as utf-8 string, prepended with length info encoded as varint
```
where `varint` and `zigzag varint` are [google's protobuf binary format](https://protobuf.dev/programming-guides/encoding/) way of using less bytes for small value.

### Functions to deserialize data

For decoding, use functions in `clojobuf-codec.deserialize`
```clojure
(defn read-int32    [reader] ...)
(defn read-int64    [reader] ...)

(defn read-uint32   [reader] ...)
(defn read-uint64   [reader] ...)

(defn read-sint32   [reader] ...)
(defn read-sint64   [reader] ...)

(defn read-fixed32  [reader] ...)
(defn read-fixed64  [reader] ...)

(defn read-double   [reader] ...)
(defn read-float    [reader] ...)

(defn read-bool     [reader] ...)
(defn read-enum     [reader] ...)

(defn read-bytes    [reader] ...)
(defn read-text     [reader] ...)
```

## Acknowledgements
Great artists steal. CLJS version of this library is only possible because [protobuf.js](https://github.com/protobufjs/protobuf.js/) showed the way. If this library's CLJS code works for you then credit goes to [protobuf.js](https://github.com/protobufjs/protobuf.js/), if it doesn't then the fault is mine to have ported the code wrongly.

PS: js bigint handling isn't ported over yet
