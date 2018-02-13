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
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.vm.annotation.ForceInline;
import static jdk.incubator.vector.VectorIntrinsics.*;

@SuppressWarnings("cast")
final class Float256Vector extends FloatVector<Shapes.S256Bit> {
    static final Float256Species SPECIES = new Float256Species();

    static final Float256Vector ZERO = new Float256Vector();

    static final int LENGTH = SPECIES.length();

    private final float[] vec; // Don't access directly, use getElements() instead.

    private float[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    Float256Vector() {
        vec = new float[SPECIES.length()];
    }

    Float256Vector(float[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    Float256Vector uOp(FUnOp f) {
        float[] vec = getElements();
        float[] res = new float[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new Float256Vector(res);
    }

    @Override
    Float256Vector uOp(Mask<Float, Shapes.S256Bit> o, FUnOp f) {
        float[] vec = getElements();
        float[] res = new float[length()];
        boolean[] mbits = ((Float256Mask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new Float256Vector(res);
    }

    // Binary operator

    @Override
    Float256Vector bOp(Vector<Float, Shapes.S256Bit> o, FBinOp f) {
        float[] res = new float[length()];
        float[] vec1 = this.getElements();
        float[] vec2 = ((Float256Vector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Float256Vector(res);
    }

    @Override
    Float256Vector bOp(Vector<Float, Shapes.S256Bit> o1, Mask<Float, Shapes.S256Bit> o2, FBinOp f) {
        float[] res = new float[length()];
        float[] vec1 = this.getElements();
        float[] vec2 = ((Float256Vector)o1).getElements();
        boolean[] mbits = ((Float256Mask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new Float256Vector(res);
    }

    // Trinary operator

    @Override
    Float256Vector tOp(Vector<Float, Shapes.S256Bit> o1, Vector<Float, Shapes.S256Bit> o2, FTriOp f) {
        float[] res = new float[length()];
        float[] vec1 = this.getElements();
        float[] vec2 = ((Float256Vector)o1).getElements();
        float[] vec3 = ((Float256Vector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new Float256Vector(res);
    }

    @Override
    Float256Vector tOp(Vector<Float, Shapes.S256Bit> o1, Vector<Float, Shapes.S256Bit> o2, Mask<Float, Shapes.S256Bit> o3, FTriOp f) {
        float[] res = new float[length()];
        float[] vec1 = getElements();
        float[] vec2 = ((Float256Vector)o1).getElements();
        float[] vec3 = ((Float256Vector)o2).getElements();
        boolean[] mbits = ((Float256Mask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new Float256Vector(res);
    }

    @Override
    float rOp(float v, FBinOp f) {
        float[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Binary operations

    @Override
    @ForceInline
    public Float256Vector add(Vector<Float,Shapes.S256Bit> o) {
        Objects.requireNonNull(o);
        Float256Vector v = (Float256Vector)o;
        return (Float256Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, Float256Vector.class, float.class, LENGTH,
            this, v,
            (v1, v2) -> ((Float256Vector)v1).bOp(v2, (i, a, b) -> (float)(a + b)));
    }

    @Override
    @ForceInline
    public Float256Vector sub(Vector<Float,Shapes.S256Bit> o) {
        Objects.requireNonNull(o);
        Float256Vector v = (Float256Vector)o;
        return (Float256Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, Float256Vector.class, float.class, LENGTH,
            this, v,
            (v1, v2) -> ((Float256Vector)v1).bOp(v2, (i, a, b) -> (float)(a - b)));
    }

    @Override
    @ForceInline
    public Float256Vector mul(Vector<Float,Shapes.S256Bit> o) {
        Objects.requireNonNull(o);
        Float256Vector v = (Float256Vector)o;
        return (Float256Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, Float256Vector.class, float.class, LENGTH,
            this, v,
            (v1, v2) -> ((Float256Vector)v1).bOp(v2, (i, a, b) -> (float)(a * b)));
    }


    @Override
    @ForceInline
    public Float256Vector div(Vector<Float,Shapes.S256Bit> o) {
        Objects.requireNonNull(o);
        Float256Vector v = (Float256Vector)o;
        return (Float256Vector) VectorIntrinsics.binaryOp(
            VECTOR_OP_DIV, Float256Vector.class, float.class, LENGTH,
            this, v,
            (v1, v2) -> ((Float256Vector)v1).bOp(v2, (i, a, b) -> (float)(a / b)));
    }


    // Type specific horizontal reductions

    @Override
    @ForceInline
    public float addAll() {
        int bits = (int) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_ADD, Float256Vector.class, float.class, LENGTH,
                                this,
                                v -> {
                                    float r = v.rOp((float) 0, (i, a, b) -> (float) (a + b));
                                    return (long)Float.floatToIntBits(r);
                                });
        return Float.intBitsToFloat(bits);
    }

    @Override
    @ForceInline
    public float mulAll() {
        int bits = (int) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_MUL, Float256Vector.class, float.class, LENGTH,
                                this,
                                v -> {
                                    float r = v.rOp((float) 0, (i, a, b) -> (float) (a * b));
                                    return (long)Float.floatToIntBits(r);
                                });
        return Float.intBitsToFloat(bits);
    }

    // Memory operations

    @Override
    @ForceInline
    public void intoArray(float[] a, int ix) {
        Objects.requireNonNull(a);
        if (VectorIntrinsics.VECTOR_ACCESS_OOB_CHECK) {
            Objects.checkFromIndexSize(ix, LENGTH, a.length);
        }
        VectorIntrinsics.store(Float256Vector.class, float.class, LENGTH,
                               a, ix, this,
                               (arr, idx, v) -> v.forEach((i, a_) -> ((float[])arr)[idx + i] = a_));
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

        Float256Vector that = (Float256Vector) o;
        return Arrays.equals(this.getElements(), that.getElements());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    Float256Mask bTest(Vector<Float, Shapes.S256Bit> o, FBinTest f) {
        float[] vec1 = getElements();
        float[] vec2 = ((Float256Vector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new Float256Mask(bits);
    }

    // Foreach

    @Override
    void forEach(FUnCon f) {
        float[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Float, Shapes.S256Bit> o, FUnCon f) {
        boolean[] mbits = ((Float256Mask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }

    Int256Vector toBits() {
        float[] vec = getElements();
        int[] res = new int[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Float.floatToIntBits(vec[i]);
        }
        return new Int256Vector(res);
    }


    @Override
    public Float256Vector rotateEL(int j) {
        float[] vec = getElements();
        float[] res = new float[length()];
        for (int i = 0; i < length(); i++){
            res[j + i % length()] = vec[i];
        }
        return new Float256Vector(res);
    }

    @Override
    public Float256Vector rotateER(int j) {
        float[] vec = getElements();
        float[] res = new float[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new Float256Vector(res);
    }

    @Override
    public Float256Vector shiftEL(int j) {
        float[] vec = getElements();
        float[] res = new float[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new Float256Vector(res);
    }

    @Override
    public Float256Vector shiftER(int j) {
        float[] vec = getElements();
        float[] res = new float[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new Float256Vector(res);
    }

    @Override
    public Float256Vector shuffle(Vector<Float, Shapes.S256Bit> o, Shuffle<Float, Shapes.S256Bit> s) {
        Float256Vector v = (Float256Vector) o;
        return uOp((i, a) -> {
            float[] vec = this.getElements();
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
    public Float256Vector swizzle(Shuffle<Float, Shapes.S256Bit> s) {
        return uOp((i, a) -> {
            float[] vec = this.getElements();
            int e = s.getElement(i);
            if(e >= 0 && e < length()) {
                return vec[e];
            } else {
                throw new ArrayIndexOutOfBoundsException("Bad reordering for shuffle");
            }
        });
    }

    @Override
    public <F, Z extends Shape> Vector<F, Z> cast(Class<F> type, Z shape) {
        Vector.Species<F,Z> species = Vector.speciesInstance(type, shape);

        // Whichever is larger
        int blen = Math.max(species.bitSize(), bitSize()) / Byte.SIZE;
        ByteBuffer bb = ByteBuffer.allocate(blen);

        int limit = Math.min(species.length(), length());

        float[] vec = getElements();
        if (type == Byte.class) {
            for (int i = 0; i < limit; i++){
                bb.put(i, (byte) vec[i]);
            }
        } else if (type == Short.class) {
            for (int i = 0; i < limit; i++){
                bb.asShortBuffer().put(i, (short) vec[i]);
            }
        } else if (type == Integer.class) {
            for (int i = 0; i < limit; i++){
                bb.asIntBuffer().put(i, (int) vec[i]);
            }
        } else if (type == Long.class){
            for (int i = 0; i < limit; i++){
                bb.asLongBuffer().put(i, (long) vec[i]);
            }
        } else if (type == Float.class){
            for (int i = 0; i < limit; i++){
                bb.asFloatBuffer().put(i, (float) vec[i]);
            }
        } else if (type == Double.class){
            for (int i = 0; i < limit; i++){
                bb.asDoubleBuffer().put(i, (double) vec[i]);
            }
        } else {
            throw new UnsupportedOperationException("Bad lane type for casting.");
        }

        return species.fromByteBuffer(bb);
    }

    // Accessors

    @Override
    public float get(int i) {
        float[] vec = getElements();
        return vec[i];
    }

    @Override
    public Float256Vector with(int i, float e) {
        float[] res = vec.clone();
        res[i] = e;
        return new Float256Vector(res);
    }

    // Mask

    static final class Float256Mask extends AbstractMask<Float, Shapes.S256Bit> {
        static final Float256Mask TRUE_MASK = new Float256Mask(true);
        static final Float256Mask FALSE_MASK = new Float256Mask(false);

        public Float256Mask(boolean[] bits) {
            super(bits);
        }

        public Float256Mask(boolean val) {
            super(val);
        }

        @Override
        Float256Mask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Float256Mask(res);
        }

        @Override
        Float256Mask bOp(Mask<Float, Shapes.S256Bit> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Float256Mask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Float256Mask(res);
        }

        @Override
        public Float256Species species() {
            return SPECIES;
        }

        @Override
        public Float256Vector toVector() {
            float[] res = new float[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = (float) (bits[i] ? -1 : 0);
            }
            return new Float256Vector(res);
        }

        // Unary operations

        //Mask<E, S> not();

        // Binary operations

        @Override
        @ForceInline
        public Float256Mask and(Mask<Float,Shapes.S256Bit> o) {
            Objects.requireNonNull(o);
            Float256Mask m = (Float256Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, Float256Mask.class, int.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a && b));
        }

        @Override
        @ForceInline
        public Float256Mask or(Mask<Float,Shapes.S256Bit> o) {
            Objects.requireNonNull(o);
            Float256Mask m = (Float256Mask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, Float256Mask.class, int.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a && b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, Float256Mask.class, int.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, Float256Mask.class, int.class, LENGTH,
                                         this, trueMask(),
                                         (m1, m2) -> super.allTrue());
        }

        // Helpers

        @ForceInline
        static Float256Mask trueMask() {
            return Float256Mask.trueMask();
        }

        @ForceInline
        static Float256Mask falseMask() {
            return Float256Mask.falseMask();
        }
    }

    // Species

    @Override
    public Float256Species species() {
        return SPECIES;
    }

    static final class Float256Species extends FloatSpecies<Shapes.S256Bit> {
        static final int BIT_SIZE = Shapes.S_256_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Float.SIZE;

        @Override
        public String toString() {
           StringBuilder sb = new StringBuilder("Shape[");
           sb.append(bitSize()).append(" bits, ");
           sb.append(length()).append(" ").append(float.class.getSimpleName()).append("s x ");
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
        public Class<Float> elementType() {
            return Float.class;
        }

        @Override
        public int elementSize() {
            return Float.SIZE;
        }

        @Override
        public Shapes.S256Bit shape() {
            return Shapes.S_256_BIT;
        }

        @Override
        Float256Vector op(FOp f) {
            float[] res = new float[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new Float256Vector(res);
        }

        @Override
        Float256Vector op(Mask<Float, Shapes.S256Bit> o, FOp f) {
            float[] res = new float[length()];
            boolean[] mbits = ((Float256Mask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new Float256Vector(res);
        }

        // Factories

        @Override
        public Float256Mask constantMask(boolean... bits) {
            return new Float256Mask(bits.clone());
        }


        @Override
        @ForceInline
        public Float256Vector zero() {
            return VectorIntrinsics.broadcastCoerced(Float256Vector.class, float.class, LENGTH,
                                                     Float.floatToIntBits(0.0f),
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public Float256Vector broadcast(float e) {
            return VectorIntrinsics.broadcastCoerced(
                Float256Vector.class, float.class, LENGTH,
                Float.floatToIntBits(e),
                ((long bits) -> SPECIES.op(i -> Float.intBitsToFloat((int)bits))));
        }

        @HotSpotIntrinsicCandidate
        @Override
        @ForceInline
        public Float256Mask trueMask() {
            return VectorIntrinsics.broadcastCoerced(Float256Mask.class, int.class, LENGTH,
                                                     (int)-1,
                                                     (z -> Float256Mask.TRUE_MASK));
        }

        @HotSpotIntrinsicCandidate
        @Override
        @ForceInline
        public Float256Mask falseMask() {
            return VectorIntrinsics.broadcastCoerced(Float256Mask.class, int.class, LENGTH,
                                                     0,
                                                     (z -> Float256Mask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public Float256Vector fromArray(float[] a, int ix) {
            Objects.requireNonNull(a);
            if (VectorIntrinsics.VECTOR_ACCESS_OOB_CHECK) {
                Objects.checkFromIndexSize(ix, LENGTH, a.length);
            }
            return (Float256Vector) VectorIntrinsics.load(Float256Vector.class, float.class, LENGTH,
                                                        a, ix,
                                                        (arr, idx) -> super.fromArray((float[]) arr, idx));
        }
    }
}
