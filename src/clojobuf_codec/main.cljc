(ns clojobuf-codec.main
  (:require [clojobuf-codec.io.reader :refer [make-reader]]
            [clojobuf-codec.io.writer :refer [make-writer ->bytes]]
            [clojobuf-codec.encode :as enc]
            [clojobuf-codec.decode :as dec]
            [clojobuf-codec.deserialize :as des]))

;; ------------- writing -------------
(def writer (make-writer))

;; (enc/write-pri writer 7 :uint32 23)                           ; wire-type 0
;; (enc/write-pri writer 8 :string "abcd")                       ; wire-type 2
;; (enc/write-packed writer 9 :sint64 [12345 67890 13579 24680]) ; wire-type 2
;; (enc/write-kv-pri writer 10 :uint32 1024 :uint32 4201)        ; wire-type 2

(enc/write-kv-pri writer 11 :int32 123 :uint64 456)              ; wire-type 2
(enc/write-kv-enum writer 12 :int32 234 567)                     ; wire-type 2
(enc/write-kv-msg writer 13 :int32 345 (clojure.core/byte-array [1 2 3 4]))                     ; wire-type 2

(def out (->bytes writer))

;; ------------- reading -------------
(def reader (make-reader out))

;; (println (dec/read-tag reader))           ; [7 0]
;; (println (dec/read-value reader :uint32)) ; 23

;; (println (dec/read-tag reader))  ; [8 2]
;; (println (dec/read-text reader)) ; abcd

;; (println (dec/read-tag reader)) ; [9 2]
;; (let [bin (dec/read-value reader :bytes)
;;       reader2 (make-reader bin)]
;;   (println (dec/read-value reader2 :sint64))  ; 12345
;;   (println (dec/read-value reader2 :sint64))  ; 67890
;;   (println (dec/read-value reader2 :sint64))  ; 13579
;;   (println (dec/read-value reader2 :sint64))  ; 24680
;;   )

;; (println (dec/read-tag reader)) ; [10 2]
;; (println (dec/read-kv-pri reader :uint32 :uint32)) ; 1024 4201


(println (dec/read-tag reader)) ; [11 2]
(println (dec/read-kv-pri reader :int32 :uint64)) ; [123 456]

(println (dec/read-tag reader)) ; [12 2]
(println (dec/read-kv-enum reader :int32)) ; [234 567]

(println (dec/read-tag reader)) ; [13 2]
(println (dec/read-kv-msg reader :int32)) ; [345 #object[[B ...]]]


;; (def tag (dec/read-tag reader))
;; (def bin (dec/read-len-coded-bytes reader))
;; (def bin-reader (make-reader bin))

;; (dec/read-tag bin-reader)
;; (dec/read-value bin-reader :uint32)
;; (dec/read-tag bin-reader)
;; (dec/read-enum bin-reader)
