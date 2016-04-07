/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.invoke;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;

/**
 * The base class for generated byte array and byte buffer view
 * implementations
 */
abstract class VarHandleByteArrayBase {
    // Buffer.address
    static final long BUFFER_ADDRESS;
    // Buffer.limit
    static final long BUFFER_LIMIT;
    // ByteBuffer.hb
    static final long BYTE_BUFFER_HB;
    // ByteBuffer.isReadOnly
    static final long BYTE_BUFFER_IS_READ_ONLY;

    static {
        try {
            BUFFER_ADDRESS = UNSAFE.objectFieldOffset(
                    Buffer.class.getDeclaredField("address"));

            BUFFER_LIMIT = UNSAFE.objectFieldOffset(
                    Buffer.class.getDeclaredField("limit"));

            BYTE_BUFFER_HB = UNSAFE.objectFieldOffset(
                    ByteBuffer.class.getDeclaredField("hb"));

            BYTE_BUFFER_IS_READ_ONLY = UNSAFE.objectFieldOffset(
                    ByteBuffer.class.getDeclaredField("isReadOnly"));
        }
        catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    static final boolean BE = UNSAFE.isBigEndian();

    static IllegalStateException newIllegalStateExceptionForMisalignedAccess(int index) {
        return new IllegalStateException("Misaligned access at index: " + index);
    }
}
