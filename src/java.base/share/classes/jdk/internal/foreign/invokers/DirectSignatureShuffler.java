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
 */

package jdk.internal.foreign.invokers;

import jdk.internal.foreign.Util;
import jdk.internal.foreign.abi.ArgumentBinding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.StorageClass;
import jdk.internal.foreign.memory.BoundedPointer;
import jdk.internal.foreign.memory.CallbackImpl;
import jdk.internal.foreign.memory.LayoutTypeImpl;

import java.foreign.layout.Function;
import java.foreign.memory.Callback;
import java.foreign.memory.LayoutType;
import java.foreign.memory.Pointer;
import java.foreign.memory.Struct;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * This class implements the shuffling logic that is required to adapt a method handle modelling a Java method into the
 * corresponding 'direct' native adapter (and viceversa, for upcalls). The shuffling is generally composed by two steps:
 * first we have to adapt incoming Java arguments into native values (or viceversa, in case of upcalls). Once that's done
 * a final permutation step is needed in order to push all the long arguments in front.
 */
class DirectSignatureShuffler {

    private static final MethodHandle LONG_TO_BOOLEAN;
    private static final MethodHandle BOOLEAN_TO_LONG;
    private static final MethodHandle LONG_TO_POINTER;
    private static final MethodHandle CALLBACK_TO_LONG;
    private static final MethodHandle LONG_TO_CALLBACK;
    private static final MethodHandle POINTER_TO_LONG;
    private static final MethodHandle STRUCT_TO_LONG;
    private static final MethodHandle STRUCT_TO_DOUBLE;
    private static final MethodHandle LONG_TO_STRUCT;
    private static final MethodHandle DOUBLE_TO_STRUCT;
    
    private static final int RET_POS = -1;

