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
final class Byte128Vector extends ByteVector<Shapes.S128Bit> {
    static final Byte128Species SPECIES = new Byte128Species();

    static final Byte128Vector ZERO = new Byte128Vector();

    static final int LENGTH = SPECIES.length();

    private final byte[] vec; // Don't access directly, use getElements() instead.

    private byte[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    Byte128Vector() {
        vec = new byte[SPECIES.length()];
    }

    Byte128Vector(byte[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    Byte128Vector uOp(FUnOp f) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new Byte128Vector(res);
    }

    @Override
    Byte128Vector uOp(Mask<Byte, Shapes.S128Bit> o, FUnOp f) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        boolean[] mbits = ((Byte128Mask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new Byte128Vector(res);
    }

    // Binary operator

    @Override
    Byte128Vector bOp(Vector<Byte, Shapes.S128Bit> o, FBinOp f) {
        byte[] res = new byte[length()];
        byte[] vec1 = this.getElements();
        byte[] vec2 = ((Byte128Vector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Byte128Vector(res);
    }

    @Override
    Byte128Vector bOp(Vector<Byte, Shapes.S128Bit> o1, Mask<Byte, Shapes.S128Bit> o2, FBinOp f) {
        byte[] res = new byte[length()];
        byte[] vec1 = this.getElements();
        byte[] vec2 = ((Byte128Vector)o1).getElements();
        boolean[] mbits = ((Byte128Mask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new Byte128Vector(res);
    }

    // Trinary operator

    @Override
    Byte128Vector tOp(Vector<Byte, Shapes.S128Bit> o1, Vector<Byte, Shapes.S128Bit> o2, FTriOp f) {
        byte[] res = new byte[length()];
        byte[] vec1 = this.getElements();
        byte[] vec2 = ((Byte128Vector)o1).getElements();
        byte[] vec3 = ((Byte128Vector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new Byte128Vector(res);
    }

    @Override
    Byte128Vector tOp(Vector<Byte, Shapes.S128Bit> o1, Vector<Byte, Shapes.S128Bit> o2, Mask<Byte, Shapes.S128Bit> o3, FTriOp f) {
        byte[] res = new byte[length()];
        byte[] vec1 = getElements();
        byte[] vec2 = ((Byte128Vector)o1).getElements();
        byte[] vec3 = ((Byte128Vector)o2).getElements();
        boolean[] mbits = ((Byte128Mask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new Byte128Vector(res);
    }

    @Override
    byte rOp(byte v, FBinOp f) {
        byte[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Binary operations with scalars

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> add(byte o) {
        return add(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> add(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return add(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> addSaturate(byte o) {
        return addSaturate(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> addSaturate(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return addSaturate(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> sub(byte o) {
        return sub(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> sub(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return sub(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> subSaturate(byte o) {
        return subSaturate(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> subSaturate(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return subSaturate(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> mul(byte o) {
        return mul(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> mul(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return mul(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> div(byte o) {
        return div(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> div(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return div(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> min(byte o) {
        return min(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> max(byte o) {
        return max(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> equal(byte o) {
        return equal(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> notEqual(byte o) {
        return notEqual(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> lessThan(byte o) {
        return lessThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> lessThanEq(byte o) {
        return lessThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> greaterThan(byte o) {
        return greaterThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Byte, Shapes.S128Bit> greaterThanEq(byte o) {
        return greaterThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> blend(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return blend(SPECIES.broadcast(o), m);
    }


    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> and(byte o) {
        return and(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> and(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return and(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> or(byte o) {
        return or(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> or(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return or(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> xor(byte o) {
        return xor(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> xor(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return xor(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> floorDiv(byte o) {
        return floorDiv(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> floorDiv(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return floorDiv(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> floorMod(byte o) {
        return floorMod(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public ByteVector<Shapes.S128Bit> floorMod(byte o, Mask<Byte,Shapes.S128Bit> m) {
        return floorMod(SPECIES.broadcast(o), m);
    }


    // Unary operations



    // Binary operations

    @Override
    @ForceInline
    public Byte128Vector add(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a + b)));
    }

    @Override
    @ForceInline
    public Byte128Vector sub(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a - b)));
    }

    @Override
    @ForceInline
    public Byte128Vector mul(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a * b)));
    }

    @Override
    @ForceInline
    public Byte128Vector div(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_DIV, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a / b)));
    }



    @Override
    @ForceInline
    public Byte128Vector and(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_AND, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a & b)));
    }

    @Override
    @ForceInline
    public Byte128Vector or(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_OR, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a | b)));
    }

    @Override
    @ForceInline
    public Byte128Vector xor(Vector<Byte,Shapes.S128Bit> o) {
        Objects.requireNonNull(o);
        Byte128Vector v = (Byte128Vector)o;
        return (Byte128Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_XOR, Byte128Vector.class, byte.class, LENGTH,
            this, v,
            (v1, v2) -> ((Byte128Vector)v1).bOp(v2, (i, a, b) -> (byte)(a ^ b)));
    }

    @Override
    @ForceInline
    public Byte128Vector and(Vector<Byte,Shapes.S128Bit> v, Mask<Byte, Shapes.S128Bit> m) {
        return blend(and(v), m);
    }

    @Override
    @ForceInline
    public Byte128Vector or(Vector<Byte,Shapes.S128Bit> v, Mask<Byte, Shapes.S128Bit> m) {
        return blend(or(v), m);
    }

    @Override
    @ForceInline
    public Byte128Vector xor(Vector<Byte,Shapes.S128Bit> v, Mask<Byte, Shapes.S128Bit> m) {
        return blend(xor(v), m);
    }

    @Override
    @ForceInline
    public Byte128Vector shiftL(int s) {
        return (Byte128Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_LSHIFT, Byte128Vector.class, byte.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (byte) (a << i)));
    }

    @Override
    @ForceInline
    public Byte128Vector shiftR(int s) {
        return (Byte128Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_URSHIFT, Byte128Vector.class, byte.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (byte) (a >>> i)));
    }

    @Override
    @ForceInline
    public Byte128Vector aShiftR(int s) {
        return (Byte128Vector) VectorIntrinsics.broadcastInt(
            VECTOR_OP_RSHIFT, Byte128Vector.class, byte.class, LENGTH,
            this, s,
            (v, i) -> v.uOp((__, a) -> (byte) (a >> i)));
    }

    // Ternary operations


    // Type specific horizontal reductions


    // Memory operations

    @Override
    @ForceInline
    public void intoArray(byte[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
        VectorIntrinsics.store(Byte128Vector.class, byte.class, LENGTH,
                               a, ix, this,
                               (arr, idx, v) -> v.forEach((i, a_) -> ((byte[])arr)[idx + i] = a_));
    }

    @Override
    @ForceInline
    public void intoArray(byte[] a, int ax, Mask<Byte, Shapes.S128Bit> m) {
        // TODO: use better default impl: forEach(m, (i, a_) -> a[ax + i] = a_);
        Byte128Vector oldVal = SPECIES.fromArray(a, ax);
        Byte128Vector newVal = oldVal.blend(this, m);
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

        Byte128Vector that = (Byte128Vector) o;
        return Arrays.equals(this.getElements(), that.getElements());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    Byte128Mask bTest(Vector<Byte, Shapes.S128Bit> o, FBinTest f) {
        byte[] vec1 = getElements();
        byte[] vec2 = ((Byte128Vector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Byte128Mask(bits);
    }

    // Comparisons


    // Foreach

    @Override
    void forEach(FUnCon f) {
        byte[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Byte, Shapes.S128Bit> o, FUnCon f) {
        boolean[] mbits = ((Byte128Mask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }



    @Override
    public Byte128Vector rotateEL(int j) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        for (int i = 0; i < length(); i++){
            res[j + i % length()] = vec[i];
        }
        return new Byte128Vector(res);
    }

    @Override
    public Byte128Vector rotateER(int j) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new Byte128Vector(res);
    }

    @Override
    public Byte128Vector shiftEL(int j) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new Byte128Vector(res);
    }

    @Override
    public Byte128Vector shiftER(int j) {
        byte[] vec = getElements();
        byte[] res = new byte[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new Byte128Vector(res);
    }

    @Override
    public Byte128Vector shuffle(Vector<Byte, Shapes.S128Bit> o, Shuffle<Byte, Shapes.S128Bit> s) {
        Byte128Vector v = (Byte128Vector) o;
        return uOp((i, a) -> {
            byte[] vec = this.getElements();
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
    public Byte128Vector swizzle(Shuffle<Byte, Shapes.S128Bit> s) {
        return uOp((i, a) -> {
            byte[] vec = this.getElements();
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
    public Byte128Vector blend(Vector<Byte, Shapes.S128Bit> o1, Mask<Byte, Shapes.S128Bit> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        Byte128Vector v = (Byte128Vector)o1;
        Byte128Mask   m = (Byte128Mask)o2;

        return (Byte128Vector) VectorIntrinsics.blend(
            Byte128Vector.class, Byte128Mask.class, byte.class, LENGTH,
            this, v, m,
            (v1, v2, m_) -> v1.bOp(v2, (i, a, b) -> m_.getElement(i) ? b : a));
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public <F> Vector<F, Shapes.S128Bit> rebracket(Species<F, Shapes.S128Bit> species) {
        Objects.requireNonNull(species);
        // TODO: check proper element type
        // TODO: update to pass species as an argument and preferably
        // push down intrinsic call into species implementation
        return VectorIntrinsics.rebracket(
            Byte128Vector.class, byte.class, LENGTH,
            species.elementType(), this,
            (v, t) -> species.reshape(v)
        );
    }

    // Accessors

    @Override
    public byte get(int i) {
        byte[] vec = getElements();
        return vec[i];
    }

    @Override
    public Byte128Vector with(int i, byte e) {
        byte[] res = vec.clone();
        res[i] = e;
        return new Byte128Vector(res);
    }

    // Mask

    static final class Byte128Mask extends AbstractMask<Byte, Shapes.S128Bit> {
        static final Byte128Mask TRUE_MASK = new Byte128Mask(true);
        static final Byte128Mask FALSE_MASK = new Byte128Mask(false);

        // FIXME: was temporarily put here to simplify rematerialization support in the JVM
        private final boolean[] bits; // Don't access directly, use getBits() instead.

        public Byte128Mask(boolean[] bits) {
            this.bits = Arrays.copyOf(bits, species().length());
        }

        public Byte128Mask(boolean val) {
            boolean[] bits = new boolean[species().length()];
            Arrays.fill(bits, val);
            this.bits = bits;
        }

        boolean[] getBits() {
            return VectorIntrinsics.maybeRebox(this).bits;
        }

        @Override
        Byte128Mask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Byte128Mask(res);
        }

        @Override
        Byte128Mask bOp(Mask<Byte, Shapes.S128Bit> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Byte128Mask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Byte128Mask(res);
        }

        @Override
        public Byte128Species species() {
            return SPECIES;
        }

        @Override
        public Byte128Vector toVector() {
            byte[] res = new byte[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = (byte) (bits[i] ? -1 : 0);
            }
            return new Byte128Vector(res);
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        public <Z> Mask<Z, Shapes.S128Bit> rebracket(Species<Z, Shapes.S128Bit> species) {
            Objects.requireNonNull(species);
            // TODO: check proper element type
            return VectorIntrinsics.rebracket(
                Byte128Mask.class, byte.class, LENGTH,
                species.elementType(), this,
                (m, t) -> m.reshape(species)
            );
        }

        // Unary operations

        //Mask<E, S> not();

        // Binary operations

        @Override
        @ForceInline
        public Byte128Mask and(Mask<Byte,Shapes.S128Bit> o) {
            Objects.requireNonNull(o);
            Byte128Mask m = (Byte128Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, Byte128Mask.class, byte.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Byte128Mask or(Mask<Byte,Shapes.S128Bit> o) {
            Objects.requireNonNull(o);
            Byte128Mask m = (Byte128Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, Byte128Mask.class, byte.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, Byte128Mask.class, byte.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, Byte128Mask.class, byte.class, LENGTH,
                                         this, species().trueMask(),
                                         (m1, m2) -> super.allTrue());
        }
    }

    // Shuffle

    static final class Byte128Shuffle extends AbstractShuffle<Byte, Shapes.S128Bit> {
        static final IntVector.IntSpecies<Shapes.S128Bit> INT_SPECIES = (IntVector.IntSpecies<Shapes.S128Bit>) Vector.speciesInstance(Integer.class, Shapes.S_128_BIT);

        public Byte128Shuffle(int[] reorder) {
            super(reorder);
        }

        @Override
        public Byte128Species species() {
            return SPECIES;
        }

        @Override
        public IntVector.IntSpecies<Shapes.S128Bit> intSpecies() {
            return INT_SPECIES;
        }
    }

    // Species

    @Override
    public Byte128Species species() {
        return SPECIES;
    }

    static final class Byte128Species extends ByteSpecies<Shapes.S128Bit> {
        static final int BIT_SIZE = Shapes.S_128_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Byte.SIZE;

        @Override
        public String toString() {
           StringBuilder sb = new StringBuilder("Shape[");
           sb.append(bitSize()).append(" bits, ");
           sb.append(length()).append(" ").append(byte.class.getSimpleName()).append("s x ");
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
        public Class<Byte> elementType() {
            return Byte.class;
        }

        @Override
        public int elementSize() {
            return Byte.SIZE;
        }

        @Override
        public Shapes.S128Bit shape() {
            return Shapes.S_128_BIT;
        }

        @Override
        Byte128Vector op(FOp f) {
            byte[] res = new byte[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new Byte128Vector(res);
        }

        @Override
        Byte128Vector op(Mask<Byte, Shapes.S128Bit> o, FOp f) {
            byte[] res = new byte[length()];
            boolean[] mbits = ((Byte128Mask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new Byte128Vector(res);
        }

        // Factories

        @Override
        public Byte128Mask constantMask(boolean... bits) {
            return new Byte128Mask(bits);
        }

        @Override
        public Byte128Shuffle constantShuffle(int... ixs) {
            return new Byte128Shuffle(ixs);
        }

        @Override
        @ForceInline
        public Byte128Vector zero() {
            return VectorIntrinsics.broadcastCoerced(Byte128Vector.class, byte.class, LENGTH,
                                                     0,
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public Byte128Vector broadcast(byte e) {
            return VectorIntrinsics.broadcastCoerced(
                Byte128Vector.class, byte.class, LENGTH,
                e,
                ((long bits) -> SPECIES.op(i -> (byte)bits)));
        }

        @Override
        @ForceInline
        public Byte128Mask trueMask() {
            return VectorIntrinsics.broadcastCoerced(Byte128Mask.class, byte.class, LENGTH,
                                                     (byte)-1,
                                                     (z -> Byte128Mask.TRUE_MASK));
        }

        @Override
        @ForceInline
        public Byte128Mask falseMask() {
            return VectorIntrinsics.broadcastCoerced(Byte128Mask.class, byte.class, LENGTH,
                                                     0,
                                                     (z -> Byte128Mask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public Byte128Vector fromArray(byte[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
            return (Byte128Vector) VectorIntrinsics.load(Byte128Vector.class, byte.class, LENGTH,
                                                        a, ix,
                                                        (arr, idx) -> super.fromArray((byte[]) arr, idx));
        }

        @Override
        @ForceInline
        public Byte128Vector fromArray(byte[] a, int ax, Mask<Byte, Shapes.S128Bit> m) {
            return zero().blend(fromArray(a, ax), m); // TODO: use better default impl: op(m, i -> a[ax + i]);
        }
    }
}
