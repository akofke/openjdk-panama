/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.nicl;

import jdk.internal.misc.Unsafe;
import jdk.internal.nicl.Util;
import jdk.internal.nicl.types.BoundedMemoryRegion;
import jdk.internal.nicl.types.BoundedPointer;

import java.nicl.types.LayoutType;
import java.nicl.types.Pointer;
import java.nicl.types.Reference;
import java.nicl.types.Resource;

/**
 * A unit of resources allocation that can be released all together.
 * The NativeScope object is not thread safe, and should be used by a
 * single thread, and the thread is responsible for transaction
 * handling.
 */
public class NativeScope implements Scope {
    // FIXME: Move this, make it dynamic and correct
    private final static long ALLOC_ALIGNMENT = 8;

    private final static Unsafe U = Unsafe.getUnsafe();
    // 64KB block
    private final static long UNIT_SIZE = 64 * 1024;

    // the address of allocated memory
    private final long block;
    // the first offset of available memory
    private long free_offset;
    // the free offset when start transaction
    private long transaction_origin;

    private boolean isAlive = true;


    public NativeScope() {
        block = U.allocateMemory(UNIT_SIZE);
        free_offset = 0;
        transaction_origin = -1;
    }

    @Override
    public void checkAlive() {
        if (!isAlive) {
            throw new IllegalStateException("Scope is not alive");
        }
    }

    @Deprecated
    @Override
    public void startAllocation() {
        if (transaction_origin >= 0) {
            throw new IllegalStateException("A transaction in progress");
        }
        transaction_origin = free_offset;
    }

    private void rollbackAllocation() {
        if (transaction_origin < 0) {
            return;
        }

        free_offset = transaction_origin;
        transaction_origin = -1;
    }

    @Deprecated
    @Override
    public void endAllocation() {
        transaction_origin = -1;
    }

    private long allocate(long size) {
        if (size <= 0) {
            rollbackAllocation();
            throw new IllegalArgumentException();
        }

        if ((free_offset + size) > UNIT_SIZE) {
            rollbackAllocation();
            throw new OutOfMemoryError();
        }

        long rv = block + free_offset;
        free_offset += Util.alignUp(size, ALLOC_ALIGNMENT);

        if ((rv % ALLOC_ALIGNMENT) != 0) {
            throw new RuntimeException("Invalid alignment: 0x" + Long.toHexString(rv));
        }

        return rv;
    }

    private BoundedMemoryRegion allocateRegion(long size) {
        return new BoundedMemoryRegion(allocate(size), size, this);
    }

    @Override
    public <T> Pointer<T> allocateArray(LayoutType<T> type, long count) {
        // FIXME: when allocating structs align size up to 8 bytes to allow for raw reads/writes?
        long size = Util.sizeof(type.getCarrierType());
        if (size < 0) {
            throw new UnsupportedOperationException("Unknown size for type " + type);
        }

        size *= count;
        if (size > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("allocate size to large");
        }

        return new BoundedPointer<>(type, allocateRegion(size));
    }

    @Override
    public <T> Pointer<T> allocate(LayoutType<T> type) {
        return allocateArray(type, 1);
    }

    @Override
    public <T extends Reference<T>> T allocateStruct(LayoutType<T> type) {
        long size = Util.alignUp(Util.sizeof(type.getCarrierType()), 8);
        BoundedPointer<T> p = new BoundedPointer<>(type, allocateRegion(size), 0);
        return p.deref();
    }

    @Override
    public void free(Resource resource) {
        // FIXME: Implement me
        throw new UnsupportedOperationException("NIY");
    }

    @Override
    public void handoff(Resource resource) {
        // FIXME: Implement me
        throw new UnsupportedOperationException("NIY");
    }

    @Override
    public void close() {
        isAlive = false;
        U.freeMemory(block);
    }
}
