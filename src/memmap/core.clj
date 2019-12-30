(ns memmap.core
  (:import 
    [java.nio ByteBuffer ByteOrder]))

(defrecord field-type [size get set])

(defn generic-buffer [size]
  (->field-type size 
                (fn [buff offset]
                  (-> buff
                      (.position offset)
                      (.slice)
                      (.limit size)))
                (fn [buff offset data] (println "this should set a generic buffer"))))

(def default-types
  {::short (->field-type (Short/BYTES) #(.getShort %1 %2) #(.putShort %1 %2 %3))
   ::le-short (->field-type (Short/BYTES) #(Short/reverseBytes (.getShort %1 %2)) #(.putShort %1 %2 (Short/reverseBytes %3)))
   ::int   (->field-type (Integer/BYTES) #(.getInt %1 %2) #(.putInt %1 %2 %3))
   ::le-uint (->field-type (Integer/BYTES) 
                        (fn [buff offset]
                          (let [int-buf (ByteBuffer/allocate 8)]
                            (.position buff offset)
                            (.get buff (.array int-buf) 0 4)
                            (Long/reverseBytes (.getLong int-buf))))
                        (fn [buff offset data]
                          (.position buff offset)
                          (.put buff (.getBytes data) 3 4)))
   ::uint (->field-type (Integer/BYTES) 
                        (fn [buff offset]
                          (let [int-buf (ByteBuffer/allocate 8)]
                            (.position buff offset)
                            (.get buff (.array int-buf) 3 4)
                            (.getLong int-buf)))
                        (fn [buff offset data]
                          (.position buff offset)
                          (.put buff (.getBytes data) 3 4)))
   ::le-int (->field-type (Integer/BYTES) #(Integer/reverseBytes (.getInt %1 %2)) #(.putInt %1 %2 (Integer/reverseBytes %3)))
   ::byte  (->field-type (Byte/BYTES) #(.get %1 %2) #(.put %1 %2 %3))
   ::char (->field-type 1 #(char (.get %1 %2)) #(.put %1 %2 (byte (int %3))))
   ::wchar (->field-type (Character/BYTES) #(.getChar %1 %2) #(.putChar %1 %2 %3))})

(defn jump-offset [buff offset]
  (.slice (.position buff offset)))

(defn field->type [fields target-field]
  (let [ftype (get fields target-field)]
    (if (keyword? ftype)
      (get default-types ftype)
      ftype)))

(defn calc-offset [fields target]
  (->> fields
       (take-while #(not= target (first %)))
       (mapv (fn [[field _]] (:size (field->type fields field))))
       (reduce +)))

(deftype mapped-struct
         [src-buf fields]
  
  clojure.lang.Associative
  (containsKey [_ k]
    (contains? fields k))
  
  clojure.lang.ILookup
  (valAt [_ k not-found]
    ((:get (field->type fields k)) src-buf (calc-offset fields k)))
  (valAt [this k]
    (.valAt this k nil))
  
  clojure.lang.IPersistentMap
  (assoc [this k v]
    ((:set (field->type fields k)) src-buf (calc-offset fields k) v)
    this)
  
  clojure.lang.Seqable
  (seq [_] (seq fields)))


(comment
  ; create a simple buffer with a few fields
  (def testBuffer (-> (ByteBuffer/allocate 8)
                      (.putShort 1)
                      (.putInt 2)
                      (.put (byte \a))
                      (.put (byte \b))))

  (def simple (->mapped-struct testBuffer (array-map
                                           :s  ::short
                                           :i ::int
                                           :c1 ::char
                                           :c2 ::char)))

  (def nested-outer (->mapped-struct testBuffer (array-map
                                                 :s ::short
                                                 :i ::int
                                                 :inner (generic-buffer 2))))
  
  ; map a buffer that is just a segment of the larger buffer
  (def nested-inner (->mapped-struct (:inner nested-outer) (array-map
                                                            :c1 ::char
                                                            :c2 ::char)))
  
  
  ; load a memory mapped file
  (import (java.io RandomAccessFile) 
          java.nio.channels.FileChannel$MapMode)
  
  (def f (let [fchan (.getChannel (RandomAccessFile. "test.exe" "r"))]
           
           (-> (.map fchan FileChannel$MapMode/READ_ONLY 0 (.size fchan))
               .load)))

  
  (def DOS-Header (array-map
                   :signature (generic-buffer 2)
                   :lastsize ::short
                   :nblocks ::short
                   :nreloc ::short
                   :hdrsize ::short
                   :minalloc ::short
                   :maxalloc ::short
                   :ss-ptr ::short
                   :sp-ptr ::short
                   :checksum ::short
                   :ip-ptr ::short
                   :cs-ptr ::short
                   :relocpos ::short
                   :noverlay ::short
                   :reserved1 (generic-buffer (* 4 (Short/BYTES)))
                   :oem_id ::short
                   :oem_info ::short
                   :reserved2 (generic-buffer (* 10 (Short/BYTES)))
                   :e_lfanew ::le-uint))
  (def DOS-Signature (array-map
                      :m ::char
                      :z ::char))
  
  
  (def pe (->mapped-struct f DOS-Header))
  (def mz-sig (->mapped-struct (:signature pe) DOS-Signature))
  
  ;jump to the COFF Header
  ;(def coff (->mapped-struct (jump-offset f (:e_lfanew pe)) COFF-Header))
  )
  