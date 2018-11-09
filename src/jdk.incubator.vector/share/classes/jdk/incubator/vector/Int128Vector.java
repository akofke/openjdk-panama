/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * or visit www.oracle.com if you need additional information or have
 * questions.
 */
package jdk.incubator.vector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import static jdk.incubator.vector.VectorIntrinsics.*;

@SuppressWarnings("cast")
final class Int128Vector extends IntVector<Shapes.S128Bit> {
    static final Int128Species SPECIES = new Int128Species();

    static final Int128Vector ZERO = new Int128Vector();

    static final int LENGTH = SPECIES.length();

    private final int[] vec; // Don't access directly, use getElements() instead.

    private int[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    Int128Vector() {
        vec = new int[SPECIES.length()];
    }

    Int128Vector(int[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    Int128Vector uOp(FUnOp f) {
        int[] vec = getElements();
        int[] res = new int[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new Int128Vector(res);
    }

    @Override
    Int128Vector uOp(Mask<Integer, Shapes.S128Bit> o, FUnOp f) {
        int[] vec = getElements();
        int[] res = new int[length()];
        boolean[] mbits = ((Int128Mask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new Int128Vector(res);
    }

    // Binary operator

    @Override
    Int128Vector bOp(Vector<Integer, Shapes.S128Bit> o, FBinOp f) {
        int[] res = new int[length()];
        int[] vec1 = this.getElements();
        int[] vec2 = ((Int128Vector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Int128Vector(res);
    }

    @Override
    Int128Vector bOp(Vector<Integer, Shapes.S128Bit> o1, Mask<Integer, Shapes.S128Bit> o2, FBinOp f) {
        int[] res = new int[length()];
        int[] vec1 = this.getElements();
        int[] vec2 = ((Int128Vector)o1).getElements();
        boolean[] mbits = ((Int128Mask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new Int128Vector(res);
    }

    // Trinary operator

    @Override
    Int128Vector tOp(Vector<Integer, Shapes.S128Bit> o1, Vector<Integer, Shapes.S128Bit> o2, FTriOp f) {
        int[] res = new int[length()];
        int[] vec1 = this.getElements();
        int[] vec2 = ((Int128Vector)o1).getElements();
        int[] vec3 = ((Int128Vector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new Int128Vector(res);
    }

    @Override
    Int128Vector tOp(Vector<Integer, Shapes.S128Bit> o1, Vector<Integer, Shapes.S128Bit> o2, Mask<Integer, Shapes.S128Bit> o3, FTriOp f) {
        int[] res = new int[length()];
        int[] vec1 = getElements();
        int[] vec2 = ((Int128Vector)o1).getElements();
        int[] vec3 = ((Int128Vector)o2).getElements();
        boolean[] mbits = ((Int128Mask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new Int128Vector(res);
    }

    @Override
    int rOp(int v, FBinOp f) {
        int[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Binary operations with scalars

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> add(int o) {
        return add(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> add(int o, Mask<Integer,Shapes.S128Bit> m) {
        return add(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> sub(int o) {
        return sub(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> sub(int o, Mask<Integer,Shapes.S128Bit> m) {
        return sub(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> mul(int o) {
        return mul(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> mul(int o, Mask<Integer,Shapes.S128Bit> m) {
        return mul(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> min(int o) {
        return min(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> max(int o) {
        return max(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> equal(int o) {
        return equal(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> notEqual(int o) {
        return notEqual(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> lessThan(int o) {
        return lessThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> lessThanEq(int o) {
        return lessThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> greaterThan(int o) {
        return greaterThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Integer, Shapes.S128Bit> greaterThanEq(int o) {
        return greaterThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> blend(int o, Mask<Integer,Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast(o), m);
    }


    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> and(int o) {
        return and(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> and(int o, Mask<Integer,Shapes.S128Bit> m) {
        return and(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> or(int o) {
        return or(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> or(int o, Mask<Integer,Shapes.S128Bit> m) {
        return or(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> xor(int o) {
        return xor(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public IntVector<Shapes.S128Bit> xor(int o, Mask<Integer,Shapes.S128Bit> m) {
        return xor(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public Int128Vector neg() {
        return SPECIES.zero().sub(this);
    }

    // Unary operations

    @ForceInline
    @Override
    public Int128Vector neg(Mask<Integer, Shapes.S128Bit> m) {
        return blend(neg(), m);
    }

    @Override
    @ForceInline
    public Int128Vector abs() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_ABS, Int128Vector.class, int.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (int) Math.abs(a)));
    }

    @ForceInline
    @Override
    public Int128Vector abs(Mask<Integer, Shapes.S128Bit> m) {
        return blend(abs(), m);
    }


    @Override
    @ForceInline
    public Int128Vector not() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_NOT, Int128Vector.class, int.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (int) ~a));
    }

    @ForceInline
    @Override
    public Int128Vector not(Mask<Integer, Shapes.S128Bit> m) {
        return blend(not(), m);
    }
    // Binary operations

    @Override
    @ForceInline
    public Int128Vector add(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a + b)));
    }

    @Override
    @ForceInline
    public Int128Vector add(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(add(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector sub(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a - b)));
    }

    @Override
    @ForceInline
    public Int128Vector sub(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(sub(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector mul(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a * b)));
    }

    @Override
    @ForceInline
    public Int128Vector mul(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(mul(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector min(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return (Int128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MIN, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> ((Int128Vector)v1).bOp(v2, (i, a, b) -> (int) ((a < b) ? a : b)));
    }

    @Override
    @ForceInline
    public Int128Vector min(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(min(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector max(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MAX, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int) ((a > b) ? a : b)));
        }

    @Override
    @ForceInline
    public Int128Vector max(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(max(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector and(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_AND, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a & b)));
    }

    @Override
    @ForceInline
    public Int128Vector or(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_OR, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a | b)));
    }

    @Override
    @ForceInline
    public Int128Vector xor(Vector<Integer,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_XOR, Int128Vector.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (int)(a ^ b)));
    }

    @Override
    @ForceInline
    public Int128Vector and(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(and(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector or(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(or(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector xor(Vector<Integer,Shapes.S128Bit> v, Mask<Integer, Shapes.S128Bit> m) {
        return blend(xor(v), m);
    }

    @Override
    @ForceInline
    public Int128Vector shiftL(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_LSHIFT, Int128Vector.class, int.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (int) (a << i)));
    }

    @Override
    @ForceInline
    public Int128Vector shiftR(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_URSHIFT, Int128Vector.class, int.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (int) (a >>> i)));
    }

    @Override
    @ForceInline
    public Int128Vector aShiftR(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_RSHIFT, Int128Vector.class, int.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (int) (a >> i)));
    }

    @Override
    @ForceInline
    public Int128Vector shiftL(Vector<Integer,Shapes.S128Bit> s) {
        Int128Vector shiftv = (Int128Vector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x1f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_LSHIFT, Int128Vector.class, int.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (int) (a << b)));
    }

    @Override
    @ForceInline
    public Int128Vector shiftR(Vector<Integer,Shapes.S128Bit> s) {
        Int128Vector shiftv = (Int128Vector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x1f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_URSHIFT, Int128Vector.class, int.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (int) (a >>> b)));
    }

    @Override
    @ForceInline
    public Int128Vector aShiftR(Vector<Integer,Shapes.S128Bit> s) {
        Int128Vector shiftv = (Int128Vector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x1f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_RSHIFT, Int128Vector.class, int.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (int) (a >> b)));
    }
    // Ternary operations


    // Type specific horizontal reductions

    @Override
    @ForceInline
    public int addAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_ADD, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) 0, (i, a, b) -> (int) (a + b)));
    }

    @Override
    @ForceInline
    public int andAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_AND, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) -1, (i, a, b) -> (int) (a & b)));
    }

    @Override
    @ForceInline
    public int andAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) -1), m).andAll();
    }

    @Override
    @ForceInline
    public int minAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MIN, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp(Integer.MAX_VALUE , (i, a, b) -> (int) ((a < b) ? a : b)));
    }

    @Override
    @ForceInline
    public int maxAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MAX, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp(Integer.MIN_VALUE , (i, a, b) -> (int) ((a > b) ? a : b)));
    }

    @Override
    @ForceInline
    public int mulAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MUL, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) 1, (i, a, b) -> (int) (a * b)));
    }

    @Override
    @ForceInline
    public int subAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_SUB, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) 0, (i, a, b) -> (int) (a - b)));
    }

    @Override
    @ForceInline
    public int orAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_OR, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) 0, (i, a, b) -> (int) (a | b)));
    }

    @Override
    @ForceInline
    public int orAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) 0), m).orAll();
    }

    @Override
    @ForceInline
    public int xorAll() {
        return (int) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_XOR, Int128Vector.class, int.class, LENGTH,
            this,
            v -> (long) v.rOp((int) 0, (i, a, b) -> (int) (a ^ b)));
    }

    @Override
    @ForceInline
    public int xorAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) 0), m).xorAll();
    }


