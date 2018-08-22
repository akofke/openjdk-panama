/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1MonitoringSupport.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1MemoryPool.hpp"
#include "gc/shared/hSpaceCounters.hpp"
#include "memory/metaspaceCounters.hpp"
#include "services/memoryPool.hpp"

class G1GenerationCounters : public GenerationCounters {
protected:
  G1MonitoringSupport* _g1mm;

public:
  G1GenerationCounters(G1MonitoringSupport* g1mm,
                       const char* name, int ordinal, int spaces,
                       size_t min_capacity, size_t max_capacity,
                       size_t curr_capacity)
  : GenerationCounters(name, ordinal, spaces, min_capacity,
                       max_capacity, curr_capacity), _g1mm(g1mm) { }
};

class G1YoungGenerationCounters : public G1GenerationCounters {
public:
  // We pad the capacity three times given that the young generation
  // contains three spaces (eden and two survivors).
  G1YoungGenerationCounters(G1MonitoringSupport* g1mm, const char* name, size_t max_size)
  : G1GenerationCounters(g1mm, name, 0 /* ordinal */, 3 /* spaces */,
                         G1MonitoringSupport::pad_capacity(0, 3) /* min_capacity */,
                         G1MonitoringSupport::pad_capacity(max_size, 3),
                         G1MonitoringSupport::pad_capacity(0, 3) /* curr_capacity */) {
    if (UsePerfData) {
      update_all();
    }
  }

  virtual void update_all() {
    size_t committed =
              G1MonitoringSupport::pad_capacity(_g1mm->young_gen_committed(), 3);
    _current_size->set_value(committed);
  }
};

class G1OldGenerationCounters : public G1GenerationCounters {
public:
  G1OldGenerationCounters(G1MonitoringSupport* g1mm, const char* name, size_t max_size)
  : G1GenerationCounters(g1mm, name, 1 /* ordinal */, 1 /* spaces */,
                         G1MonitoringSupport::pad_capacity(0) /* min_capacity */,
                         G1MonitoringSupport::pad_capacity(max_size),
                         G1MonitoringSupport::pad_capacity(0) /* curr_capacity */) {
    if (UsePerfData) {
      update_all();
    }
  }

  virtual void update_all() {
    size_t committed =
              G1MonitoringSupport::pad_capacity(_g1mm->old_gen_committed());
    _current_size->set_value(committed);
  }
};

