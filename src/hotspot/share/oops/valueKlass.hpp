/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_OOPS_VALUEKLASS_HPP
#define SHARE_VM_OOPS_VALUEKLASS_HPP

#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"

// A ValueKlass is a specialized InstanceKlass for value types.


class ValueKlass: public InstanceKlass {
  friend class VMStructs;
  friend class InstanceKlass;

 private:

  // Constructor
  ValueKlass(const ClassFileParser& parser)
    : InstanceKlass(parser, InstanceKlass::_misc_kind_value_type) {
    set_has_vcc_klass();
    // Addresses used for value type calling convention
    *((Array<SigEntry>**)adr_extended_sig()) = NULL;
    *((Array<VMRegPair>**)adr_return_regs()) = NULL;
    *((address*)adr_pack_handler()) = NULL;
    *((address*)adr_unpack_handler()) = NULL;
    assert(pack_handler() == NULL, "pack handler not null");
  }

  address adr_extended_sig() const {
    address adr_vcc = adr_vcc_klass();
    if (adr_vcc == NULL) {
      address adr_jf = adr_value_fields_klasses();
      if (adr_jf != NULL) {
        return adr_jf + this->java_fields_count() * sizeof(Klass*);
      }

      address adr_fing = adr_fingerprint();
      if (adr_fing != NULL) {
        return adr_fingerprint() + sizeof(u8);
      }

      InstanceKlass** adr_host = adr_host_klass();
      if (adr_host != NULL) {
        return (address)(adr_host + 1);
      }

      Klass** adr_impl = adr_implementor();
      if (adr_impl != NULL) {
        return (address)(adr_impl + 1);
      }

      return (address)end_of_nonstatic_oop_maps();
    } else {
      return adr_vcc + sizeof(Klass*);
    }
  }

  address adr_return_regs() const {
    return adr_extended_sig() + sizeof(intptr_t);
  }

  // pack and unpack handlers for value types return
  address adr_pack_handler() const {
    return (address)this + in_bytes(pack_handler_offset());
  }

  address adr_unpack_handler() const {
    return (address)this + in_bytes(unpack_handler_offset());
  }

  address pack_handler() const {
    return *(address*)adr_pack_handler();
  }

  address unpack_handler() const {
    return *(address*)adr_unpack_handler();
  }

  // static Klass* array_klass_impl(InstanceKlass* this_k, bool or_null, int n, TRAPS);

  GrowableArray<SigEntry> collect_fields(int base_off = 0) const;

  void cleanup_blobs();

 protected:
  // Returns the array class for the n'th dimension
  Klass* array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type
  Klass* array_klass_impl(bool or_null, TRAPS);

 public:
  // Type testing
  bool is_value_slow() const        { return true; }

  // Casting from Klass*
  static ValueKlass* cast(Klass* k) {
    assert(k->is_value(), "cast to ValueKlass");
    return (ValueKlass*) k;
  }

  // Use this to return the size of an instance in heap words
  // Implementation is currently simple because all value types are allocated
  // in Java heap like Java objects.
  virtual int size_helper() const {
    return layout_helper_to_size_helper(layout_helper());
  }

  // allocate_instance() allocates a stand alone value in the Java heap
  instanceOop allocate_instance(TRAPS);
  // allocate_buffered_or_heap_instance() tries to allocate a value in the
  // thread local value buffer, if allocation fails, it allocates it in the
  // Java heap
  instanceOop allocate_buffered_or_heap_instance(bool* in_heap, TRAPS);

  // minimum number of bytes occupied by nonstatic fields, HeapWord aligned or pow2
  int raw_value_byte_size() const;

  int first_field_offset() const;

  address data_for_oop(oop o) const {
    return ((address) (void*) o) + first_field_offset();
  }

  oop oop_for_data(address data) const {
    oop o = (oop) (data - first_field_offset());
    assert(oopDesc::is_oop(o, false), "Not an oop");
    return o;
  }

  void set_if_bufferable() {
    bool bufferable;
    if (contains_oops()) {
      bufferable = false;
    } else {
      int size_in_heap_words = size_helper();
      int base_offset = instanceOopDesc::base_offset_in_bytes();
      size_t size_in_bytes = size_in_heap_words * HeapWordSize - base_offset;
      bufferable = size_in_bytes <= BigValueTypeThreshold;
    }
    if (bufferable) {
      _extra_flags |= _extra_is_bufferable;
    } else {
      _extra_flags &= ~_extra_is_bufferable;
    }
  }

  bool is_bufferable() const          {
    return (_extra_flags & _extra_is_bufferable) != 0;
  }

  // Query if h/w provides atomic load/store
  bool is_atomic();

  bool flatten_array();

  bool contains_oops() const { return nonstatic_oop_map_count() > 0; }
  int nonstatic_oop_count();

  // Prototype general store methods...

  // copy the fields, with no concern for GC barriers
  void raw_field_copy(void* src, void* dst, size_t raw_byte_size);

  void value_store(void* src, void* dst, bool dst_is_heap, bool dst_uninitialized) {
    value_store(src, dst, nonstatic_field_size() << LogBytesPerHeapOop, dst_is_heap, dst_uninitialized);
  }

  // store the value of this klass contained with src into dst, raw data ptr
  void value_store(void* src, void* dst, size_t raw_byte_size, bool dst_is_heap, bool dst_uninitialized);

  oop unbox(Handle src, InstanceKlass* target_klass, TRAPS);
  oop box(Handle src, InstanceKlass* target_klass, TRAPS);

  // GC support...

  // oop iterate raw value type data pointer (where oop_addr may not be an oop, but backing/array-element)
  template <bool nv, typename T, class OopClosureType>
  inline void oop_iterate_specialized(const address oop_addr, OopClosureType* closure);

  template <bool nv, typename T, class OopClosureType>
  inline void oop_iterate_specialized_bounded(const address oop_addr, OopClosureType* closure, void* lo, void* hi);

  // calling convention support
  void initialize_calling_convention();
  Array<SigEntry>* extended_sig() const {
    assert(this != SystemDictionary::___Value_klass(), "make no sense for __Value");
    return *((Array<SigEntry>**)adr_extended_sig());
  }
  Array<VMRegPair>* return_regs() const {
    assert(this != SystemDictionary::___Value_klass(), "make no sense for __Value");
    return *((Array<VMRegPair>**)adr_return_regs());
  }
  void save_oop_fields(const RegisterMap& map, GrowableArray<Handle>& handles) const;
  bool save_oop_results(RegisterMap& map, GrowableArray<Handle>& handles) const;
  void restore_oop_results(RegisterMap& map, GrowableArray<Handle>& handles) const;
  oop realloc_result(const RegisterMap& reg_map, const GrowableArray<Handle>& handles, bool buffered, TRAPS);
  static ValueKlass* returned_value_type(const RegisterMap& reg_map);

  // pack and unpack handlers. Need to be loadable from generated code
  // so at a fixed offset from the base of the klass pointer.
  static ByteSize pack_handler_offset() {
    return in_ByteSize(InstanceKlass::header_size() * wordSize);
  }

  static ByteSize unpack_handler_offset() {
    return in_ByteSize((InstanceKlass::header_size()+1) * wordSize);
  }

  void deallocate_contents(ClassLoaderData* loader_data);
  static void cleanup(ValueKlass* ik) ;
};

#endif /* SHARE_VM_OOPS_VALUEKLASS_HPP */
