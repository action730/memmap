# MemMap

Memmap aims to bring C-like struct mapping to Clojure with declarative structs and map like access. It can be used to read binary bufers, or memory mapped files; with the ability to modify both.

## Implementation

Memmap currently supports a few common data types with more coming and allows for custom data types to be provided with the stuct definition.

`(array-map ...)` is highly recommended to define structs since they preserve order, though nested vectors should also be fine.

## Limitations

Memmap currently cannot resize buffers.

## Usage

Require

```clojure
(require '[memmap.core :as mm])
```

Define a struct
```clojure
(def DOS-Header (array-map
                   :signature (mm/generic-buffer 2)
                   :lastsize :mm/short
                   :nblocks :mm/short
                   :nreloc :mm/short
                   :hdrsize :mm/short
                   :minalloc :mm/short
                   :maxalloc :mm/short
                   :ss-ptr :mm/short
                   :sp-ptr :mm/short
                   :checksum :mm/short
                   :ip-ptr :mm/short
                   :cs-ptr :mm/short
                   :relocpos :mm/short
                   :noverlay :mm/short
                   :reserved1 (mm/generic-buffer (* 4 (Short/BYTES)))
                   :oem_id :mm/short
                   :oem_info :mm/short
                   :reserved2 (mm/generic-buffer (* 10 (Short/BYTES)))
                   :e_lfanew :mm/le-uint))
```

Map the struct to a `ByteBuffer`

```clojure
(def dos-struct (mm/->mapped-struct buff DOS-Header))
```

Use the mapped struct like a map!

```clojure
(:e_lfanew dos-struct)
```

Jump to an offset in the buffer and parse more!

```clojure
(def coff-struct (mm/->mapped-struct (mm/jump-offset buf (:e_lfanew dos-struct)) COFF-Header))
```