G1MonitoringSupport::G1MonitoringSupport(G1CollectedHeap* g1h) :
  _g1h(g1h),
  _incremental_memory_manager("G1 Young Generation", "end of minor GC"),
  _full_gc_memory_manager("G1 Old Generation", "end of major GC"),
  _eden_space_pool(NULL),
  _survivor_space_pool(NULL),
  _old_gen_pool(NULL),
  _incremental_collection_counters(NULL),
  _full_collection_counters(NULL),
  _conc_collection_counters(NULL),
  _young_gen_counters(NULL),
  _old_gen_counters(NULL),
  _old_space_counters(NULL),
  _eden_space_counters(NULL),
  _from_space_counters(NULL),
  _to_space_counters(NULL),

  _overall_committed(0),
  _overall_used(0),
  _young_gen_committed(0),
  _old_gen_committed(0),

  _eden_space_committed(0),
  _eden_space_used(0),
  _survivor_space_committed(0),
  _survivor_space_used(0),
  _old_gen_used(0) {

  recalculate_sizes();

  // Counters for garbage collections
  //
  //  name "collector.0".  In a generational collector this would be the
  // young generation collection.
  _incremental_collection_counters =
    new CollectorCounters("G1 incremental collections", 0);
  //   name "collector.1".  In a generational collector this would be the
  // old generation collection.
  _full_collection_counters =
    new CollectorCounters("G1 stop-the-world full collections", 1);
  //   name "collector.2".  In a generational collector this would be the
  // STW phases in concurrent collection.
  _conc_collection_counters =
    new CollectorCounters("G1 stop-the-world phases", 2);

  // "Generation" and "Space" counters.
  //
  //  name "generation.1" This is logically the old generation in
  // generational GC terms.  The "1, 1" parameters are for
  // the n-th generation (=1) with 1 space.
  // Counters are created from minCapacity, maxCapacity, and capacity
  _old_gen_counters = new G1OldGenerationCounters(this, "old", _g1h->max_capacity());

  //  name  "generation.1.space.0"
  // Counters are created from maxCapacity, capacity, initCapacity,
  // and used.
  _old_space_counters = new HSpaceCounters(_old_gen_counters->name_space(),
    "space", 0 /* ordinal */,
    pad_capacity(g1h->max_capacity()) /* max_capacity */,
    pad_capacity(_old_gen_committed) /* init_capacity */);

  //   Young collection set
  //  name "generation.0".  This is logically the young generation.
  //  The "0, 3" are parameters for the n-th generation (=0) with 3 spaces.
  // See  _old_collection_counters for additional counters
  _young_gen_counters = new G1YoungGenerationCounters(this, "young", _g1h->max_capacity());

  const char* young_collection_name_space = _young_gen_counters->name_space();

  //  name "generation.0.space.0"
  // See _old_space_counters for additional counters
  _eden_space_counters = new HSpaceCounters(young_collection_name_space,
    "eden", 0 /* ordinal */,
    pad_capacity(g1h->max_capacity()) /* max_capacity */,
    pad_capacity(_eden_space_committed) /* init_capacity */);

  //  name "generation.0.space.1"
  // See _old_space_counters for additional counters
  // Set the arguments to indicate that this survivor space is not used.
  _from_space_counters = new HSpaceCounters(young_collection_name_space,
    "s0", 1 /* ordinal */,
    pad_capacity(0) /* max_capacity */,
    pad_capacity(0) /* init_capacity */);
  // Given that this survivor space is not used, we update it here
  // once to reflect that its used space is 0 so that we don't have to
  // worry about updating it again later.
  _from_space_counters->update_used(0);

  //  name "generation.0.space.2"
  // See _old_space_counters for additional counters
  _to_space_counters = new HSpaceCounters(young_collection_name_space,
    "s1", 2 /* ordinal */,
    pad_capacity(g1h->max_capacity()) /* max_capacity */,
    pad_capacity(_survivor_space_committed) /* init_capacity */);
}

G1MonitoringSupport::~G1MonitoringSupport() {
  delete _eden_space_pool;
  delete _survivor_space_pool;
  delete _old_gen_pool;
}

void G1MonitoringSupport::initialize_serviceability() {
  _eden_space_pool = new G1EdenPool(_g1h, _eden_space_committed);
  _survivor_space_pool = new G1SurvivorPool(_g1h, _survivor_space_committed);
  _old_gen_pool = new G1OldGenPool(_g1h, _old_gen_committed, _g1h->max_capacity());

  _full_gc_memory_manager.add_pool(_eden_space_pool);
  _full_gc_memory_manager.add_pool(_survivor_space_pool);
  _full_gc_memory_manager.add_pool(_old_gen_pool);

  _incremental_memory_manager.add_pool(_eden_space_pool);
  _incremental_memory_manager.add_pool(_survivor_space_pool);
  _incremental_memory_manager.add_pool(_old_gen_pool, false /* always_affected_by_gc */);
}

GrowableArray<GCMemoryManager*> G1MonitoringSupport::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(&_incremental_memory_manager);
  memory_managers.append(&_full_gc_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> G1MonitoringSupport::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(3);
  memory_pools.append(_eden_space_pool);
  memory_pools.append(_survivor_space_pool);
  memory_pools.append(_old_gen_pool);
  return memory_pools;
}

