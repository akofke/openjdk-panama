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
package jdk.internal.nicl;

import jdk.internal.misc.Unsafe;
import jdk.internal.nicl.types.BoundedMemoryRegion;
import jdk.internal.nicl.types.BoundedPointer;
import jdk.internal.nicl.types.DescriptorParser;
import jdk.internal.nicl.types.Function;
import jdk.internal.nicl.types.LayoutTypeImpl;
import jdk.internal.nicl.types.Types;
import jdk.internal.nicl.types.UncheckedPointer;
import jdk.internal.org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nicl.LibrarySymbol;
import java.nicl.NativeLibrary;
import java.nicl.Scope;
import java.nicl.metadata.C;
import java.nicl.metadata.CallingConvention;
import java.nicl.metadata.NativeType;
import java.nicl.types.*;
import java.util.Objects;
import java.util.stream.Stream;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

public final class Util {
    private Util() {
    }

    public static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & ~(alignment - 1);
    }

    public static boolean isCType(Class<?> clz) {
        return clz.isAnnotationPresent(C.class) &&
               clz.isAnnotationPresent(NativeType.class);
    }

    private static boolean isCMethod(Method m) {
        return m.isAnnotationPresent(C.class) &&
               m.isAnnotationPresent(NativeType.class);
    }

    public static boolean isCStruct(Class<?> clz) {
        if (!isCType(clz)) {
            return false;
        }
        NativeType nt = clz.getAnnotation(NativeType.class);
        return nt.isRecordType();
    }

    public static boolean isFunction(Class<?> clz) {
        if (!isCType(clz)) {
            return false;
        }

        return clz.isAnnotationPresent(CallingConvention.class);
    }

    private static jdk.internal.nicl.types.Type typeof2(java.lang.reflect.Type t) {
        if (t instanceof Class) {
            Class<?> c = (Class<?>) t;
            if (c.isPrimitive()) {
                switch (Type.getDescriptor(c)) {
                    case "V":
                        return Types.VOID;
                    case "Z":
                        return Types.BOOLEAN;
                    case "B":
                        return Types.INT8;
                    case "C":
                        return Types.UNSIGNED.INT16;
                    case "S":
                        return Types.INT16;
                    case "I":
                        return Types.INT32;
                    case "J":
                        return Types.INT64;
                    case "F":
                        return Types.FLOAT;
                    case "D":
                        return Types.DOUBLE;
                    default:
                        throw new IllegalArgumentException("Unhandled type: " + t);
                }
            } else if (Pointer.class.isAssignableFrom(c)) {
                return Types.POINTER;
            } else if (isFunctionalInterface(c)) {
                return Types.POINTER;
            } else if (isCType(c)) {
                return typeof(c);
            } else {
                throw new IllegalArgumentException("Unhandled type: " + t);
            }
        } else {
            ParameterizedType pt = (ParameterizedType)t;
            Class<?> rawType = (Class<?>)pt.getRawType();

            if (Pointer.class.isAssignableFrom(rawType)) {
                jdk.internal.nicl.types.Type pointeeType = typeof2(pt.getActualTypeArguments()[0]);
                return new jdk.internal.nicl.types.Pointer(pointeeType);
            } else {
                throw new IllegalArgumentException("Unhandled type: " + t);
            }
        }
    }

    public static jdk.internal.nicl.types.Function typeof(MethodType methodType) {
        return new Function(
                Stream.of(methodType.parameterArray()).map(Util::typeof2).toArray(jdk.internal.nicl.types.Type[]::new),
                typeof2(methodType.returnType()), false);
    }

    private static jdk.internal.nicl.types.Type typeof(NativeType nt) {
        return new DescriptorParser(nt.layout()).parseLayout().findFirst().get();
    }

    private static jdk.internal.nicl.types.Type typeofFunctionalInterfaceMethod(Class<?> clz) {
        Method m = findFunctionalInterfaceMethod(clz);
        if (m == null) {
            throw new IllegalArgumentException("Failed to look up FunctionalInterface method for class " + clz);
        }

        if (!m.isAnnotationPresent(NativeType.class)) {
            throw new IllegalArgumentException("FunctionalInterface method for class " + clz + " has no NativeType annotation");
        }

        return typeof(m.getAnnotation(NativeType.class));
    }

    public static jdk.internal.nicl.types.Type typeof(Class<?> clz) {
        if (!isCType(clz)) {
            if (clz.isAnnotationPresent(C.class) && clz.isAnnotationPresent(FunctionalInterface.class)) {
                return typeofFunctionalInterfaceMethod(clz);
            }
            throw new IllegalArgumentException();
        }
        return typeof(clz.getAnnotation(NativeType.class));
    }

    public static jdk.internal.nicl.types.Type typeof(Method m) {
        return typeof(m.getAnnotation(NativeType.class));
    }

    private static long sizeofPrimitive(Class<?> cls) {
        switch (Type.getDescriptor(cls)) {
        case "B":
        case "Z":
            return 1;

        case "S":
        case "C":
            return 2;

        case "I":
        case "F":
            return 4;

        case "J":
        case "D":
            return 8;

        default:
            throw new IllegalArgumentException("Unhandled type: " + cls);
        }
    }

    public static long sizeof(Class<?> clz) {
        // FIXME: treat java primitives as corresponding native types
        if (clz.isPrimitive()) {
            return sizeofPrimitive(clz);
        } else if (Pointer.class.isAssignableFrom(clz)) {
            return Unsafe.ADDRESS_SIZE;
        }

        if (! isCType(clz)) {
            throw new IllegalArgumentException(clz + " is not a valid type");
        }

        NativeType nt = clz.getAnnotation(NativeType.class);
        return nt.size();
    }

    static MethodType methodTypeFor(Method method) {
        return MethodType.methodType(method.getReturnType(), method.getParameterTypes());
    }

    /**
     * Return the number of (JVM) stack slots consumed by a type
     *
     * @param c the type to query
     * @return the number of stack slots consumed by the type
     */
    static int getSlotsForType(Class<?> c) {
        if (c == long.class || c == double.class) {
            return 2;
        }
        return 1;
    }

    static int returnInsn(Class<?> cls) {
        if (cls.isPrimitive()) {
            switch (PrimitiveClassType.typeof(cls)) {
                case BOOLEAN: // fallthrough
                case BYTE: // fallthrough
                case SHORT: // fallthrough
                case CHAR: // fallthrough
                case INT:
                    return IRETURN;
                case LONG:
                    return LRETURN;
                case FLOAT:
                    return FRETURN;
                case DOUBLE:
                    return DRETURN;
                case VOID:
                    return RETURN;
                default:
                    throw new Error("Unsupported: " + cls.getName());
            }
        } else {
            return ARETURN;
        }
    }

    static int loadInsn(Class<?> cls) {
        if (cls.isPrimitive()) {
            switch (PrimitiveClassType.typeof(cls)) {
                case BOOLEAN: // fallthrough
                case BYTE: // fallthrough
                case SHORT: // fallthrough
                case CHAR: // fallthrough
                case INT:
                    return ILOAD;
                case LONG:
                    return LLOAD;
                case FLOAT:
                    return FLOAD;
                case DOUBLE:
                    return DLOAD;
                default:
                    throw new Error("Unsupported: " + cls.getName());
            }
        } else {
            return ALOAD;
        }
    }

    public static boolean isFunctionalInterface(Class<?> c) {
        return c.isAnnotationPresent(FunctionalInterface.class);
    }

    public static Method findFunctionalInterfaceMethod(Class<?> c) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals("fn")) {
                return m;
            }
        }

        return null;
    }

    public static LayoutType<?> createLayoutType(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)type;

            if (pt.getRawType() != Pointer.class) {
                throw new IllegalArgumentException("Unexpected parameterized type: " + type);
            }

            return createLayoutType(pt.getActualTypeArguments()[0]).ptrType();
        } else {
            return LayoutTypeImpl.create((Class<?>)type);
        }
    }

    static MethodHandle findNative(String sym, MethodType mt) throws NoSuchMethodException, IllegalAccessException {
        SymbolLookup lookup = new SymbolLookup(name -> {
                if (!name.equals(sym)) {
                    throw new AssertionError();
                } else {
                    return new LibrarySymbol(name,
                            new UncheckedPointer<>(LayoutTypeImpl.create(void.class),
                            NativeInvoker.findNativeAddress(name)));
                }
            });
        return new NativeInvoker(mt, false, lookup, sym).getBoundMethodHandle();
    }


    public static final LayoutType<Byte> BYTE_TYPE = NativeLibrary.createLayout(byte.class);
    public static final LayoutType<Pointer<Byte>> BYTE_PTR_TYPE = BYTE_TYPE.ptrType();

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static long unpack(Pointer<?> ptr) throws IllegalAccessException {
        return ptr == null? 0 : ptr.addr();
    }

    public static long strlen(long addr) {
        long i = 0;

        while (UNSAFE.getByte(addr + i) != 0) {
            i++;
        }

        return i;
    }

    public static <T> Pointer<T> createPtr(long addr, LayoutType<T> type) {
        return createPtr(null, addr, type);
    }

    public static <T> Pointer<T> createPtr(Object base, long addr, LayoutType<T> type) {
        // FIXME: Long.MAX_VALUE is not correct
        return new BoundedPointer<>(type, new BoundedMemoryRegion(base, addr, Long.MAX_VALUE), 0);
    }

    // Helper methods useful for playing with pointers into the Java heap and data copying

    public static MemoryRegion createRegionForArrayElements(long[] arr) {
        return new BoundedMemoryRegion(arr, UNSAFE.arrayBaseOffset(long[].class), arr.length * 8, MemoryRegion.MODE_RW);
    }

    public static MemoryRegion createRegionForArrayElements(byte[] arr) {
        return new BoundedMemoryRegion(arr, UNSAFE.arrayBaseOffset(byte[].class), arr.length, MemoryRegion.MODE_RW);
    }

    public static MemoryRegion createRegionForArrayElements(long[] arr, Scope scope) {
        return new BoundedMemoryRegion(arr, UNSAFE.arrayBaseOffset(long[].class), arr.length * 8, MemoryRegion.MODE_RW, scope);
    }

    public static Pointer<Long> createArrayElementsPointer(long[] arr) {
        return new BoundedPointer<>(NativeLibrary.createLayout(long.class), createRegionForArrayElements(arr), 0);
    }

    public static Pointer<Byte> createArrayElementsPointer(byte[] arr) {
        return new BoundedPointer<>(NativeLibrary.createLayout(byte.class), createRegionForArrayElements(arr), 0);
    }

    public static Pointer<Long> createArrayElementsPointer(long[] arr, Scope scope) {
        return new BoundedPointer<>(NativeLibrary.createLayout(long.class), createRegionForArrayElements(arr, scope), 0);
    }

    public static void copy(Pointer<?> src, Pointer<?> dst, long bytes) throws IllegalAccessException {
        BoundedPointer<?> bsrc = (BoundedPointer<?>)Objects.requireNonNull(src);
        BoundedPointer<?> bdst = (BoundedPointer<?>)Objects.requireNonNull(dst);

        bsrc.copyTo(bdst, bytes);
    }
}
