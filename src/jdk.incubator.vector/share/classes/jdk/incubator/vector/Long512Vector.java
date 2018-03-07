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
import java.util.Arrays;
import java.util.Objects;
import jdk.internal.vm.annotation.ForceInline;
import static jdk.incubator.vector.VectorIntrinsics.*;

@SuppressWarnings("cast")
final class Long512Vector extends LongVector<Shapes.S512Bit> {
    static final Long512Species SPECIES = new Long512Species();

    static final Long512Vector ZERO = new Long512Vector();

    static final int LENGTH = SPECIES.length();

    private final long[] vec; // Don't access directly, use getElements() instead.

    private long[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    Long512Vector() {
        vec = new long[SPECIES.length()];
    }

    Long512Vector(long[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    Long512Vector uOp(FUnOp f) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector uOp(Mask<Long, Shapes.S512Bit> o, FUnOp f) {
        long[] vec = getElements();
        long[] res = new long[length()];
        boolean[] mbits = ((Long512Mask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new Long512Vector(res);
    }

    // Binary operator

    @Override
    Long512Vector bOp(Vector<Long, Shapes.S512Bit> o, FBinOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((Long512Vector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector bOp(Vector<Long, Shapes.S512Bit> o1, Mask<Long, Shapes.S512Bit> o2, FBinOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((Long512Vector)o1).getElements();
        boolean[] mbits = ((Long512Mask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new Long512Vector(res);
    }

    // Trinary operator

    @Override
    Long512Vector tOp(Vector<Long, Shapes.S512Bit> o1, Vector<Long, Shapes.S512Bit> o2, FTriOp f) {
        long[] res = new long[length()];
        long[] vec1 = this.getElements();
        long[] vec2 = ((Long512Vector)o1).getElements();
        long[] vec3 = ((Long512Vector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new Long512Vector(res);
    }

    @Override
    Long512Vector tOp(Vector<Long, Shapes.S512Bit> o1, Vector<Long, Shapes.S512Bit> o2, Mask<Long, Shapes.S512Bit> o3, FTriOp f) {
        long[] res = new long[length()];
        long[] vec1 = getElements();
        long[] vec2 = ((Long512Vector)o1).getElements();
        long[] vec3 = ((Long512Vector)o2).getElements();
        boolean[] mbits = ((Long512Mask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new Long512Vector(res);
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
    public LongVector<Shapes.S512Bit> add(long o) {
        return add(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> add(long o, Mask<Long,Shapes.S512Bit> m) {
        return add(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> addSaturate(long o) {
        return addSaturate(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> addSaturate(long o, Mask<Long,Shapes.S512Bit> m) {
        return addSaturate(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> sub(long o) {
        return sub(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> sub(long o, Mask<Long,Shapes.S512Bit> m) {
        return sub(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> subSaturate(long o) {
        return subSaturate(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> subSaturate(long o, Mask<Long,Shapes.S512Bit> m) {
        return subSaturate(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> mul(long o) {
        return mul(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> mul(long o, Mask<Long,Shapes.S512Bit> m) {
        return mul(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> div(long o) {
        return div(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> div(long o, Mask<Long,Shapes.S512Bit> m) {
        return div(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> min(long o) {
        return min(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> max(long o) {
        return max(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> equal(long o) {
        return equal(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> notEqual(long o) {
        return notEqual(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> lessThan(long o) {
        return lessThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> lessThanEq(long o) {
        return lessThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> greaterThan(long o) {
        return greaterThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Long, Shapes.S512Bit> greaterThanEq(long o) {
        return greaterThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> blend(long o, Mask<Long,Shapes.S512Bit> m) {
        return blend(SPECIES.broadcast(o), m);
    }


    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> and(long o) {
        return and(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> and(long o, Mask<Long,Shapes.S512Bit> m) {
        return and(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> or(long o) {
        return or(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> or(long o, Mask<Long,Shapes.S512Bit> m) {
        return or(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> xor(long o) {
        return xor(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> xor(long o, Mask<Long,Shapes.S512Bit> m) {
        return xor(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> floorDiv(long o) {
        return floorDiv(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> floorDiv(long o, Mask<Long,Shapes.S512Bit> m) {
        return floorDiv(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> floorMod(long o) {
        return floorMod(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public LongVector<Shapes.S512Bit> floorMod(long o, Mask<Long,Shapes.S512Bit> m) {
        return floorMod(SPECIES.broadcast(o), m);
    }


    // Unary operations



    // Binary operations

    @Override
    @ForceInline
    public Long512Vector add(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a + b)));
    }

    @Override
    @ForceInline
    public Long512Vector sub(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a - b)));
    }

    @Override
    @ForceInline
    public Long512Vector mul(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a * b)));
    }

    @Override
    @ForceInline
    public Long512Vector div(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_DIV, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a / b)));
    }



    @Override
    @ForceInline
    public Long512Vector and(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_AND, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a & b)));
    }

    @Override
    @ForceInline
    public Long512Vector or(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_OR, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a | b)));
    }

    @Override
    @ForceInline
    public Long512Vector xor(Vector<Long,Shapes.S512Bit> o) {
        Objects.requireNonNull(o);
        Long512Vector v = (Long512Vector)o;
        return (Long512Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_XOR, Long512Vector.class, long.class, LENGTH,
            this, v,
            (v1, v2) -> ((Long512Vector)v1).bOp(v2, (i, a, b) -> (long)(a ^ b)));
    }

    @Override
    @ForceInline
    public Long512Vector and(Vector<Long,Shapes.S512Bit> v, Mask<Long, Shapes.S512Bit> m) {
        return blend(and(v), m);
    }

    @Override
    @ForceInline
    public Long512Vector or(Vector<Long,Shapes.S512Bit> v, Mask<Long, Shapes.S512Bit> m) {
        return blend(or(v), m);
    }

    @Override
    @ForceInline
    public Long512Vector xor(Vector<Long,Shapes.S512Bit> v, Mask<Long, Shapes.S512Bit> m) {
        return blend(xor(v), m);
    }

    @Override
    @ForceInline
    public Long512Vector shiftL(int s) {
        return (Long512Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_LSHIFT, Long512Vector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a << i)));
    }

    @Override
    @ForceInline
    public Long512Vector shiftR(int s) {
        return (Long512Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_URSHIFT, Long512Vector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a >>> i)));
    }

    @Override
    @ForceInline
    public Long512Vector aShiftR(int s) {
        return (Long512Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_RSHIFT, Long512Vector.class, long.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (long) (a >> i)));
    }

    // Ternary operations


    // Type specific horizontal reductions

    @Override
    @ForceInline
    public long addAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_ADD, Long512Vector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 0, (i, a, b) -> (long) (a + b)));
    }

    @Override
    @ForceInline
    public long mulAll() {
        return (long) VectorIntrinsics.reductionCoerced(
            VECTOR_OP_MUL, Long512Vector.class, long.class, LENGTH,
            this,
            v -> (long) v.rOp((long) 1, (i, a, b) -> (long) (a * b)));
    }

    // Memory operations

    @Override
    @ForceInline
    public void intoArray(long[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
        VectorIntrinsics.store(Long512Vector.class, long.class, LENGTH,
                               a, ix, this,
                               (arr, idx, v) -> v.forEach((i, a_) -> ((long[])arr)[idx + i] = a_));
    }

    @Override
    @ForceInline
    public void intoArray(long[] a, int ax, Mask<Long, Shapes.S512Bit> m) {
        // TODO: use better default impl: forEach(m, (i, a_) -> a[ax + i] = a_);
        Long512Vector oldVal = SPECIES.fromArray(a, ax);
        Long512Vector newVal = oldVal.blend(this, m);
        newVal.intoArray(a, ax);
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

        Long512Vector that = (Long512Vector) o;
        return Arrays.equals(this.getElements(), that.getElements());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    Long512Mask bTest(Vector<Long, Shapes.S512Bit> o, FBinTest f) {
        long[] vec1 = getElements();
        long[] vec2 = ((Long512Vector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Long512Mask(bits);
    }

    // Comparisons


    // Foreach

    @Override
    void forEach(FUnCon f) {
        long[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Long, Shapes.S512Bit> o, FUnCon f) {
        boolean[] mbits = ((Long512Mask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }


    Double512Vector toFP() {
        long[] vec = getElements();
        double[] res = new double[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Double.longBitsToDouble(vec[i]);
        }
        return new Double512Vector(res);
    }

    @Override
    public Long512Vector rotateEL(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length(); i++){
            res[j + i % length()] = vec[i];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector rotateER(int j) {
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
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shiftEL(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shiftER(int j) {
        long[] vec = getElements();
        long[] res = new long[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new Long512Vector(res);
    }

    @Override
    public Long512Vector shuffle(Vector<Long, Shapes.S512Bit> o, Shuffle<Long, Shapes.S512Bit> s) {
        Long512Vector v = (Long512Vector) o;
        return uOp((i, a) -> {
            long[] vec = this.getElements();
            int e = s.getElement(i);
            if(e >= 0 && e < length()) {
                //from this
                return vec[e];
            } else if(e < length() * 2) {
                //from o
                return v.getElements()[e - length()];
            } else {
                throw new ArrayIndexOutOfBoundsException("Bad reordering for shuffle");
            }
        });
    }

    @Override
    public Long512Vector swizzle(Shuffle<Long, Shapes.S512Bit> s) {
        return uOp((i, a) -> {
            long[] vec = this.getElements();
            int e = s.getElement(i);
            if(e >= 0 && e < length()) {
                return vec[e];
            } else {
                throw new ArrayIndexOutOfBoundsException("Bad reordering for shuffle");
            }
        });
    }

    @Override
    @ForceInline
    public Long512Vector blend(Vector<Long, Shapes.S512Bit> o1, Mask<Long, Shapes.S512Bit> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        Long512Vector v = (Long512Vector)o1;
        Long512Mask   m = (Long512Mask)o2;

        return (Long512Vector) VectorIntrinsics.blend(
            Long512Vector.class, Long512Mask.class, long.class, LENGTH,
            this, v, m,
            (v1, v2, m_) -> v1.bOp(v2, (i, a, b) -> m_.getElement(i) ? b : a));
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public <F> Vector<F, Shapes.S512Bit> rebracket(Species<F, Shapes.S512Bit> species) {
        Objects.requireNonNull(species);
        // TODO: check proper element type
        // TODO: update to pass species as an argument and preferably
        // push down intrinsic call into species implementation
        return VectorIntrinsics.rebracket(
            Long512Vector.class, long.class, LENGTH,
            species.elementType(), this,
            (v, t) -> species.reshape(v)
        );
    }

    // Accessors

    @Override
    public long get(int i) {
        long[] vec = getElements();
        return vec[i];
    }

    @Override
    public Long512Vector with(int i, long e) {
        long[] res = vec.clone();
        res[i] = e;
        return new Long512Vector(res);
    }

    // Mask

    static final class Long512Mask extends AbstractMask<Long, Shapes.S512Bit> {
        static final Long512Mask TRUE_MASK = new Long512Mask(true);
        static final Long512Mask FALSE_MASK = new Long512Mask(false);

        // FIXME: was temporarily put here to simplify rematerialization support in the JVM
        private final boolean[] bits; // Don't access directly, use getBits() instead.

        public Long512Mask(boolean[] bits) {
            this.bits = Arrays.copyOf(bits, species().length());
        }

        public Long512Mask(boolean val) {
            boolean[] bits = new boolean[species().length()];
            Arrays.fill(bits, val);
            this.bits = bits;
        }

        boolean[] getBits() {
            return VectorIntrinsics.maybeRebox(this).bits;
        }

        @Override
        Long512Mask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Long512Mask(res);
        }

        @Override
        Long512Mask bOp(Mask<Long, Shapes.S512Bit> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Long512Mask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Long512Mask(res);
        }

        @Override
        public Long512Species species() {
            return SPECIES;
        }

        @Override
        public Long512Vector toVector() {
            long[] res = new long[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = (long) (bits[i] ? -1 : 0);
            }
            return new Long512Vector(res);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <Z> Mask<Z, Shapes.S512Bit> rebracket(Species<Z, Shapes.S512Bit> species) {
            Objects.requireNonNull(species);
            // TODO: check proper element type
            return VectorIntrinsics.rebracket(
                Long512Mask.class, long.class, LENGTH,
                species.elementType(), this,
                (m, t) -> m.reshape(species)
            );
        }

        // Unary operations

        //Mask<E, S> not();

        // Binary operations

        @Override
        @ForceInline
        public Long512Mask and(Mask<Long,Shapes.S512Bit> o) {
            Objects.requireNonNull(o);
            Long512Mask m = (Long512Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, Long512Mask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Long512Mask or(Mask<Long,Shapes.S512Bit> o) {
            Objects.requireNonNull(o);
            Long512Mask m = (Long512Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, Long512Mask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, Long512Mask.class, long.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, Long512Mask.class, long.class, LENGTH,
                                         this, species().trueMask(),
                                         (m1, m2) -> super.allTrue());
        }
    }

    // Shuffle

    static final class Long512Shuffle extends AbstractShuffle<Long, Shapes.S512Bit> {
        static final IntVector.IntSpecies<Shapes.S512Bit> INT_SPECIES = (IntVector.IntSpecies<Shapes.S512Bit>) Vector.speciesInstance(Integer.class, Shapes.S_512_BIT);

        public Long512Shuffle(int[] reorder) {
            super(reorder);
        }

        @Override
        public Long512Species species() {
            return SPECIES;
        }

        @Override
        public IntVector.IntSpecies<Shapes.S512Bit> intSpecies() {
            return INT_SPECIES;
        }
    }

    // Species

    @Override
    public Long512Species species() {
        return SPECIES;
    }

    static final class Long512Species extends LongSpecies<Shapes.S512Bit> {
        static final int BIT_SIZE = Shapes.S_512_BIT.bitSize();

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
        public int bitSize() {
            return BIT_SIZE;
        }

        @Override
        public int length() {
            return LENGTH;
        }

        @Override
        public Class<Long> elementType() {
            return Long.class;
        }

        @Override
        public int elementSize() {
            return Long.SIZE;
        }

        @Override
        public Shapes.S512Bit shape() {
            return Shapes.S_512_BIT;
        }

        @Override
        Long512Vector op(FOp f) {
            long[] res = new long[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new Long512Vector(res);
        }

        @Override
        Long512Vector op(Mask<Long, Shapes.S512Bit> o, FOp f) {
            long[] res = new long[length()];
            boolean[] mbits = ((Long512Mask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new Long512Vector(res);
        }

        // Factories

        @Override
        public Long512Mask constantMask(boolean... bits) {
            return new Long512Mask(bits);
        }

        @Override
        public Long512Shuffle constantShuffle(int... ixs) {
            return new Long512Shuffle(ixs);
        }

        @Override
        @ForceInline
        public Long512Vector zero() {
            return VectorIntrinsics.broadcastCoerced(Long512Vector.class, long.class, LENGTH,
                                                     0,
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public Long512Vector broadcast(long e) {
            return VectorIntrinsics.broadcastCoerced(
                Long512Vector.class, long.class, LENGTH,
                e,
                ((long bits) -> SPECIES.op(i -> (long)bits)));
        }

        @Override
        @ForceInline
        public Long512Mask trueMask() {
            return VectorIntrinsics.broadcastCoerced(Long512Mask.class, long.class, LENGTH,
                                                     (long)-1,
                                                     (z -> Long512Mask.TRUE_MASK));
        }

        @Override
        @ForceInline
        public Long512Mask falseMask() {
            return VectorIntrinsics.broadcastCoerced(Long512Mask.class, long.class, LENGTH,
                                                     0,
                                                     (z -> Long512Mask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public Long512Vector fromArray(long[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
            return (Long512Vector) VectorIntrinsics.load(Long512Vector.class, long.class, LENGTH,
                                                        a, ix,
                                                        (arr, idx) -> super.fromArray((long[]) arr, idx));
        }

        @Override
        @ForceInline
        public Long512Vector fromArray(long[] a, int ax, Mask<Long, Shapes.S512Bit> m) {
            return zero().blend(fromArray(a, ax), m); // TODO: use better default impl: op(m, i -> a[ax + i]);
        }
    }
}
