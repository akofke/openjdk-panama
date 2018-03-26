/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_GC_SHARED_GCARGUMENTS_HPP
#define SHARE_GC_SHARED_GCARGUMENTS_HPP

#include "memory/allocation.hpp"

class CollectedHeap;

class GCArguments : public CHeapObj<mtGC> {
private:
  static GCArguments* _instance;

  static void select_gc();
  static void select_gc_ergonomically();
  static bool gc_selected();

protected:
  template <class Heap, class Policy>
  CollectedHeap* create_heap_with_policy();

public:
  static jint initialize();
  static bool is_initialized();
  static GCArguments* arguments();

  virtual void initialize_flags();

  virtual size_t conservative_max_heap_alignment() = 0;

  virtual CollectedHeap* create_heap() = 0;
};

#endif // SHARE_GC_SHARED_GCARGUMENTS_HPP
