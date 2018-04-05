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

import jdk.internal.vm.annotation.ForceInline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("cast")
public abstract class IntVector<S extends Vector.Shape> extends Vector<Integer,S> {

    IntVector() {}

    // Unary operator

    interface FUnOp {
        int apply(int i, int a);
    }

    abstract IntVector<S> uOp(FUnOp f);

    abstract IntVector<S> uOp(Mask<Integer, S> m, FUnOp f);

    // Binary operator

    interface FBinOp {
        int apply(int i, int a, int b);
    }

    abstract IntVector<S> bOp(Vector<Integer,S> o, FBinOp f);

    abstract IntVector<S> bOp(Vector<Integer,S> o, Mask<Integer, S> m, FBinOp f);

    // Trinary operator

    interface FTriOp {
        int apply(int i, int a, int b, int c);
    }

    abstract IntVector<S> tOp(Vector<Integer,S> o1, Vector<Integer,S> o2, FTriOp f);

    abstract IntVector<S> tOp(Vector<Integer,S> o1, Vector<Integer,S> o2, Mask<Integer, S> m, FTriOp f);

    // Reduction operator

    abstract int rOp(int v, FBinOp f);

    // Binary test

    interface FBinTest {
        boolean apply(int i, int a, int b);
    }

    abstract Mask<Integer, S> bTest(Vector<Integer,S> o, FBinTest f);

    // Foreach

    interface FUnCon {
        void apply(int i, int a);
    }

    abstract void forEach(FUnCon f);

    abstract void forEach(Mask<Integer, S> m, FUnCon f);

    //

