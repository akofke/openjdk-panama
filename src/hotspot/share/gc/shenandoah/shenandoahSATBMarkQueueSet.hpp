/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHSATBMARKQUEUESET_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHSATBMARKQUEUESET_HPP

#include "gc/shared/satbMarkQueue.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"

class ShenandoahSATBMarkQueue: public SATBMarkQueue {
public:
  ShenandoahSATBMarkQueue(SATBMarkQueueSet* qset) : SATBMarkQueue(qset, /* permanent = */ false) {}
  virtual bool should_enqueue_buffer();
};

class ShenandoahSATBMarkQueueSet : public SATBMarkQueueSet {
private:
  ShenandoahHeap* _heap;
  BufferNode::Allocator _satb_mark_queue_buffer_allocator;
public:
  ShenandoahSATBMarkQueueSet();

  void initialize(ShenandoahHeap* const heap,
                  Monitor* cbl_mon,
                  int process_completed_threshold,
                  uint buffer_enqueue_threshold_percentage,
                  Mutex* lock);

  virtual SATBMarkQueue& satb_queue_for_thread(JavaThread* const t) const;
  virtual void filter(SATBMarkQueue* queue);
};

#endif
