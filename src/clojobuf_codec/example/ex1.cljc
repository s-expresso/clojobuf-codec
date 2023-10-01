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