void G1MonitoringSupport::recalculate_sizes() {
  assert_heap_locked_or_at_safepoint(true);

  // Recalculate all the sizes from scratch.

  uint young_list_length = _g1h->young_regions_count();
  uint survivor_list_length = _g1h->survivor_regions_count();
  assert(young_list_length >= survivor_list_length, "invariant");
  uint eden_list_length = young_list_length - survivor_list_length;
  // Max length includes any potential extensions to the young gen
  // we'll do when the GC locker is active.
  uint young_list_max_length = _g1h->g1_policy()->young_list_max_length();
  assert(young_list_max_length >= survivor_list_length, "invariant");
  uint eden_list_max_length = young_list_max_length - survivor_list_length;

  _overall_used = _g1h->used_unlocked();
  _eden_space_used = (size_t) eden_list_length * HeapRegion::GrainBytes;
  _survivor_space_used = (size_t) survivor_list_length * HeapRegion::GrainBytes;
  _old_gen_used = subtract_up_to_zero(_overall_used, _eden_space_used + _survivor_space_used);

  // First calculate the committed sizes that can be calculated independently.
  _survivor_space_committed = _survivor_space_used;
  _old_gen_committed = HeapRegion::align_up_to_region_byte_size(_old_gen_used);

  // Next, start with the overall committed size.
  _overall_committed = _g1h->capacity();
  size_t committed = _overall_committed;

  // Remove the committed size we have calculated so far (for the
  // survivor and old space).
  assert(committed >= (_survivor_space_committed + _old_gen_committed), "sanity");
  committed -= _survivor_space_committed + _old_gen_committed;

  // Next, calculate and remove the committed size for the eden.
  _eden_space_committed = (size_t) eden_list_max_length * HeapRegion::GrainBytes;
  // Somewhat defensive: be robust in case there are inaccuracies in
  // the calculations
  _eden_space_committed = MIN2(_eden_space_committed, committed);
  committed -= _eden_space_committed;

  // Finally, give the rest to the old space...
  _old_gen_committed += committed;
  // ..and calculate the young gen committed.
  _young_gen_committed = _eden_space_committed + _survivor_space_committed;

  assert(_overall_committed ==
         (_eden_space_committed + _survivor_space_committed + _old_gen_committed),
         "the committed sizes should add up");
  // Somewhat defensive: cap the eden used size to make sure it
  // never exceeds the committed size.
  _eden_space_used = MIN2(_eden_space_used, _eden_space_committed);
  // _survivor_committed and _old_committed are calculated in terms of
  // the corresponding _*_used value, so the next two conditions
  // should hold.
  assert(_survivor_space_used <= _survivor_space_committed, "post-condition");
  assert(_old_gen_used <= _old_gen_committed, "post-condition");
}

void G1MonitoringSupport::update_sizes() {
  recalculate_sizes();
  if (UsePerfData) {
    _eden_space_counters->update_capacity(pad_capacity(_eden_space_committed));
    _eden_space_counters->update_used(_eden_space_used);
   // only the "to" survivor space is active, so we don't need to
    // update the counters for the "from" survivor space
    _to_space_counters->update_capacity(pad_capacity(_survivor_space_committed));
    _to_space_counters->update_used(_survivor_space_used);
    _old_space_counters->update_capacity(pad_capacity(_old_gen_committed));
    _old_space_counters->update_used(_old_gen_used);

    _young_gen_counters->update_all();
    _old_gen_counters->update_all();

    MetaspaceCounters::update_performance_counters();
    CompressedClassSpaceCounters::update_performance_counters();
  }
}

void G1MonitoringSupport::update_eden_size() {
  // Recalculate everything - this is fast enough.
  recalculate_sizes();
  if (UsePerfData) {
    _eden_space_counters->update_used(_eden_space_used);
  }
}

G1MonitoringScope::G1MonitoringScope(G1MonitoringSupport* g1mm, bool full_gc, bool all_memory_pools_affected) :
  _tcs(full_gc ? g1mm->_full_collection_counters : g1mm->_incremental_collection_counters),
  _tms(full_gc ? &g1mm->_full_gc_memory_manager : &g1mm->_incremental_memory_manager,
       G1CollectedHeap::heap()->gc_cause(), all_memory_pools_affected) {
}