    @Override
    public IntVector<S> add(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a + b));
    }

    /**
     * Adds this vector to the result of broadcasting an input scalar.
     * <p>
     * This is a vector binary operation where the primitive addition operation
     * ({@code +}) is applied to lane elements.
     *
     * @param b the input scalar
     * @return the result of adding this vector to the broadcast of an input
     * scalar
     */
    public abstract IntVector<S> add(int b);

    @Override
    public IntVector<S> add(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a + b));
    }

    /**
     * Adds this vector to the result of broadcasting an input scalar,
     * selecting lane elements controlled by a mask.
     * <p>
     * This is a vector binary operation where the primitive addition operation
     * ({@code +}) is applied to lane elements.
     *
     * @param b the input vector
     * @param m the mask controlling lane selection
     * @return the result of adding this vector to the broadcast of an input
     * scalar
     */
    public abstract IntVector<S> add(int b, Mask<Integer, S> m);

    @Override
    public IntVector<S> addSaturate(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) ((a >= Integer.MAX_VALUE || Integer.MAX_VALUE - b > a) ? Integer.MAX_VALUE : a + b));
    }

    public abstract IntVector<S> addSaturate(int o);

    @Override
    public IntVector<S> addSaturate(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) ((a >= Integer.MAX_VALUE || Integer.MAX_VALUE - b > a) ? Integer.MAX_VALUE : a + b));
    }

    public abstract IntVector<S> addSaturate(int o, Mask<Integer, S> m);

    @Override
    public IntVector<S> sub(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a - b));
    }

    public abstract IntVector<S> sub(int o);

    @Override
    public IntVector<S> sub(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a - b));
    }

    public abstract IntVector<S> sub(int o, Mask<Integer, S> m);

    @Override
    public IntVector<S> subSaturate(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) ((a >= Integer.MIN_VALUE || Integer.MIN_VALUE + b > a) ? Integer.MAX_VALUE : a - b));
    }

    public abstract IntVector<S> subSaturate(int o);

    @Override
    public IntVector<S> subSaturate(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) ((a >= Integer.MIN_VALUE || Integer.MIN_VALUE + b > a) ? Integer.MAX_VALUE : a - b));
    }

    public abstract IntVector<S> subSaturate(int o, Mask<Integer, S> m);

    @Override
    public IntVector<S> mul(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a * b));
    }

    public abstract IntVector<S> mul(int o);

    @Override
    public IntVector<S> mul(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a * b));
    }

    public abstract IntVector<S> mul(int o, Mask<Integer, S> m);

    @Override
    public IntVector<S> neg() {
        return uOp((i, a) -> (int) (-a));
    }

    @Override
    public IntVector<S> neg(Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) (-a));
    }

    @Override
    public IntVector<S> abs() {
        return uOp((i, a) -> (int) Math.abs(a));
    }

    @Override
    public IntVector<S> abs(Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) Math.abs(a));
    }

    @Override
    public IntVector<S> min(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (a <= b) ? a : b);
    }

    public abstract IntVector<S> min(int o);

    @Override
    public IntVector<S> max(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (a >= b) ? a : b);
    }

    public abstract IntVector<S> max(int o);

    @Override
    public Mask<Integer, S> equal(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a == b);
    }

    public abstract Mask<Integer, S> equal(int o);

    @Override
    public Mask<Integer, S> notEqual(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a != b);
    }

    public abstract Mask<Integer, S> notEqual(int o);

    @Override
    public Mask<Integer, S> lessThan(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a < b);
    }

    public abstract Mask<Integer, S> lessThan(int o);

    @Override
    public Mask<Integer, S> lessThanEq(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a <= b);
    }

    public abstract Mask<Integer, S> lessThanEq(int o);

    @Override
    public Mask<Integer, S> greaterThan(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a > b);
    }

    public abstract Mask<Integer, S> greaterThan(int o);

    @Override
    public Mask<Integer, S> greaterThanEq(Vector<Integer,S> o) {
        return bTest(o, (i, a, b) -> a >= b);
    }

    public abstract Mask<Integer, S> greaterThanEq(int o);

    @Override
    public IntVector<S> blend(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, (i, a, b) -> m.getElement(i) ? b : a);
    }

    public abstract IntVector<S> blend(int o, Mask<Integer, S> m);

    @Override
    public abstract IntVector<S> shuffle(Vector<Integer,S> o, Shuffle<Integer, S> m);

    @Override
    public abstract IntVector<S> swizzle(Shuffle<Integer, S> m);

    @Override
    @ForceInline
    public <T extends Shape> IntVector<T> resize(Species<Integer, T> species) {
        return (IntVector<T>) species.reshape(this);
    }

    @Override
    public abstract IntVector<S> rotateEL(int i);

    @Override
    public abstract IntVector<S> rotateER(int i);

    @Override
    public abstract IntVector<S> shiftEL(int i);

    @Override
    public abstract IntVector<S> shiftER(int i);


    public IntVector<S> and(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a & b));
    }

    public abstract IntVector<S> and(int o);

    public IntVector<S> and(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a & b));
    }

    public abstract IntVector<S> and(int o, Mask<Integer, S> m);

    public IntVector<S> or(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a | b));
    }

    public abstract IntVector<S> or(int o);

    public IntVector<S> or(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a | b));
    }

    public abstract IntVector<S> or(int o, Mask<Integer, S> m);

    public IntVector<S> xor(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a ^ b));
    }

    public abstract IntVector<S> xor(int o);

    public IntVector<S> xor(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a ^ b));
    }

    public abstract IntVector<S> xor(int o, Mask<Integer, S> m);

    public IntVector<S> not() {
        return uOp((i, a) -> (int) (~a));
    }

    public IntVector<S> not(Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) (~a));
    }

    // logical shift left
    public IntVector<S> shiftL(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a << b));
    }

    public IntVector<S> shiftL(int s) {
        return uOp((i, a) -> (int) (a << s));
    }

    public IntVector<S> shiftL(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a << b));
    }

    public IntVector<S> shiftL(int s, Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) (a << s));
    }

    // logical, or unsigned, shift right
    public IntVector<S> shiftR(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a >>> b));
    }

    public IntVector<S> shiftR(int s) {
        return uOp((i, a) -> (int) (a >>> s));
    }

    public IntVector<S> shiftR(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a >>> b));
    }

    public IntVector<S> shiftR(int s, Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) (a >>> s));
    }

    // arithmetic, or signed, shift right
    public IntVector<S> ashiftR(Vector<Integer,S> o) {
        return bOp(o, (i, a, b) -> (int) (a >> b));
    }

    public IntVector<S> aShiftR(int s) {
        return uOp((i, a) -> (int) (a >> s));
    }

    public IntVector<S> ashiftR(Vector<Integer,S> o, Mask<Integer, S> m) {
        return bOp(o, m, (i, a, b) -> (int) (a >> b));
    }

    public IntVector<S> aShiftR(int s, Mask<Integer, S> m) {
        return uOp(m, (i, a) -> (int) (a >> s));
    }

    public IntVector<S> rotateL(int j) {
        return uOp((i, a) -> (int) Integer.rotateLeft(a, j));
    }

    public IntVector<S> rotateR(int j) {
        return uOp((i, a) -> (int) Integer.rotateRight(a, j));
    }

    @Override
    public void intoByteArray(byte[] a, int ix) {
        ByteBuffer bb = ByteBuffer.wrap(a, ix, a.length - ix);
        intoByteBuffer(bb);
    }

    @Override
    public void intoByteArray(byte[] a, int ix, Mask<Integer, S> m) {
        ByteBuffer bb = ByteBuffer.wrap(a, ix, a.length - ix);
        intoByteBuffer(bb, m);
    }

    @Override
    public void intoByteBuffer(ByteBuffer bb) {
        IntBuffer fb = bb.asIntBuffer();
        forEach((i, a) -> fb.put(a));
    }

    @Override
    public void intoByteBuffer(ByteBuffer bb, Mask<Integer, S> m) {
        IntBuffer fb = bb.asIntBuffer();
        forEach((i, a) -> {
            if (m.getElement(i))
                fb.put(a);
            else
                fb.position(fb.position() + 1);
        });
    }

    @Override
    public void intoByteBuffer(ByteBuffer bb, int ix) {
        bb = bb.duplicate().position(ix);
        IntBuffer fb = bb.asIntBuffer();
        forEach((i, a) -> fb.put(i, a));
    }

    @Override
    public void intoByteBuffer(ByteBuffer bb, int ix, Mask<Integer, S> m) {
        bb = bb.duplicate().position(ix);
        IntBuffer fb = bb.asIntBuffer();
        forEach(m, (i, a) -> fb.put(i, a));
    }


    // Type specific horizontal reductions

    /**
     * Sums all lane elements of this vector.
     * <p>
     * This is an associative vector reduction operation where the addition
     * operation ({@code +}) is applied to lane elements, and the identity value
     * is {@code 0}.
     *
     * @return the sum of all the lane elements of this vector
     */
    public int addAll() {
        return rOp((int) 0, (i, a, b) -> (int) (a + b));
    }

    public int subAll() {
        return rOp((int) 0, (i, a, b) -> (int) (a - b));
    }

    public int mulAll() {
        return rOp((int) 1, (i, a, b) -> (int) (a * b));
    }

    public int minAll() {
        return rOp(Integer.MAX_VALUE, (i, a, b) -> a > b ? b : a);
    }

    public int maxAll() {
        return rOp(Integer.MIN_VALUE, (i, a, b) -> a < b ? b : a);
    }

    public int orAll() {
        return rOp((int) 0, (i, a, b) -> (int) (a | b));
    }

    public int andAll() {
        return rOp((int) -1, (i, a, b) -> (int) (a & b));
    }

    public int xorAll() {
        return rOp((int) 0, (i, a, b) -> (int) (a ^ b));
    }

    // Type specific accessors

    /**
     * Gets the lane element at lane index {@code i}
     *
     * @param i the lane index
     * @return the lane element at lane index {@code i}
     */
    public abstract int get(int i);

    /**
     * Replaces the lane element of this vector at lane index {@code i} with
     * value {@code e}.
     * <p>
     * This is a cross-lane operation.
     * @@@ specify as blend(species().broadcast(e), mask)
     *
     * @param i the lane index of the lane element to be replaced
     * @param e the value to be placed
     * @return the result of replacing the lane element of this vector at lane
     * index {@code i} with value {@code e}.
     */
    public abstract IntVector<S> with(int i, int e);

    // Type specific extractors

    @ForceInline
    public int[] toArray() {
        int[] a = new int[species().length()];
        intoArray(a, 0);
        return a;
    }

    public void intoArray(int[] a, int ax) {
        forEach((i, a_) -> a[ax + i] = a_);
    }

    public void intoArray(int[] a, int ax, Mask<Integer, S> m) {
        forEach(m, (i, a_) -> a[ax + i] = a_);
    }

    public void intoArray(int[] a, int ax, int[] indexMap, int mx) {
        forEach((i, a_) -> a[ax + indexMap[mx + i]] = a_);
    }

    public void intoArray(int[] a, int ax, Mask<Integer, S> m, int[] indexMap, int mx) {
        forEach(m, (i, a_) -> a[ax + indexMap[mx + i]] = a_);
    }

    // Species

    @Override
    public abstract IntSpecies<S> species();

    public static abstract class IntSpecies<S extends Vector.Shape> extends Vector.Species<Integer, S> {
        interface FOp {
            int apply(int i);
        }

        abstract IntVector<S> op(FOp f);

        abstract IntVector<S> op(Mask<Integer, S> m, FOp f);

        // Factories

        @Override
        public IntVector<S> zero() {
            return op(i -> 0);
        }

        public IntVector<S> broadcast(int e) {
            return op(i -> e);
        }

        public IntVector<S> single(int e) {
            return op(i -> i == 0 ? e : (int) 0);
        }

        public IntVector<S> random() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return op(i -> (int) r.nextInt());
        }

        public IntVector<S> scalars(int... es) {
            return op(i -> es[i]);
        }

        /**
         * Loads a vector from an array starting at offset.
         * <p>
         * For each vector lane, where {@code N} is the vector lane index, the
         * array element at index {@code i + N} is placed into the
         * resulting vector at lane index {@code N}.
         *
         * @param a the array
         * @param i the offset into the array
         * @return the vector loaded from an array
         * @throws IndexOutOfBoundsException if {@code i < 0} or
         * {@code i < a.length - this.length()}
         */
        public IntVector<S> fromArray(int[] a, int i) {
            return op(n -> a[i + n]);
        }

        /**
         * Loads a vector from an array starting at offset.
         * <p>
         * For each vector lane, where {@code N} is the vector lane index,
         * if the mask lane at index {@code N} is set then the array element at
         * index {@code i + N} is placed into the resulting vector at lane index
         * {@code N}, otherwise the default element value is placed into the
         * resulting vector at lane index {@code N}.
         *
         * @param a the array
         * @param i the offset into the array
         * @param m the mask
         * @return the vector loaded from an array
         * @throws IndexOutOfBoundsException if {@code i < 0} or
         * {@code i < a.length - this.length()}
         */
        public IntVector<S> fromArray(int[] a, int i, Mask<Integer, S> m) {
            return op(m, n -> a[i + n]);
        }

        /**
         * Loads a vector from an array using indexes obtained from an index
         * map.
         * <p>
         * For each vector lane, where {@code N} is the vector lane index, the
         * array element at index {@code i + indexMap[j + N]} is placed into the
         * resulting vector at lane index {@code N}.
         *
         * @param a the array
         * @param i the offset into the array, may be negative if relative
         * indexes in the index map compensate to produce a value within the
         * array bounds
         * @param indexMap the index map
         * @param j the offset into the index map
         * @return the vector loaded from an array
         * @throws IndexOutOfBoundsException if {@code j < 0}, or
         * {@code j < indexMap.length - this.length()}, or for any vector
         * lane index {@code N} the result of {@code i + indexMap[j + N]} is
         * {@code < 0} or {@code >= a.length}
         */
        public IntVector<S> fromArray(int[] a, int i, int[] indexMap, int j) {
            return op(n -> a[i + indexMap[j + n]]);
        }

        /**
         * Loads a vector from an array using indexes obtained from an index
         * map.
         * <p>
         * For each vector lane, where {@code N} is the vector lane index,
         * if the mask lane at index {@code N} is set then the array element at
         * index {@code i + indexMap[j + N]} is placed into the resulting vector
         * at lane index {@code N}, otherwise the default element value is
         * placed into the resulting vector at lane index {@code N}.
         *
         * @param a the array
         * @param i the offset into the array, may be negative if relative
         * indexes in the index map compensate to produce a value within the
         * array bounds
         * @param m the mask
         * @param indexMap the index map
         * @param j the offset into the index map
         * @return the vector loaded from an array
         * @throws IndexOutOfBoundsException if {@code j < 0}, or
         * {@code j < indexMap.length - this.length()}, or for any vector
         * lane index {@code N} the result of {@code i + indexMap[j + N]} is
         * {@code < 0} or {@code >= a.length}
         */
        public IntVector<S> fromArray(int[] a, int i, Mask<Integer, S> m, int[] indexMap, int j) {
            return op(m, n -> a[i + indexMap[j + n]]);
        }

        @Override
        public IntVector<S> fromByteArray(byte[] a, int ix) {
            ByteBuffer bb = ByteBuffer.wrap(a, ix, a.length - ix);
            return fromByteBuffer(bb);
        }

        @Override
        public IntVector<S> fromByteArray(byte[] a, int ix, Mask<Integer, S> m) {
            ByteBuffer bb = ByteBuffer.wrap(a, ix, a.length - ix);
            return fromByteBuffer(bb, m);
        }

        @Override
        public IntVector<S> fromByteBuffer(ByteBuffer bb) {
            IntBuffer fb = bb.asIntBuffer();
            return op(i -> fb.get());
        }

        @Override
        public IntVector<S> fromByteBuffer(ByteBuffer bb, Mask<Integer, S> m) {
            IntBuffer fb = bb.asIntBuffer();
            return op(i -> {
                if(m.getElement(i))
                    return fb.get();
                else {
                    fb.position(fb.position() + 1);
                    return (int) 0;
                }
            });
        }

        @Override
        public IntVector<S> fromByteBuffer(ByteBuffer bb, int ix) {
            bb = bb.duplicate().position(ix);
            IntBuffer fb = bb.asIntBuffer();
            return op(i -> fb.get(i));
        }

        @Override
        public IntVector<S> fromByteBuffer(ByteBuffer bb, int ix, Mask<Integer, S> m) {
            bb = bb.duplicate().position(ix);
            IntBuffer fb = bb.asIntBuffer();
            return op(m, i -> fb.get(i));
        }

        @Override
        public <F, T extends Shape> IntVector<S> reshape(Vector<F, T> o) {
            int blen = Math.max(o.species().bitSize(), bitSize()) / Byte.SIZE;
            ByteBuffer bb = ByteBuffer.allocate(blen).order(ByteOrder.nativeOrder());
            o.intoByteBuffer(bb, 0);
            return fromByteBuffer(bb, 0);
        }

        @Override
        @ForceInline
        public <F> IntVector<S> rebracket(Vector<F, S> o) {
            return reshape(o);
        }

        @Override
        @ForceInline
        public <T extends Shape> IntVector<S> resize(Vector<Integer, T> o) {
            return reshape(o);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <F, T extends Shape> IntVector<S> cast(Vector<F, T> v) {
            // Allocate array of required size
            int[] a = new int[length()];

            Class<?> vtype = v.species().elementType();
            int limit = Math.min(v.species().length(), length());
            if (vtype == Byte.class) {
                ByteVector<T> tv = (ByteVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == Short.class) {
                ShortVector<T> tv = (ShortVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == Integer.class) {
                IntVector<T> tv = (IntVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == Long.class){
                LongVector<T> tv = (LongVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == Float.class){
                FloatVector<T> tv = (FloatVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else if (vtype == Double.class){
                DoubleVector<T> tv = (DoubleVector<T>)v;
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) tv.get(i);
                }
            } else {
                throw new UnsupportedOperationException("Bad lane type for casting.");
            }

            return scalars(a);
        }

    }
}
