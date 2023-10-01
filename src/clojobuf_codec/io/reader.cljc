(ns clojobuf-codec.io.reader
  #?(:clj (:import (java.io InputStream
                            ByteArrayInputStream)))
  #?(:cljs (:require [clojobuf-codec.io.writer :refer [make-writer write-byte ->bytes]])))

(defprotocol ByteReader
  (read-byte [this] "Reads a byte")
  (available? [this] "Has data available for reading?")
  (read-bytearray [this len] "Reads a bytearray of length 'len'"))

#?(:clj (extend-protocol ByteReader
          InputStream
          (read-byte
            ([this] (.read this)))
          (read-bytearray
            ([this len]
             (let [ba (byte-array len)]
               (.read this ba)
               ba)
             #_(doto (byte-array len) #(.read this %))))
          (available? ([this] (> (.available this) 0)))))

#?(:cljs (deftype ByteArrayReader [buffer
                                   ^:unsynchronized-mutable index]
           ByteReader
           (read-byte [_]
             (let [value (aget buffer index)]
               (set! index (inc index))
               value))
           (available? [_]
             (> (.-length buffer) index))
           #_(read-bytearray [_ len]
                             (let [begin index]
                               (set! index (+ index len)) ; move index to end of read region
                               (.subarray buffer begin index)))
           (read-bytearray [this len]
             (let [writer (make-writer)]
               (dotimes [_ len] (write-byte writer (read-byte this)))
               (->bytes writer)))))

#?(:clj (defn make-reader [bytes] (ByteArrayInputStream. bytes)))

#?(:cljs (defn make-reader [bytes] (->ByteArrayReader (js/Uint8Array. bytes) 0)))