    @Override
    @ForceInline
    public int addAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) 0), m).addAll();
    }

    @Override
    @ForceInline
    public int subAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) 0), m).subAll();
    }

    @Override
    @ForceInline
    public int mulAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast((int) 1), m).mulAll();
    }

    @Override
    @ForceInline
    public int minAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast(Integer.MAX_VALUE), m).minAll();
    }

    @Override
    @ForceInline
    public int maxAll(Mask<Integer, Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast(Integer.MIN_VALUE), m).maxAll();
    }

    @Override
    @ForceInline
    public Shuffle<Integer, Shapes.S128Bit> toShuffle() {
        int[] a = toArray();
        int[] sa = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            sa[i] = (int) a[i];
        }
        return SPECIES.shuffleFromArray(sa, 0);
    }

    // Memory operations

    private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_INT_INDEX_SCALE);

    @Override
    @ForceInline
    public void intoArray(int[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
        VectorIntrinsics.store(Int128Vector.class, int.class, LENGTH,
                               a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_INT_BASE_OFFSET,
                               this,
                               a, ix,
                               (arr, idx, v) -> v.forEach((i, e) -> arr[idx + i] = e));
    }

    @Override
    @ForceInline
    public final void intoArray(int[] a, int ax, Mask<Integer, Shapes.S128Bit> m) {
        Int128Vector oldVal = SPECIES.fromArray(a, ax);
        Int128Vector newVal = oldVal.blend(this, m);
        newVal.intoArray(a, ax);
    }

    @Override
    @ForceInline
    public void intoByteArray(byte[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
        VectorIntrinsics.store(Int128Vector.class, int.class, LENGTH,
                               a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                               this,
                               a, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = ByteBuffer.wrap(c, idx, c.length - idx).order(ByteOrder.nativeOrder());
                                   IntBuffer tb = bbc.asIntBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public final void intoByteArray(byte[] a, int ix, Mask<Integer, Shapes.S128Bit> m) {
        Int128Vector oldVal = SPECIES.fromByteArray(a, ix);
        Int128Vector newVal = oldVal.blend(this, m);
        newVal.intoByteArray(a, ix);
    }

    @Override
    @ForceInline
    public void intoByteBuffer(ByteBuffer bb, int ix) {
        if (bb.order() != ByteOrder.nativeOrder()) {
            throw new IllegalArgumentException();
        }
        if (bb.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        ix = VectorIntrinsics.checkIndex(ix, bb.limit(), bitSize() / Byte.SIZE);
        VectorIntrinsics.store(Int128Vector.class, int.class, LENGTH,
                               U.getReference(bb, BYTE_BUFFER_HB), ix + U.getLong(bb, BUFFER_ADDRESS),
                               this,
                               bb, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                   IntBuffer tb = bbc.asIntBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public void intoByteBuffer(ByteBuffer bb, int ix, Mask<Integer, Shapes.S128Bit> m) {
        Int128Vector oldVal = SPECIES.fromByteBuffer(bb, ix);
        Int128Vector newVal = oldVal.blend(this, m);
        newVal.intoByteBuffer(bb, ix);
    }

    //

    @Override
    public String toString() {
        return Arrays.toString(getElements());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        Int128Vector that = (Int128Vector) o;
        return this.equal(that).allTrue();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    Int128Mask bTest(Vector<Integer, Shapes.S128Bit> o, FBinTest f) {
        int[] vec1 = getElements();
        int[] vec2 = ((Int128Vector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Int128Mask(bits);
    }

    // Comparisons

    @Override
    @ForceInline
    public Int128Mask equal(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return VectorIntrinsics.compare(
            BT_eq, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a == b));
    }

    @Override
    @ForceInline
    public Int128Mask notEqual(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return VectorIntrinsics.compare(
            BT_ne, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a != b));
    }

    @Override
    @ForceInline
    public Int128Mask lessThan(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return VectorIntrinsics.compare(
            BT_lt, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a < b));
    }

    @Override
    @ForceInline
    public Int128Mask lessThanEq(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return VectorIntrinsics.compare(
            BT_le, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a <= b));
    }

    @Override
    @ForceInline
    public Int128Mask greaterThan(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return (Int128Mask) VectorIntrinsics.compare(
            BT_gt, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a > b));
    }

    @Override
    @ForceInline
    public Int128Mask greaterThanEq(Vector<Integer, Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Int128Vector v = (Int128Vector)o;

        return VectorIntrinsics.compare(
            BT_ge, Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a >= b));
    }

    // Foreach

    @Override
    void forEach(FUnCon f) {
        int[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Integer, Shapes.S128Bit> o, FUnCon f) {
        boolean[] mbits = ((Int128Mask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }


    Float128Vector toFP() {
        int[] vec = getElements();
        float[] res = new float[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Float.intBitsToFloat(vec[i]);
        }
        return new Float128Vector(res);
    }

    @Override
    public Int128Vector rotateEL(int j) {
        int[] vec = getElements();
        int[] res = new int[length()];
        for (int i = 0; i < length(); i++){
            res[(j + i) % length()] = vec[i];
        }
        return new Int128Vector(res);
    }

    @Override
    public Int128Vector rotateER(int j) {
        int[] vec = getElements();
        int[] res = new int[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new Int128Vector(res);
    }

    @Override
    public Int128Vector shiftEL(int j) {
        int[] vec = getElements();
        int[] res = new int[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new Int128Vector(res);
    }

    @Override
    public Int128Vector shiftER(int j) {
        int[] vec = getElements();
        int[] res = new int[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new Int128Vector(res);
    }

    @Override
    @ForceInline
    public Int128Vector rearrange(Vector<Integer, Shapes.S128Bit> v,
                                  Shuffle<Integer, Shapes.S128Bit> s, Mask<Integer, Shapes.S128Bit> m) {
        return this.rearrange(s).blend(v.rearrange(s), m);
    }

    @Override
    @ForceInline
    public Int128Vector rearrange(Shuffle<Integer, Shapes.S128Bit> o1) {
    Objects.requireNonNull(o1);
    Int128Shuffle s =  (Int128Shuffle)o1;

        return VectorIntrinsics.rearrangeOp(
            Int128Vector.class, Int128Shuffle.class, int.class, LENGTH,
            this, s,
            (v1, s_) -> v1.uOp((i, a) -> {
            int[] vec = this.getElements();
            int ei = s_.getElement(i);
            return vec[ei];
        }));
    }

    @Override
    @ForceInline
    public Int128Vector blend(Vector<Integer, Shapes.S128Bit> o1, Mask<Integer, Shapes.S128Bit> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        Int128Vector v = (Int128Vector)o1;
        Int128Mask   m = (Int128Mask)o2;

        return VectorIntrinsics.blend(
            Int128Vector.class, Int128Mask.class, int.class, LENGTH,
            this, v, m,
            (v1, v2, m_) -> v1.bOp(v2, (i, a, b) -> m_.getElement(i) ? b : a));
    }

    // Accessors

    @Override
    public int get(int i) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        return (int) VectorIntrinsics.extract(
                                Int128Vector.class, int.class, LENGTH,
                                this, i,
                                (vec, ix) -> {
                                    int[] vecarr = vec.getElements();
                                    return (long)vecarr[ix];
                                });
    }

    @Override
    public Int128Vector with(int i, int e) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        return VectorIntrinsics.insert(
                                Int128Vector.class, int.class, LENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    int[] res = v.getElements().clone();
                                    res[ix] = (int)bits;
                                    return new Int128Vector(res);
                                });
    }

    // Mask

    static final class Int128Mask extends AbstractMask<Integer, Shapes.S128Bit> {
        static final Int128Mask TRUE_MASK = new Int128Mask(true);
        static final Int128Mask FALSE_MASK = new Int128Mask(false);

        private final boolean[] bits; // Don't access directly, use getBits() instead.

        public Int128Mask(boolean[] bits) {
            this(bits, 0);
        }

        public Int128Mask(boolean[] bits, int offset) {
            boolean[] a = new boolean[species().length()];
            for (int i = 0; i < a.length; i++) {
                a[i] = bits[offset + i];
            }
            this.bits = a;
        }

        public Int128Mask(boolean val) {
            boolean[] bits = new boolean[species().length()];
            Arrays.fill(bits, val);
            this.bits = bits;
        }

        boolean[] getBits() {
            return VectorIntrinsics.maybeRebox(this).bits;
        }

        @Override
        Int128Mask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Int128Mask(res);
        }

        @Override
        Int128Mask bOp(Mask<Integer, Shapes.S128Bit> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Int128Mask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Int128Mask(res);
        }

        @Override
        public Int128Species species() {
            return SPECIES;
        }

        @Override
        public Int128Vector toVector() {
            int[] res = new int[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                // -1 will result in the most significant bit being set in
                // addition to some or all other bits
                res[i] = (int) (bits[i] ? -1 : 0);
            }
            return new Int128Vector(res);
        }

        // Unary operations

        @Override
        @ForceInline
        public Int128Mask not() {
            return (Int128Mask) VectorIntrinsics.unaryOp(
                                             VECTOR_OP_NOT, Int128Mask.class, int.class, LENGTH,
                                             this,
                                             (m1) -> m1.uOp((i, a) -> !a));
        }

        // Binary operations

        @Override
        @ForceInline
        public Int128Mask and(Mask<Integer,Shapes.S128Bit> o) {
            Objects.requireNonNull(o);
            Int128Mask m = (Int128Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, Int128Mask.class, int.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Int128Mask or(Mask<Integer,Shapes.S128Bit> o) {
            Objects.requireNonNull(o);
            Int128Mask m = (Int128Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, Int128Mask.class, int.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, Int128Mask.class, int.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, Int128Mask.class, int.class, LENGTH,
                                         this, species().maskAllTrue(),
                                         (m1, m2) -> super.allTrue());
        }
    }

    // Shuffle

    static final class Int128Shuffle extends AbstractShuffle<Integer, Shapes.S128Bit> {
        Int128Shuffle(byte[] reorder) {
            super(reorder);
        }

        public Int128Shuffle(int[] reorder) {
            super(reorder);
        }

        public Int128Shuffle(int[] reorder, int i) {
            super(reorder, i);
        }

        public Int128Shuffle(IntUnaryOperator f) {
            super(f);
        }

        @Override
        public Int128Species species() {
            return SPECIES;
        }

        @Override
        public Int128Vector toVector() {
            int[] va = new int[SPECIES.length()];
            for (int i = 0; i < va.length; i++) {
              va[i] = (int) getElement(i);
            }
            return species().fromArray(va, 0);
        }

        @Override
        public Int128Shuffle rearrange(Vector.Shuffle<Integer, Shapes.S128Bit> o) {
            Int128Shuffle s = (Int128Shuffle) o;
            byte[] r = new byte[reorder.length];
            for (int i = 0; i < reorder.length; i++) {
                r[i] = reorder[s.reorder[i]];
            }
            return new Int128Shuffle(r);
        }
    }

    // Species

    @Override
    public Int128Species species() {
        return SPECIES;
    }

    static final class Int128Species extends IntSpecies<Shapes.S128Bit> {
        static final int BIT_SIZE = Shapes.S_128_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Integer.SIZE;

        @Override
        public String toString() {
           StringBuilder sb = new StringBuilder("Shape[");
           sb.append(bitSize()).append(" bits, ");
           sb.append(length()).append(" ").append(int.class.getSimpleName()).append("s x ");
           sb.append(elementSize()).append(" bits");
           sb.append("]");
           return sb.toString();
        }

        @Override
        @ForceInline
        public int bitSize() {
            return BIT_SIZE;
        }

        @Override
        @ForceInline
        public int length() {
            return LENGTH;
        }

        @Override
        @ForceInline
        public Class<Integer> elementType() {
            return int.class;
        }

        @Override
        @ForceInline
        public int elementSize() {
            return Integer.SIZE;
        }

        @Override
        @ForceInline
        public Shapes.S128Bit shape() {
            return Shapes.S_128_BIT;
        }

        @Override
        Int128Vector op(FOp f) {
            int[] res = new int[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new Int128Vector(res);
        }

        @Override
        Int128Vector op(Mask<Integer, Shapes.S128Bit> o, FOp f) {
            int[] res = new int[length()];
            boolean[] mbits = ((Int128Mask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new Int128Vector(res);
        }

        // Factories

        @Override
        public Int128Mask maskFromValues(boolean... bits) {
            return new Int128Mask(bits);
        }

        @Override
        public Int128Mask maskFromArray(boolean[] bits, int i) {
            return new Int128Mask(bits, i);
        }

        @Override
        public Int128Shuffle shuffle(IntUnaryOperator f) {
            return new Int128Shuffle(f);
        }

        @Override
        public Int128Shuffle shuffleIota() {
            return new Int128Shuffle(AbstractShuffle.IDENTITY);
        }

        @Override
        public Int128Shuffle shuffleFromValues(int... ixs) {
            return new Int128Shuffle(ixs);
        }

        @Override
        public Int128Shuffle shuffleFromArray(int[] ixs, int i) {
            return new Int128Shuffle(ixs, i);
        }

        @Override
        @ForceInline
        public Int128Vector zero() {
            return VectorIntrinsics.broadcastCoerced(Int128Vector.class, int.class, LENGTH,
                                                     0,
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public Int128Vector broadcast(int e) {
            return VectorIntrinsics.broadcastCoerced(
                Int128Vector.class, int.class, LENGTH,
                e,
                ((long bits) -> SPECIES.op(i -> (int)bits)));
        }

        @Override
        @ForceInline
        public Int128Mask maskAllTrue() {
            return VectorIntrinsics.broadcastCoerced(Int128Mask.class, int.class, LENGTH,
                                                     (int)-1,
                                                     (z -> Int128Mask.TRUE_MASK));
        }

        @Override
        @ForceInline
        public Int128Mask maskAllFalse() {
            return VectorIntrinsics.broadcastCoerced(Int128Mask.class, int.class, LENGTH,
                                                     0,
                                                     (z -> Int128Mask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public Int128Vector scalars(int... es) {
            Objects.requireNonNull(es);
            int ix = VectorIntrinsics.checkIndex(0, es.length, LENGTH);
            return VectorIntrinsics.load(Int128Vector.class, int.class, LENGTH,
                                         es, Unsafe.ARRAY_INT_BASE_OFFSET,
                                         es, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public Int128Vector fromArray(int[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
            return VectorIntrinsics.load(Int128Vector.class, int.class, LENGTH,
                                         a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_INT_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public Int128Vector fromArray(int[] a, int ax, Mask<Integer, Shapes.S128Bit> m) {
            return zero().blend(fromArray(a, ax), m);
        }

        @Override
        @ForceInline
        public Int128Vector fromByteArray(byte[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(Int128Vector.class, int.class, LENGTH,
                                         a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = ByteBuffer.wrap(c, idx, a.length - idx).order(ByteOrder.nativeOrder());
                                             IntBuffer tb = bbc.asIntBuffer();
                                             return op(i -> tb.get());
                                         });
        }

        @Override
        @ForceInline
        public Int128Vector fromByteArray(byte[] a, int ix, Mask<Integer, Shapes.S128Bit> m) {
            return zero().blend(fromByteArray(a, ix), m);
        }

        @Override
        @ForceInline
        public Int128Vector fromByteBuffer(ByteBuffer bb, int ix) {
            if (bb.order() != ByteOrder.nativeOrder()) {
                throw new IllegalArgumentException();
            }
            ix = VectorIntrinsics.checkIndex(ix, bb.limit(), bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(Int128Vector.class, int.class, LENGTH,
                                         U.getReference(bb, BYTE_BUFFER_HB), U.getLong(bb, BUFFER_ADDRESS) + ix,
                                         bb, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                             IntBuffer tb = bbc.asIntBuffer();
                                             return op(i -> tb.get());
                                         });
        }

        @Override
        @ForceInline
        public Int128Vector fromByteBuffer(ByteBuffer bb, int ix, Mask<Integer, Shapes.S128Bit> m) {
            return zero().blend(fromByteBuffer(bb, ix), m);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <F, T extends Shape> Int128Vector cast(Vector<F, T> o) {
            if (o.length() != LENGTH)
                throw new IllegalArgumentException("Vector length this species length differ");

            return VectorIntrinsics.cast(
                o.getClass(),
                o.elementType(), LENGTH,
                Int128Vector.class,
                int.class, LENGTH,
                o, this,
                (s, v) -> s.castDefault(v)
            );
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        private <F, T extends Shape> Int128Vector castDefault(Vector<F, T> v) {
            // Allocate array of required size
            int limit = length();
            int[] a = new int[limit];

            Class<?> vtype = v.species().elementType();
            if (vtype == byte.class) {
                ByteVector<T> tv = (ByteVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == short.class) {
                ShortVector<T> tv = (ShortVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == int.class) {
                IntVector<T> tv = (IntVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == long.class){
                LongVector<T> tv = (LongVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == float.class){
                FloatVector<T> tv = (FloatVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == double.class){
                DoubleVector<T> tv = (DoubleVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else {
                throw new UnsupportedOperationException("Bad lane type for casting.");
            }

            return scalars(a);
        }

        @Override
        @ForceInline
        public <E, S extends Shape> Int128Mask cast(Mask<E, S> m) {
            if (m.length() != LENGTH)
                throw new IllegalArgumentException("Mask length this species length differ");
            return new Int128Mask(m.toArray());
        }

        @Override
        @ForceInline
        public <E, S extends Shape> Int128Shuffle cast(Shuffle<E, S> s) {
            if (s.length() != LENGTH)
                throw new IllegalArgumentException("Shuffle length this species length differ");
            return new Int128Shuffle(s.toArray());
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <F> Int128Vector rebracket(Vector<F, Shapes.S128Bit> o) {
            Objects.requireNonNull(o);
            if (o.elementType() == byte.class) {
                Byte128Vector so = (Byte128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Byte128Vector.class,
                    byte.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.elementType() == short.class) {
                Short128Vector so = (Short128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Short128Vector.class,
                    short.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.elementType() == int.class) {
                Int128Vector so = (Int128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Int128Vector.class,
                    int.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.elementType() == long.class) {
                Long128Vector so = (Long128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Long128Vector.class,
                    long.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.elementType() == float.class) {
                Float128Vector so = (Float128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Float128Vector.class,
                    float.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.elementType() == double.class) {
                Double128Vector so = (Double128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Double128Vector.class,
                    double.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else {
                throw new InternalError("Unimplemented type");
            }
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <T extends Shape> Int128Vector resize(Vector<Integer, T> o) {
            Objects.requireNonNull(o);
            if (o.bitSize() == 64) {
                Int64Vector so = (Int64Vector)o;
                return VectorIntrinsics.reinterpret(
                    Int64Vector.class,
                    int.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.bitSize() == 128) {
                Int128Vector so = (Int128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Int128Vector.class,
                    int.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.bitSize() == 256) {
                Int256Vector so = (Int256Vector)o;
                return VectorIntrinsics.reinterpret(
                    Int256Vector.class,
                    int.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else if (o.bitSize() == 512) {
                Int512Vector so = (Int512Vector)o;
                return VectorIntrinsics.reinterpret(
                    Int512Vector.class,
                    int.class, so.length(),
                    Int128Vector.class,
                    int.class, LENGTH,
                    so, this,
                    (s, v) -> (Int128Vector) s.reshape(v)
                );
            } else {
                throw new InternalError("Unimplemented size");
            }
        }
    }
}
