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
import java.nio.LongBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import static jdk.incubator.vector.VectorIntrinsics.*;

@SuppressWarnings("cast")
final class LongMaxVector extends LongVector {
    static final LongMaxSpecies SPECIES = new LongMaxSpecies();

    static final LongMaxVector ZERO = new LongMaxVector();

    static final int LENGTH = SPECIES.length();

    // Index vector species
    private static final IntVector.IntSpecies INDEX_SPEC;
    static {
        int bitSize = Vector.bitSizeForVectorLength(int.class, LENGTH);
        Vector.Shape shape = Vector.shapeForVectorBitSize(bitSize);
        INDEX_SPEC = (IntVector.IntSpecies) Vector.species(int.class, shape);
    }

    private final long[] vec; // Don't access directly, use getElements() instead.

    private long[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    LongMaxVector() {
        vec = new long[SPECIES.length()];
    }

    LongMaxVector(long[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    LongMaxVector uOp(FUnOp f) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new LongMaxVector(res);
    }

    @Override
    LongMaxVector uOp(Mask<Long> o, FUnOp f) {
        long[] vec = getElements();
        long[] res = new long[length()];
        boolean[] mbits = ((LongMaxMask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new LongMaxVector(res);
    }

    // Binary operator

    @Override
    LongMaxVector bOp(Vector<Long> o, FBinOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((LongMaxVector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new LongMaxVector(res);
    }

    @Override
    LongMaxVector bOp(Vector<Long> o1, Mask<Long> o2, FBinOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((LongMaxVector)o1).getElements();
        boolean[] mbits = ((LongMaxMask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new LongMaxVector(res);
    }

    // Trinary operator

    @Override
    LongMaxVector tOp(Vector<Long> o1, Vector<Long> o2, FTriOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((LongMaxVector)o1).getElements();
        long[] vec3 = ((LongMaxVector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new LongMaxVector(res);
    }

    @Override
    LongMaxVector tOp(Vector<Long> o1, Vector<Long> o2, Mask<Long> o3, FTriOp f) {
        long[] res = new long[length()];
        long[] vec1 = getElements();
        long[] vec2 = ((LongMaxVector)o1).getElements();
        long[] vec3 = ((LongMaxVector)o2).getElements();
        boolean[] mbits = ((LongMaxMask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new LongMaxVector(res);
    }

    @Override
    long rOp(long v, FBinOp f) {
        long[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Binary operations with scalars

    @Override
    @ForceInline
    public LongVector add(long o) {
        return add(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector add(long o, Mask<Long> m) {
        return add(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector sub(long o) {
        return sub(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector sub(long o, Mask<Long> m) {
        return sub(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector mul(long o) {
        return mul(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector mul(long o, Mask<Long> m) {
        return mul(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector min(long o) {
        return min(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector max(long o) {
        return max(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> equal(long o) {
        return equal(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> notEqual(long o) {
        return notEqual(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> lessThan(long o) {
        return lessThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> lessThanEq(long o) {
        return lessThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> greaterThan(long o) {
        return greaterThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long> greaterThanEq(long o) {
        return greaterThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector blend(long o, Mask<Long> m) {
        return blend(SPECIES.broadcast(o), m);
    }


    @Override
    @ForceInline
    public LongVector and(long o) {
        return and(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector and(long o, Mask<Long> m) {
        return and(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector or(long o) {
        return or(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector or(long o, Mask<Long> m) {
        return or(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector xor(long o) {
        return xor(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector xor(long o, Mask<Long> m) {
        return xor(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongMaxVector neg() {
        return SPECIES.zero().sub(this);
    }

    // Unary operations

    @ForceInline
    @Override
    public LongMaxVector neg(Mask<Long> m) {
        return blend(neg(), m);
    }

    @Override
    @ForceInline
    public LongMaxVector abs() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_ABS, LongMaxVector.class, long.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (long) Math.abs(a)));
    }

    @ForceInline
    @Override
    public LongMaxVector abs(Mask<Long> m) {
        return blend(abs(), m);
    }


    @Override
    @ForceInline
    public LongMaxVector not() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_NOT, LongMaxVector.class, long.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (long) ~a));
    }

    @ForceInline
    @Override
    public LongMaxVector not(Mask<Long> m) {
        return blend(not(), m);
    }
    // Binary operations

    @Override
    @ForceInline
    public LongMaxVector add(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a + b)));
    }

    @Override
    @ForceInline
    public LongMaxVector add(Vector<Long> v, Mask<Long> m) {
        return blend(add(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector sub(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a - b)));
    }

    @Override
    @ForceInline
    public LongMaxVector sub(Vector<Long> v, Mask<Long> m) {
        return blend(sub(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector mul(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a * b)));
    }

    @Override
    @ForceInline
    public LongMaxVector mul(Vector<Long> v, Mask<Long> m) {
        return blend(mul(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector min(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return (LongMaxVector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MIN, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((LongMaxVector)v1).bOp(v2, (i, a, b) -> (long) ((a < b) ? a : b)));
    }

    @Override
    @ForceInline
    public LongMaxVector min(Vector<Long> v, Mask<Long> m) {
        return blend(min(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector max(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MAX, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long) ((a > b) ? a : b)));
        }

    @Override
    @ForceInline
    public LongMaxVector max(Vector<Long> v, Mask<Long> m) {
        return blend(max(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector and(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_AND, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a & b)));
    }

    @Override
    @ForceInline
    public LongMaxVector or(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_OR, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a | b)));
    }

    @Override
    @ForceInline
    public LongMaxVector xor(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_XOR, LongMaxVector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (long)(a ^ b)));
    }

    @Override
    @ForceInline
    public LongMaxVector and(Vector<Long> v, Mask<Long> m) {
        return blend(and(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector or(Vector<Long> v, Mask<Long> m) {
        return blend(or(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector xor(Vector<Long> v, Mask<Long> m) {
        return blend(xor(v), m);
    }

    @Override
    @ForceInline
    public LongMaxVector shiftL(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_LSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a << i)));
    }

    @Override
    @ForceInline
    public LongMaxVector shiftR(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_URSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a >>> i)));
    }

    @Override
    @ForceInline
    public LongMaxVector aShiftR(int s) {
        return VectorIntrinsics.broadcastInt(
            VECTOR_OP_RSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a >> i)));
    }

    @Override
    @ForceInline
    public LongMaxVector shiftL(Vector<Long> s) {
        LongMaxVector shiftv = (LongMaxVector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x3f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_LSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (long) (a << b)));
    }

    @Override
    @ForceInline
    public LongMaxVector shiftR(Vector<Long> s) {
        LongMaxVector shiftv = (LongMaxVector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x3f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_URSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (long) (a >>> b)));
    }

    @Override
    @ForceInline
    public LongMaxVector aShiftR(Vector<Long> s) {
        LongMaxVector shiftv = (LongMaxVector)s;
        // As per shift specification for Java, mask the shift count.
        shiftv = shiftv.and(species().broadcast(0x3f));
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_RSHIFT, LongMaxVector.class, long.class, LENGTH,
            this, shiftv,
            (v1, v2) -> v1.bOp(v2,(i,a, b) -> (long) (a >> b)));
    }
    // Ternary operations


    // Type specific horizontal reductions

    @Override
    @ForceInline
    public long addAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_ADD, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 0, (i, a, b) -> (long) (a + b)));
    }

    @Override
    @ForceInline
    public long andAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_AND, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) -1, (i, a, b) -> (long) (a & b)));
    }

    @Override
    @ForceInline
    public long andAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) -1), m).andAll();
    }

    @Override
    @ForceInline
    public long minAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MIN, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp(Long.MAX_VALUE , (i, a, b) -> (long) ((a < b) ? a : b)));
    }

    @Override
    @ForceInline
    public long maxAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MAX, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp(Long.MIN_VALUE , (i, a, b) -> (long) ((a > b) ? a : b)));
    }

    @Override
    @ForceInline
    public long mulAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MUL, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 1, (i, a, b) -> (long) (a * b)));
    }

    @Override
    @ForceInline
    public long subAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_SUB, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 0, (i, a, b) -> (long) (a - b)));
    }

    @Override
    @ForceInline
    public long orAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_OR, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 0, (i, a, b) -> (long) (a | b)));
    }

    @Override
    @ForceInline
    public long orAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) 0), m).orAll();
    }

    @Override
    @ForceInline
    public long xorAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_XOR, LongMaxVector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 0, (i, a, b) -> (long) (a ^ b)));
    }

    @Override
    @ForceInline
    public long xorAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) 0), m).xorAll();
    }


    @Override
    @ForceInline
    public long addAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) 0), m).addAll();
    }

    @Override
    @ForceInline
    public long subAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) 0), m).subAll();
    }

    @Override
    @ForceInline
    public long mulAll(Mask<Long> m) {
        return blend(SPECIES.broadcast((long) 1), m).mulAll();
    }

    @Override
    @ForceInline
    public long minAll(Mask<Long> m) {
        return blend(SPECIES.broadcast(Long.MAX_VALUE), m).minAll();
    }

    @Override
    @ForceInline
    public long maxAll(Mask<Long> m) {
        return blend(SPECIES.broadcast(Long.MIN_VALUE), m).maxAll();
    }

    @Override
    @ForceInline
    public Shuffle<Long> toShuffle() {
        long[] a = toArray();
        int[] sa = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            sa[i] = (int) a[i];
        }
        return SPECIES.shuffleFromArray(sa, 0);
    }

    // Memory operations

    private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_LONG_INDEX_SCALE);

    @Override
    @ForceInline
    public void intoArray(long[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
        VectorIntrinsics.store(LongMaxVector.class, long.class, LENGTH,
                               a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_LONG_BASE_OFFSET,
                               this,
                               a, ix,
                               (arr, idx, v) -> v.forEach((i, e) -> arr[idx + i] = e));
    }

    @Override
    @ForceInline
    public final void intoArray(long[] a, int ax, Mask<Long> m) {
        LongMaxVector oldVal = SPECIES.fromArray(a, ax);
        LongMaxVector newVal = oldVal.blend(this, m);
        newVal.intoArray(a, ax);
    }
    @Override
    @ForceInline
    public void intoArray(long[] a, int ix, int[] b, int iy) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        // Index vector: vix[0:n] = i -> ix + indexMap[iy + i]
        IntVector vix = INDEX_SPEC.fromArray(b, iy).add(ix);

        vix = VectorIntrinsics.checkIndex(vix, a.length);

        VectorIntrinsics.storeWithMap(LongMaxVector.class, long.class, LENGTH, vix.getClass(),
                               a, Unsafe.ARRAY_LONG_BASE_OFFSET, vix,
                               this,
                               a, ix, b, iy,
                               (arr, idx, v, indexMap, idy) -> v.forEach((i, e) -> arr[idx+indexMap[idy+i]] = e));
    }

     @Override
     @ForceInline
     public final void intoArray(long[] a, int ax, Mask<Long> m, int[] b, int iy) {
         // @@@ This can result in out of bounds errors for unset mask lanes
         LongMaxVector oldVal = SPECIES.fromArray(a, ax, b, iy);
         LongMaxVector newVal = oldVal.blend(this, m);
         newVal.intoArray(a, ax, b, iy);
     }

    @Override
    @ForceInline
    public void intoByteArray(byte[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
        VectorIntrinsics.store(LongMaxVector.class, long.class, LENGTH,
                               a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                               this,
                               a, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = ByteBuffer.wrap(c, idx, c.length - idx).order(ByteOrder.nativeOrder());
                                   LongBuffer tb = bbc.asLongBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public final void intoByteArray(byte[] a, int ix, Mask<Long> m) {
        LongMaxVector oldVal = SPECIES.fromByteArray(a, ix);
        LongMaxVector newVal = oldVal.blend(this, m);
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
        VectorIntrinsics.store(LongMaxVector.class, long.class, LENGTH,
                               U.getReference(bb, BYTE_BUFFER_HB), ix + U.getLong(bb, BUFFER_ADDRESS),
                               this,
                               bb, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                   LongBuffer tb = bbc.asLongBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public void intoByteBuffer(ByteBuffer bb, int ix, Mask<Long> m) {
        LongMaxVector oldVal = SPECIES.fromByteBuffer(bb, ix);
        LongMaxVector newVal = oldVal.blend(this, m);
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

        LongMaxVector that = (LongMaxVector) o;
        return this.equal(that).allTrue();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    LongMaxMask bTest(Vector<Long> o, FBinTest f) {
        long[] vec1 = getElements();
        long[] vec2 = ((LongMaxVector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new LongMaxMask(bits);
    }

    // Comparisons

    @Override
    @ForceInline
    public LongMaxMask equal(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return VectorIntrinsics.compare(
            BT_eq, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a == b));
    }

    @Override
    @ForceInline
    public LongMaxMask notEqual(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return VectorIntrinsics.compare(
            BT_ne, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a != b));
    }

    @Override
    @ForceInline
    public LongMaxMask lessThan(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return VectorIntrinsics.compare(
            BT_lt, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a < b));
    }

    @Override
    @ForceInline
    public LongMaxMask lessThanEq(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return VectorIntrinsics.compare(
            BT_le, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a <= b));
    }

    @Override
    @ForceInline
    public LongMaxMask greaterThan(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return (LongMaxMask) VectorIntrinsics.compare(
            BT_gt, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a > b));
    }

    @Override
    @ForceInline
    public LongMaxMask greaterThanEq(Vector<Long> o) {
        Objects.requireNonNull(o);
        LongMaxVector v = (LongMaxVector)o;

        return VectorIntrinsics.compare(
            BT_ge, LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a >= b));
    }

    // Foreach

    @Override
    void forEach(FUnCon f) {
        long[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Long> o, FUnCon f) {
        boolean[] mbits = ((LongMaxMask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }


    DoubleMaxVector toFP() {
        long[] vec = getElements();
        double[] res = new double[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Double.longBitsToDouble(vec[i]);
        }
        return new DoubleMaxVector(res);
    }

    @Override
    public LongMaxVector rotateEL(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++){
            res[(j + i) % length()] = vec[i];
        }
        return new LongMaxVector(res);
    }

    @Override
    public LongMaxVector rotateER(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new LongMaxVector(res);
    }

    @Override
    public LongMaxVector shiftEL(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new LongMaxVector(res);
    }

    @Override
    public LongMaxVector shiftER(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new LongMaxVector(res);
    }

    @Override
    @ForceInline
    public LongMaxVector rearrange(Vector<Long> v,
                                  Shuffle<Long> s, Mask<Long> m) {
        return this.rearrange(s).blend(v.rearrange(s), m);
    }

    @Override
    @ForceInline
    public LongMaxVector rearrange(Shuffle<Long> o1) {
    Objects.requireNonNull(o1);
    LongMaxShuffle s =  (LongMaxShuffle)o1;

        return VectorIntrinsics.rearrangeOp(
            LongMaxVector.class, LongMaxShuffle.class, long.class, LENGTH,
            this, s,
            (v1, s_) -> v1.uOp((i, a) -> {
            long[] vec = this.getElements();
            int ei = s_.getElement(i);
            return vec[ei];
        }));
    }

    @Override
    @ForceInline
    public LongMaxVector blend(Vector<Long> o1, Mask<Long> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        LongMaxVector v = (LongMaxVector)o1;
        LongMaxMask   m = (LongMaxMask)o2;

        return VectorIntrinsics.blend(
            LongMaxVector.class, LongMaxMask.class, long.class, LENGTH,
            this, v, m,
            (v1, v2, m_) -> v1.bOp(v2, (i, a, b) -> m_.getElement(i) ? b : a));
    }

    // Accessors

    @Override
    public long get(int i) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        return (long) VectorIntrinsics.extract(
                                LongMaxVector.class, long.class, LENGTH,
                                this, i,
                                (vec, ix) -> {
                                    long[] vecarr = vec.getElements();
                                    return (long)vecarr[ix];
                                });
    }

    @Override
    public LongMaxVector with(int i, long e) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        return VectorIntrinsics.insert(
                                LongMaxVector.class, long.class, LENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    long[] res = v.getElements().clone();
                                    res[ix] = (long)bits;
                                    return new LongMaxVector(res);
                                });
    }

    // Mask

    static final class LongMaxMask extends AbstractMask<Long> {
        static final LongMaxMask TRUE_MASK = new LongMaxMask(true);
        static final LongMaxMask FALSE_MASK = new LongMaxMask(false);

        private final boolean[] bits; // Don't access directly, use getBits() instead.

        public LongMaxMask(boolean[] bits) {
            this(bits, 0);
        }

        public LongMaxMask(boolean[] bits, int offset) {
            boolean[] a = new boolean[species().length()];
            for (int i = 0; i < a.length; i++) {
                a[i] = bits[offset + i];
            }
            this.bits = a;
        }

        public LongMaxMask(boolean val) {
            boolean[] bits = new boolean[species().length()];
            Arrays.fill(bits, val);
            this.bits = bits;
        }

        boolean[] getBits() {
            return VectorIntrinsics.maybeRebox(this).bits;
        }

        @Override
        LongMaxMask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMaxMask(res);
        }

        @Override
        LongMaxMask bOp(Mask<Long> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMaxMask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMaxMask(res);
        }

        @Override
        public LongMaxSpecies species() {
            return SPECIES;
        }

        @Override
        public LongMaxVector toVector() {
            long[] res = new long[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                // -1 will result in the most significant bit being set in
                // addition to some or all other bits
                res[i] = (long) (bits[i] ? -1 : 0);
            }
            return new LongMaxVector(res);
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMaxMask not() {
            return (LongMaxMask) VectorIntrinsics.unaryOp(
                                             VECTOR_OP_NOT, LongMaxMask.class, long.class, LENGTH,
                                             this,
                                             (m1) -> m1.uOp((i, a) -> !a));
        }

        // Binary operations

        @Override
        @ForceInline
        public LongMaxMask and(Mask<Long> o) {
            Objects.requireNonNull(o);
            LongMaxMask m = (LongMaxMask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, LongMaxMask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMaxMask or(Mask<Long> o) {
            Objects.requireNonNull(o);
            LongMaxMask m = (LongMaxMask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, LongMaxMask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, LongMaxMask.class, long.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, LongMaxMask.class, long.class, LENGTH,
                                         this, species().maskAllTrue(),
                                         (m1, m2) -> super.allTrue());
        }
    }

    // Shuffle

    static final class LongMaxShuffle extends AbstractShuffle<Long> {
        LongMaxShuffle(byte[] reorder) {
            super(reorder);
        }

        public LongMaxShuffle(int[] reorder) {
            super(reorder);
        }

        public LongMaxShuffle(int[] reorder, int i) {
            super(reorder, i);
        }

        public LongMaxShuffle(IntUnaryOperator f) {
            super(f);
        }

        @Override
        public LongMaxSpecies species() {
            return SPECIES;
        }

        @Override
        public LongMaxVector toVector() {
            long[] va = new long[SPECIES.length()];
            for (int i = 0; i < va.length; i++) {
              va[i] = (long) getElement(i);
            }
            return species().fromArray(va, 0);
        }

        @Override
        public LongMaxShuffle rearrange(Vector.Shuffle<Long> o) {
            LongMaxShuffle s = (LongMaxShuffle) o;
            byte[] r = new byte[reorder.length];
            for (int i = 0; i < reorder.length; i++) {
                r[i] = reorder[s.reorder[i]];
            }
            return new LongMaxShuffle(r);
        }
    }

    // Species

    @Override
    public LongMaxSpecies species() {
        return SPECIES;
    }

    static final class LongMaxSpecies extends LongSpecies {
        static final int BIT_SIZE = Shape.S_Max_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Long.SIZE;

        @Override
        public String toString() {
           StringBuilder sb = new StringBuilder("Shape[");
           sb.append(bitSize()).append(" bits, ");
           sb.append(length()).append(" ").append(long.class.getSimpleName()).append("s x ");
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
        public Class<Long> elementType() {
            return long.class;
        }

        @Override
        @ForceInline
        public int elementSize() {
            return Long.SIZE;
        }

        @Override
        @ForceInline
        public Shape shape() {
            return Shape.S_Max_BIT;
        }

        @Override
        LongMaxVector op(FOp f) {
            long[] res = new long[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new LongMaxVector(res);
        }

        @Override
        LongMaxVector op(Mask<Long> o, FOp f) {
            long[] res = new long[length()];
            boolean[] mbits = ((LongMaxMask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new LongMaxVector(res);
        }

        @Override
        LongMaxMask opm(FOpm f) {
            boolean[] res = new boolean[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = (boolean)f.apply(i);
            }
            return new LongMaxMask(res);
        }

        // Factories

        @Override
        public LongMaxMask maskFromValues(boolean... bits) {
            return new LongMaxMask(bits);
        }

        @Override
        public LongMaxShuffle shuffle(IntUnaryOperator f) {
            return new LongMaxShuffle(f);
        }

        @Override
        public LongMaxShuffle shuffleIota() {
            return new LongMaxShuffle(AbstractShuffle.IDENTITY);
        }

        @Override
        public LongMaxShuffle shuffleFromValues(int... ixs) {
            return new LongMaxShuffle(ixs);
        }

        @Override
        public LongMaxShuffle shuffleFromArray(int[] ixs, int i) {
            return new LongMaxShuffle(ixs, i);
        }

        @Override
        @ForceInline
        public LongMaxVector zero() {
            return VectorIntrinsics.broadcastCoerced(LongMaxVector.class, long.class, LENGTH,
                                                     0,
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public LongMaxVector broadcast(long e) {
            return VectorIntrinsics.broadcastCoerced(
                LongMaxVector.class, long.class, LENGTH,
                e,
                ((long bits) -> SPECIES.op(i -> (long)bits)));
        }

        @Override
        @ForceInline
        public LongMaxMask maskAllTrue() {
            return VectorIntrinsics.broadcastCoerced(LongMaxMask.class, long.class, LENGTH,
                                                     (long)-1,
                                                     (z -> LongMaxMask.TRUE_MASK));
        }

        @Override
        @ForceInline
        public LongMaxMask maskAllFalse() {
            return VectorIntrinsics.broadcastCoerced(LongMaxMask.class, long.class, LENGTH,
                                                     0,
                                                     (z -> LongMaxMask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public LongMaxVector scalars(long... es) {
            Objects.requireNonNull(es);
            int ix = VectorIntrinsics.checkIndex(0, es.length, LENGTH);
            return VectorIntrinsics.load(LongMaxVector.class, long.class, LENGTH,
                                         es, Unsafe.ARRAY_LONG_BASE_OFFSET,
                                         es, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public LongMaxMask maskFromArray(boolean[] bits, int ix) {
            Objects.requireNonNull(bits);
            ix = VectorIntrinsics.checkIndex(ix, bits.length, LENGTH);
            return VectorIntrinsics.load(LongMaxMask.class, long.class, LENGTH,
                                         bits, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                                         bits, ix,
                                         (c, idx) -> opm(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public LongMaxVector fromArray(long[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
            return VectorIntrinsics.load(LongMaxVector.class, long.class, LENGTH,
                                         a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_LONG_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public LongMaxVector fromArray(long[] a, int ax, Mask<Long> m) {
            return zero().blend(fromArray(a, ax), m);
        }

        @Override
        @ForceInline
        public LongMaxVector fromByteArray(byte[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(LongMaxVector.class, long.class, LENGTH,
                                         a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = ByteBuffer.wrap(c, idx, a.length - idx).order(ByteOrder.nativeOrder());
                                             LongBuffer tb = bbc.asLongBuffer();
                                             return op(i -> tb.get());
                                         });
        }
        @Override
        @ForceInline
        public LongMaxVector fromArray(long[] a, int ix, int[] b, int iy) {
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);

            // Index vector: vix[0:n] = i -> ix + indexMap[iy + i]
            IntVector vix = INDEX_SPEC.fromArray(b, iy).add(ix);

            vix = VectorIntrinsics.checkIndex(vix, a.length);

            return VectorIntrinsics.loadWithMap(LongMaxVector.class, long.class, LENGTH, vix.getClass(),
                                        a, Unsafe.ARRAY_LONG_BASE_OFFSET, vix,
                                        a, ix, b, iy,
                                       (c, idx, indexMap, idy) -> op(n -> c[idx + indexMap[idy+n]]));
       }

       @Override
       @ForceInline
       public LongMaxVector fromArray(long[] a, int ax, Mask<Long> m, int[] indexMap, int j) {
           // @@@ This can result in out of bounds errors for unset mask lanes
           return zero().blend(fromArray(a, ax, indexMap, j), m);
       }


        @Override
        @ForceInline
        public LongMaxVector fromByteArray(byte[] a, int ix, Mask<Long> m) {
            return zero().blend(fromByteArray(a, ix), m);
        }

        @Override
        @ForceInline
        public LongMaxVector fromByteBuffer(ByteBuffer bb, int ix) {
            if (bb.order() != ByteOrder.nativeOrder()) {
                throw new IllegalArgumentException();
            }
            ix = VectorIntrinsics.checkIndex(ix, bb.limit(), bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(LongMaxVector.class, long.class, LENGTH,
                                         U.getReference(bb, BYTE_BUFFER_HB), U.getLong(bb, BUFFER_ADDRESS) + ix,
                                         bb, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                             LongBuffer tb = bbc.asLongBuffer();
                                             return op(i -> tb.get());
                                         });
        }

        @Override
        @ForceInline
        public LongMaxVector fromByteBuffer(ByteBuffer bb, int ix, Mask<Long> m) {
            return zero().blend(fromByteBuffer(bb, ix), m);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <F> LongMaxVector cast(Vector<F> o) {
            if (o.length() != LENGTH)
                throw new IllegalArgumentException("Vector length this species length differ");

            return VectorIntrinsics.cast(
                o.getClass(),
                o.elementType(), LENGTH,
                LongMaxVector.class,
                long.class, LENGTH,
                o, this,
                (s, v) -> s.castDefault(v)
            );
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        private <F> LongMaxVector castDefault(Vector<F> v) {
            // Allocate array of required size
            int limit = length();
            long[] a = new long[limit];

            Class<?> vtype = v.species().elementType();
            if (vtype == byte.class) {
                ByteVector tv = (ByteVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else if (vtype == short.class) {
                ShortVector tv = (ShortVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else if (vtype == int.class) {
                IntVector tv = (IntVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else if (vtype == long.class){
                LongVector tv = (LongVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else if (vtype == float.class){
                FloatVector tv = (FloatVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else if (vtype == double.class){
                DoubleVector tv = (DoubleVector)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) tv.get(i);
                }
            } else {
                throw new UnsupportedOperationException("Bad lane type for casting.");
            }

            return scalars(a);
        }

        @Override
        @ForceInline
        public <E> LongMaxMask cast(Mask<E> m) {
            if (m.length() != LENGTH)
                throw new IllegalArgumentException("Mask length this species length differ");
            return new LongMaxMask(m.toArray());
        }

        @Override
        @ForceInline
        public <E> LongMaxShuffle cast(Shuffle<E> s) {
            if (s.length() != LENGTH)
                throw new IllegalArgumentException("Shuffle length this species length differ");
            return new LongMaxShuffle(s.toArray());
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <F> LongMaxVector rebracket(Vector<F> o) {
            Objects.requireNonNull(o);
            if (o.elementType() == byte.class) {
                ByteMaxVector so = (ByteMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    ByteMaxVector.class,
                    byte.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.elementType() == short.class) {
                ShortMaxVector so = (ShortMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    ShortMaxVector.class,
                    short.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.elementType() == int.class) {
                IntMaxVector so = (IntMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    IntMaxVector.class,
                    int.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.elementType() == long.class) {
                LongMaxVector so = (LongMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    LongMaxVector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.elementType() == float.class) {
                FloatMaxVector so = (FloatMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    FloatMaxVector.class,
                    float.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.elementType() == double.class) {
                DoubleMaxVector so = (DoubleMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    DoubleMaxVector.class,
                    double.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else {
                throw new InternalError("Unimplemented type");
            }
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public LongMaxVector resize(Vector<Long> o) {
            Objects.requireNonNull(o);
            if (o.bitSize() == 64 && (o instanceof Long64Vector)) {
                Long64Vector so = (Long64Vector)o;
                return VectorIntrinsics.reinterpret(
                    Long64Vector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.bitSize() == 128 && (o instanceof Long128Vector)) {
                Long128Vector so = (Long128Vector)o;
                return VectorIntrinsics.reinterpret(
                    Long128Vector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.bitSize() == 256 && (o instanceof Long256Vector)) {
                Long256Vector so = (Long256Vector)o;
                return VectorIntrinsics.reinterpret(
                    Long256Vector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if (o.bitSize() == 512 && (o instanceof Long512Vector)) {
                Long512Vector so = (Long512Vector)o;
                return VectorIntrinsics.reinterpret(
                    Long512Vector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else if ((o.bitSize() > 0) && (o.bitSize() <= 2048)
                    && (o.bitSize() % 128 == 0) && (o instanceof LongMaxVector)) {
                LongMaxVector so = (LongMaxVector)o;
                return VectorIntrinsics.reinterpret(
                    LongMaxVector.class,
                    long.class, so.length(),
                    LongMaxVector.class,
                    long.class, LENGTH,
                    so, this,
                    (s, v) -> (LongMaxVector) s.reshape(v)
                );
            } else {
                throw new InternalError("Unimplemented size");
            }
        }
    }
}