    static {
        try {
            LONG_TO_BOOLEAN = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "longToBoolean", MethodType.methodType(boolean.class, long.class));
            BOOLEAN_TO_LONG = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "booleanToLong", MethodType.methodType(long.class, boolean.class));
            LONG_TO_POINTER = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "longToPointer", MethodType.methodType(Pointer.class, LayoutType.class, long.class));
            POINTER_TO_LONG = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "pointerToLong", MethodType.methodType(long.class, Pointer.class));
            LONG_TO_CALLBACK = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "longToCallback", MethodType.methodType(Callback.class, Class.class, long.class));
            CALLBACK_TO_LONG = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "callbackToLong", MethodType.methodType(long.class, Callback.class));
            STRUCT_TO_LONG = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "structToLong", MethodType.methodType(long.class, ArgumentBinding.class, Struct.class));
            STRUCT_TO_DOUBLE = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "structToDouble", MethodType.methodType(double.class, ArgumentBinding.class, Struct.class));
            LONG_TO_STRUCT = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "longToStruct", MethodType.methodType(Struct.class, Class.class, long.class));
            DOUBLE_TO_STRUCT = MethodHandles.lookup().findStatic(DirectSignatureShuffler.class, "doubleToStruct", MethodType.methodType(Struct.class, Class.class, double.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final ShuffleDirection direction;
    private final MethodType javaMethodType;
    private MethodType nativeMethodType = MethodType.methodType(void.class);
    private List<UnaryOperator<MethodHandle>> adapters = new ArrayList<>();
    private List<Integer> longPerms = new ArrayList<>();
    private List<Integer> doublePerms = new ArrayList<>();

    private DirectSignatureShuffler(CallingSequence callingSequence, MethodType javaMethodType,
                                    IntFunction<LayoutType<?>> layoutTypeFactory, ShuffleDirection direction) {
        checkCallingSequence(callingSequence);
        this.direction = direction;
        this.javaMethodType = javaMethodType;
        processType(RET_POS, layoutTypeFactory.apply(RET_POS), callingSequence.getReturnBindings(), direction.flip());
        for (int i = 0 ; i < javaMethodType.parameterCount() ; i++) {
            processType(i, layoutTypeFactory.apply(i), callingSequence.getArgumentBindings(i), direction);
        }
    }

    static DirectSignatureShuffler javaToNativeShuffler(CallingSequence callingSequence, MethodType javaMethodType,
                                                        IntFunction<LayoutType<?>> layoutTypeFactory) {
        return new DirectSignatureShuffler(callingSequence, javaMethodType, layoutTypeFactory, ShuffleDirection.JAVA_TO_NATIVE);
    }

    static DirectSignatureShuffler nativeToJavaShuffler(CallingSequence callingSequence, MethodType javaMethodType,
                                                        IntFunction<LayoutType<?>> layoutTypeFactory) {
        return new DirectSignatureShuffler(callingSequence, javaMethodType, layoutTypeFactory, ShuffleDirection.NATIVE_TO_JAVA);
    }

    MethodHandle adapt(MethodHandle mh) {
        for (UnaryOperator<MethodHandle> adapter : adapters) {
            mh = adapter.apply(mh);
        }
        return MethodHandles.permuteArguments(mh,
                (direction == ShuffleDirection.JAVA_TO_NATIVE) ? javaMethodType : nativeMethodType,
                permutations().stream().mapToInt(x -> x).toArray());
    }

    MethodType nativeMethodType() {
        return nativeMethodType;
    }
    
    MethodType javaMethodType() {
        return javaMethodType;
    }

    String nativeSigSuffix() {
        MethodType mt = nativeMethodType();
        return String.format("%s_%s",
                desc(mt.returnType()),
                mt.parameterCount() > 0 ?
                        mt.parameterList().stream().map(this::desc).collect(Collectors.joining()) :
                        "V");

    }

    private static void checkCallingSequence(CallingSequence callingSequence) {
        if (callingSequence.returnsInMemory() ||
                !callingSequence.getBindings(StorageClass.STACK_ARGUMENT_SLOT).isEmpty()) {
            throw new IllegalArgumentException("Unsupported non-scalarized calling sequence!");
        }
    }

    private void processType(int sigPos, LayoutType<?> lt, List<ArgumentBinding> bindings, ShuffleDirection direction) {
        Class<?> carrier = (Class<?>) Util.unboxIfNeeded(((LayoutTypeImpl<?>)lt).carrier());
        if (carrier.isPrimitive()) {
            if (carrier == long.class) {
                updateNativeMethodType(sigPos, long.class);
            } else if (carrier == double.class) {
                updateNativeMethodType(sigPos, double.class);
            } else if (carrier == float.class) {
                updateNativeMethodType(sigPos, double.class);
                adapters.add(direction.doubleAdapter(sigPos, carrier));
            } else if (carrier == boolean.class) {
                updateNativeMethodType(sigPos, long.class);
                adapters.add(direction.booleanAdapter(sigPos));
            } else if (carrier == void.class) {
                assert sigPos == -1;
            } else {
                updateNativeMethodType(sigPos, long.class);
                adapters.add(direction.longAdapter(sigPos, carrier));
            }
        } else if (carrier == Pointer.class) {
            updateNativeMethodType(sigPos, long.class);
            adapters.add(direction.pointerAdapter(sigPos, lt));
        } else if (carrier == Callback.class) {
            updateNativeMethodType(sigPos, long.class);
            adapters.add(direction.callbackAdapter(sigPos, lt));
        } else if (Util.isCStruct(carrier)) {
            if (bindings.size() == 1) {
                ArgumentBinding binding = bindings.get(0);
                switch (binding.getStorage().getStorageClass()) {
                    case INTEGER_ARGUMENT_REGISTER:
                    case INTEGER_RETURN_REGISTER:
                        updateNativeMethodType(sigPos, long.class);
                        adapters.add(direction.longStructAdapter(sigPos, binding, carrier));
                        break;
                    case VECTOR_ARGUMENT_REGISTER:
                    case VECTOR_RETURN_REGISTER:
                        updateNativeMethodType(sigPos, double.class);
                        adapters.add(direction.doubleStructAdapter(sigPos, binding, carrier));
                        break;
                    default:
                        //non-register bindings should have already been discarded by now
                        throw new IllegalStateException("Cannot get here!");
                }
            } else {
                throw new IllegalArgumentException("Multi-value struct!");
            }
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + carrier);
        }
    }   

    private void updateNativeMethodType(int sigPos, Class<?> carrier) {
        if (sigPos == -1) {
            nativeMethodType = nativeMethodType.changeReturnType(carrier);
        } else {
            nativeMethodType = nativeMethodType.appendParameterTypes(carrier);
            if (carrier == long.class) {
                longPerms.add(sigPos);
            } else {
                doublePerms.add(sigPos);
            }
        }
    }

    private List<Integer> permutations() {
        List<Integer> perms = new ArrayList<>();
        perms.addAll(longPerms);
        perms.addAll(doublePerms);
        return perms;
    }

    private String desc(Class<?> clazz) {
        if (clazz == long.class) {
            return "J";
        } else if (clazz == double.class) {
            return "D";
        } else if (clazz == void.class) {
            return "V";
        } else {
            throw new IllegalStateException("Unexpected class: " + clazz);
        }
    }

    // adapter helpers

    enum ShuffleDirection {
        JAVA_TO_NATIVE(5),
        NATIVE_TO_JAVA(4);

        ShuffleDirection(int maxArity) {
            this.maxArity = maxArity;
        }

        int maxArity;

        ShuffleDirection flip() {
            return this == JAVA_TO_NATIVE ? NATIVE_TO_JAVA : JAVA_TO_NATIVE;
        }

        MethodHandle filterHandle(MethodHandle mh, int pos, MethodHandle filter) {
            return pos == RET_POS ?
                    MethodHandles.filterReturnValue(mh, filter) :
                    MethodHandles.filterArguments(mh, pos, filter);
        }

        UnaryOperator<MethodHandle> longAdapter(int pos, Class<?> carrier) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE) ? primitiveToLong(carrier) : longToPrimitive(carrier));
        }

        UnaryOperator<MethodHandle> doubleAdapter(int pos, Class<?> carrier) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE) ? primitiveToDouble(carrier) : doubleToPrimitive(carrier));
        }

        UnaryOperator<MethodHandle> booleanAdapter(int pos) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE) ? BOOLEAN_TO_LONG : LONG_TO_BOOLEAN);
        }

        UnaryOperator<MethodHandle> pointerAdapter(int pos, LayoutType<?> type) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE) ? POINTER_TO_LONG : LONG_TO_POINTER.bindTo(((LayoutTypeImpl<?>)type).pointeeType()));
        }

        UnaryOperator<MethodHandle> callbackAdapter(int pos, LayoutType<?> type) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE) ? CALLBACK_TO_LONG : LONG_TO_CALLBACK.bindTo(((LayoutTypeImpl<?>)type).getFuncIntf()));
        }

        UnaryOperator<MethodHandle> longStructAdapter(int pos, ArgumentBinding binding, Class<?> carrier) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE)  ?
                            STRUCT_TO_LONG.bindTo(binding).asType(MethodType.methodType(long.class, carrier)) :
                            LONG_TO_STRUCT.bindTo(carrier).asType(MethodType.methodType(carrier, long.class)));
        }

        UnaryOperator<MethodHandle> doubleStructAdapter(int pos, ArgumentBinding binding, Class<?> carrier) {
            return mh -> filterHandle(mh, pos,
                    (this == JAVA_TO_NATIVE)  ?
                            STRUCT_TO_DOUBLE.bindTo(binding).asType(MethodType.methodType(double.class, carrier)) :
                            DOUBLE_TO_STRUCT.bindTo(carrier).asType(MethodType.methodType(carrier, double.class)));
        }
    }

    private static boolean longToBoolean(long value) {
        return value != 0;
    }

    private static long booleanToLong(boolean value) {
        return value ? 1 : 0;
    }

    private static long pointerToLong(Pointer<?> value) throws IllegalAccessException {
        return value.addr();
    }

    private static Pointer<?> longToPointer(LayoutType<?> lt, long addr) {
        return addr == 0L ?
                BoundedPointer.nullPointer() :
                BoundedPointer.createNativeVoidPointer(addr).cast(lt);
    }

    private static long callbackToLong(Callback<?> value) throws IllegalAccessException {
        return value.entryPoint().addr();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Callback<?> longToCallback(Class<?> funcClass, long addr) {
        return new CallbackImpl(BoundedPointer.createNativeVoidPointer(addr),
                funcClass);
    }

    private static long structToLong(ArgumentBinding binding, Struct<?> struct) {
        return ((BoundedPointer<?>)struct.ptr()).region.getBits(binding.getOffset(), binding.getStorage().getSize());
    }

    private static double structToDouble(ArgumentBinding binding, Struct<?> struct) {
        return Double.longBitsToDouble(structToLong(binding, struct));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Struct<?> longToStruct(Class carrier, long value) {
        Pointer<? extends Struct<?>> ptr = BoundedPointer.fromLongArray(LayoutType.ofStruct(carrier), new long[] { value });
        return ptr.get();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Struct<?> doubleToStruct(Class carrier, double value) {
        return longToStruct(carrier, Double.doubleToLongBits(value));
    }

    private static MethodHandle primitiveToLong(Class<?> carrier) {
        MethodHandle mh = MethodHandles.identity(long.class);
        return MethodHandles.explicitCastArguments(mh, MethodType.methodType(long.class, carrier));
    }

    private static MethodHandle longToPrimitive(Class<?> carrier) {
        MethodHandle mh = MethodHandles.identity(long.class);
        return MethodHandles.explicitCastArguments(mh, MethodType.methodType(carrier, long.class));
    }

    private static MethodHandle primitiveToDouble(Class<?> carrier) {
        MethodHandle mh = MethodHandles.identity(double.class);
        return MethodHandles.explicitCastArguments(mh, MethodType.methodType(double.class, carrier));
    }

    private static MethodHandle doubleToPrimitive(Class<?> carrier) {
        MethodHandle mh = MethodHandles.identity(double.class);
        return MethodHandles.explicitCastArguments(mh, MethodType.methodType(carrier, double.class));
    }

    // predicate: is fast path applicable?

    static boolean acceptDowncall(Function function, CallingSequence callingSequence) {
        return accept(function, callingSequence, ShuffleDirection.JAVA_TO_NATIVE);
    }

    static boolean acceptUpcall(Function function, CallingSequence callingSequence) {
        return accept(function, callingSequence, ShuffleDirection.NATIVE_TO_JAVA);
    }

    private static boolean accept(Function function, CallingSequence callingSequence, ShuffleDirection direction) {
        int arity = function.argumentLayouts().size();
        if (arity > direction.maxArity) return false;
        for (int i = 0 ; i < function.argumentLayouts().size() ; i++) {
            List<ArgumentBinding> argumentBindings = callingSequence.getArgumentBindings(i);
            if (argumentBindings.size() != 1 ||
                    argumentBindings.get(0).getStorage().getStorageClass() == StorageClass.STACK_ARGUMENT_SLOT) {
                return false;
            }
        }
        return !callingSequence.returnsInMemory() &&
                callingSequence.getReturnBindings().size() <= 1;
    }
}
