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

#ifndef SHARE_VM_OOPS_VALUEARRAYKLASS_HPP
#define SHARE_VM_OOPS_VALUEARRAYKLASS_HPP

#include "classfile/classLoaderData.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/valueKlass.hpp"
#include "utilities/macros.hpp"

/**
 * Array of values, gives a layout of typeArrayOop, but needs oops iterators
 */
class ValueArrayKlass : public ArrayKlass {
  friend class VMStructs;
 private:
  // Constructor
  ValueArrayKlass(Klass* element_klass, Symbol* name);

  static ValueArrayKlass* allocate_klass(Klass* element_klass, Symbol* name, TRAPS);
protected:
  // Returns the ArrayKlass for n'th dimension.
  Klass* array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type.
  Klass* array_klass_impl(bool or_null, TRAPS);

 public:

  ValueArrayKlass() {}

  virtual ValueKlass* element_klass() const;
  virtual void set_element_klass(Klass* k);

  // Casting from Klass*
  static ValueArrayKlass* cast(Klass* k) {
    assert(k->is_valueArray_klass(), "cast to ValueArrayKlass");
    return (ValueArrayKlass*) k;
  }

  // klass allocation
  static ValueArrayKlass* allocate_klass(Klass* element_klass, TRAPS);

  void initialize(TRAPS);

  ModuleEntry* module() const;
  PackageEntry* package() const;

  bool can_be_primary_super_slow() const;
  GrowableArray<Klass*>* compute_secondary_supers(int num_extra_slots);
  bool compute_is_subtype_of(Klass* k);

  int element_byte_size() const { return 1 << layout_helper_log2_element_size(_layout_helper); }

  bool is_valueArray_klass_slow() const { return true; }

  bool contains_oops() {
    return element_klass()->contains_oops();
  }

  bool is_atomic() {
    return element_klass()->is_atomic();
  }

  oop protection_domain() const;

  static jint array_layout_helper(ValueKlass* vklass); // layout helper for values

  // sizing
  static int header_size()  { return sizeof(ValueArrayKlass)/HeapWordSize; }
  int size() const          { return ArrayKlass::static_size(header_size()); }

  jint max_elements() const;

  int oop_size(oop obj) const;

  // Oop Allocation
  oop allocate(int length, bool do_zero, TRAPS);
  oop multi_allocate(int rank, jint* sizes, TRAPS);

  // Naming
  const char* internal_name() const { return external_name(); }

  // Copying
  void copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS);

  // GC specific object visitors
  //
  // Mark Sweep
  int oop_ms_adjust_pointers(oop obj);

#if INCLUDE_ALL_GCS
  // Parallel Scavenge
  void oop_ps_push_contents(  oop obj, PSPromotionManager* pm);
  // Parallel Compact
  void oop_pc_follow_contents(oop obj, ParCompactionManager* cm);
  void oop_pc_update_pointers(oop obj, ParCompactionManager* cm);
#endif

 private:
  template <bool nv, typename OopClosureType>
  inline void oop_oop_iterate(oop obj, OopClosureType* closure);
  template <bool nv, typename OopClosureType>
  inline void oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  template <bool nv, typename T, class OopClosureType>
  inline void oop_oop_iterate_elements_specialized(valueArrayOop a, OopClosureType* closure);

  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_elements_bounded(valueArrayOop a, OopClosureType* closure, MemRegion mr);

  template <bool nv, typename T, class OopClosureType>
  inline void oop_oop_iterate_elements_specialized_bounded(valueArrayOop a, OopClosureType* closure, void* low, void* high);

 public:
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_elements(valueArrayOop a, OopClosureType* closure);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_RANGE)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_RANGE)

#if INCLUDE_ALL_GCS
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_NO_BACKWARDS)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_NO_BACKWARDS)
#endif // INCLUDE_ALL_GCS

 public:
  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  void oop_print_value_on(oop obj, outputStream* st);
#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st);
#endif

  // Verification
  void verify_on(outputStream* st);
  void oop_verify_on(oop obj, outputStream* st);
};

#endif

