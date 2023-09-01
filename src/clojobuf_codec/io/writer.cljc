(ns clojobuf-codec.io.writer
  #?(:clj (:import (java.io OutputStream
                            ByteArrayOutputStream))))

(defprotocol ByteWriter
  (write-byte [this char] "Writes a byte")
  #?(:clj (write-bytearray [this ba] "Writes a bytearray"))
  (->bytes [this]))

#?(:clj (extend-protocol ByteWriter
          OutputStream
          (write-byte
            ([this char] (.write this char)))
          (write-bytearray
            ([this ba] (.write this ba 0 (count ba))))
          (->bytes
            ([this] (.toByteArray this)))))

#?(:cljs (deftype ByteArrayWriter [^:unsynchronized-mutable buffer]
           ByteWriter
           (write-byte
             [_ char] (.push buffer char))
           (->bytes
             [_] buffer)))

#?(:clj (defn make-writer [] (ByteArrayOutputStream.)))
#?(:cljs (defn make-writer [] (->ByteArrayWriter (js/Array.))))
