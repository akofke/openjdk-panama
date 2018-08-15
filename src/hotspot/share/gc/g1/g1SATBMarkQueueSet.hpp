/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1SATBMARKQUEUE_HPP
#define SHARE_VM_GC_G1_G1SATBMARKQUEUE_HPP

#include "gc/g1/satbMarkQueue.hpp"

class G1CollectedHeap;
class JavaThread;

class G1SATBMarkQueueSet : public SATBMarkQueueSet {
  G1CollectedHeap* _g1h;

public:
  G1SATBMarkQueueSet();

  void initialize(G1CollectedHeap* g1h,
                  Monitor* cbl_mon, Mutex* fl_lock,
                  int process_completed_threshold,
                  uint buffer_enqueue_threshold_percentage,
                  Mutex* lock);

  static void handle_zero_index_for_thread(JavaThread* t);
  virtual SATBMarkQueue& satb_queue_for_thread(JavaThread* const t) const;
  virtual void filter(SATBMarkQueue* queue);
};

#endif // SHARE_VM_GC_G1_G1SATBMARKQUEUE_HPP
