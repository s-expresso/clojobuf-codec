(ns clojobuf-codec.util)

(defn raise [err-txt]
  #?(:clj (throw (Exception. err-txt)))
  #?(:cljs (throw (js/Error err-txt))))

(def >>> unsigned-bit-shift-right)
(def >> bit-shift-right)
(def << bit-shift-left)

#?(:cljs (defrecord HL32 [hi lo]))

#?(:cljs (defn make-HL32 [hi lo]
           (->HL32 hi lo)))

#?(:cljs (defn n64->HL32 [number64]
           (if (= number64 0)
             (->HL32 0 0)
             (let [neg (< number64 0)
                   magnitude (if neg (- number64) number64)
                   lo (>>> magnitude 0)
                   hi (-> magnitude (- lo) (/ 4294967296) (>>> 0))]
               (if neg
                 (let [lo- (-> lo (bit-not) (>>> 0))
                       hi- (-> hi (bit-not) (>>> 0))]
                   (cond (and (> lo- 4294967294) (> hi- 4294967294)) (->HL32 0 0)
                         (> lo- 4294967294) (->HL32 (+ hi- 1) 0)
                         :else (->HL32 hi- (+ lo- 1))))
                 (->HL32 hi lo))))))

#?(:cljs (defn HL32->n64 [^HL32 hl32 signed]
           (if (and signed (not= 0 (>>> (:hi hl32) 31)))
             (let [lo (-> (:lo hl32) (bit-not) (+ 1) (>>>))
                   hi (-> (:hi hl32) (bit-not) (>>>))]
               (if (zero? lo)
                 (-> hi (+ 1) (>>> 0) (* 4294967296) (-))
                 (-> hi (* 4294967296) (+ lo) (-))))
             (-> (:hi hl32) (* 4294967296) (+ (:lo hl32))))))

#?(:cljs (defn zigzag-encode-HL32 [hl32]
           (let [mask (-> (:hi hl32) (>> 31)) ; -1 if left-most bit is 1, 0 otherwise
                 hi (-> (:hi hl32) (<< 1) (bit-or (>>> (:lo hl32) 31)) (bit-xor mask) (>>> 0))
                 lo (-> (:lo hl32) (<< 1)                              (bit-xor mask) (>>> 0))]
             (->HL32 hi lo))))

#?(:cljs (defn zigzag-decode-HL32 [hl32]
           (let [mask (-> (:lo hl32) (bit-and 1) (-))
                 lo (-> (:lo hl32) (>>> 1) (bit-or (<< (:hi hl32) 31)) (bit-xor mask) (>>> 0))
                 hi (-> (:hi hl32) (>>> 1)                             (bit-xor mask) (>>> 0))]
             (->HL32 hi lo))))