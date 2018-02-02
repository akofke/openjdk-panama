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

#ifndef SHARE_VM_GC_SHARED_GENMEMORYPOOLS_HPP
#define SHARE_VM_GC_SHARED_GENMEMORYPOOLS_HPP

#include "services/memoryPool.hpp"

class ContiguousSpace;
class DefNewGeneration;
class Generation;

class ContiguousSpacePool : public CollectedMemoryPool {
private:
  ContiguousSpace* _space;

public:
  ContiguousSpacePool(ContiguousSpace* space,
                      const char* name,
                      size_t max_size,
                      bool support_usage_threshold);

  ContiguousSpace* space() { return _space; }
  MemoryUsage get_memory_usage();
  size_t used_in_bytes();
};

class SurvivorContiguousSpacePool : public CollectedMemoryPool {
private:
  DefNewGeneration* _young_gen;

public:
  SurvivorContiguousSpacePool(DefNewGeneration* young_gen,
                              const char* name,
                              size_t max_size,
                              bool support_usage_threshold);

  MemoryUsage get_memory_usage();

  size_t used_in_bytes();
  size_t committed_in_bytes();
};

class GenerationPool : public CollectedMemoryPool {
private:
  Generation* _gen;
public:
  GenerationPool(Generation* gen, const char* name, bool support_usage_threshold);

  MemoryUsage get_memory_usage();
  size_t used_in_bytes();
};

#endif // SHARE_VM_GC_SHARED_GENMEMORYPOOLS_HPP
