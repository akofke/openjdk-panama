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

import jdk.internal.nicl.Util;
import java.nicl.types.*;

// These static utility methods are invoked from generated code
public final class RuntimeSupport {
    private RuntimeSupport() {}

    /*** FIXME: Temporary exports ***/
    @Deprecated
    public static <T> Reference<T> buildRef(Pointer<?> p, long offset, LayoutType<T> type) {
        return buildPtr(p, offset, type).lvalue();
    }

    private static <T> Pointer<T> buildPtr(Pointer<?> p, long offset, LayoutType<T> type) {
        return p
                .cast(NativeLibrary.createLayout(byte.class))
                .offset(offset)
                .cast(type);
    }

    @Deprecated
    public static void copyFromArray(int[] src, Pointer<?> p, long offset, int nElems) {
        Pointer<Integer> dst = buildPtr(p, offset, NativeLibrary.createLayout(int.class));
        for (int i = 0; i < nElems; i++) {
            dst.offset(i).lvalue().set(src[i]);
        }
    }

    @Deprecated
    public static void copyToArray(Pointer<?> p, long offset, int[] dst, int nElems) {
        Pointer<Integer> src = buildPtr(p, offset, NativeLibrary.createLayout(int.class));
        for (int i = 0; i < nElems; i++) {
            dst[i] = src.offset(i).lvalue().get();
        }
    }

    @Deprecated
    public static <T> void copyFromArray(T[] src, Pointer<?> p, long offset, int nElems, Class<T> elementType) {
        Pointer<T> dst = buildPtr(p, offset, NativeLibrary.createLayout(elementType));
        for (int i = 0; i < nElems; i++) {
            dst.offset(i).lvalue().set(src[i]);
        }
    }

    @Deprecated
    public static <T> void copyToArray(Pointer<?> p, long offset, T[] dst, int nElems, Class<T> elementType) {
        Pointer<T> src = buildPtr(p, offset, NativeLibrary.createLayout(elementType));
        for (int i = 0; i < nElems; i++) {
            dst[i] = src.offset(i).lvalue().get();
        }
    }

    // used by sample code
    public static void copy(Pointer<?> src, Pointer<?> dst, long bytes) throws IllegalAccessException {
        Util.copy(src, dst, bytes);
    }
}
