(ns clojobuf-codec.main2
  (:require [clojobuf-codec.io.reader :refer [make-reader available?]]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.serialize :refer [write-int32 write-double write-text]]
            [clojobuf-codec.deserialize :refer [read-int32 read-double read-text]]))

(def writer (make-writer))

(write-int32 writer 23)
(write-double writer 123.456)
(write-text writer "the quick brown fox")

(def out (->bytes writer))

(def reader (make-reader out))

(println (if (available? reader) (read-int32 reader) "n/a"))  ; 23
(println (if (available? reader) (read-double reader) "n/a")) ; 123.456
(println (if (available? reader) (read-text reader) "n/a"))   ; the quick brown fox"
(println (if (available? reader) (read-int32 reader) "n/a"))  ; n/a
