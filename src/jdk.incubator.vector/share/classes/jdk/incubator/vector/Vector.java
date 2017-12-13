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

import jdk.internal.HotSpotIntrinsicCandidate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public interface Vector<E, S extends Vector.Shape<Vector<?, ?>>> {

    Species<E, S> species();

    default Class<E> elementType() { return species().elementType(); }

    default S shape() { return species().shape(); }

    @HotSpotIntrinsicCandidate
    default int length() { return species().length(); }

    default int bitSize() { return species().bitSize(); }

    //Arithmetic
    Vector<E, S> add(Vector<E, S> o);

    Vector<E, S> add(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> addSaturate(Vector<E, S> o);

    Vector<E, S> addSaturate(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> sub(Vector<E, S> o);

    Vector<E, S> sub(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> subSaturate(Vector<E, S> o);

    Vector<E, S> subSaturate(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> mul(Vector<E, S> o);

    Vector<E, S> mul(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> div(Vector<E, S> o);

    Vector<E, S> div(Vector<E, S> o, Mask<E, S> m);

    Vector<E, S> neg();

    Vector<E, S> neg(Mask<E, S> m);

    //Maths from java.math
    Vector<E, S> abs();

    Vector<E, S> abs(Mask<E, S> m);

    Vector<E, S> min(Vector<E, S> o);

    Vector<E, S> max(Vector<E, S> o);

    //TODO: Parity

    //Comparisons
    //For now these are projected into the same element type.  False is the element 0.  True is otherwise.
    //TODO: N.B. Floating point NaN behaviors?
    //TODO: Check the JLS
    Mask<E, S> equal(Vector<E, S> o);

    Mask<E, S> notEqual(Vector<E, S> o);

    Mask<E, S> lessThan(Vector<E, S> o);

    Mask<E, S> lessThanEq(Vector<E, S> o);

    Mask<E, S> greaterThan(Vector<E, S> o);

    Mask<E, S> greaterThanEq(Vector<E, S> o);

    //Elemental shifting
    Vector<E, S> rotateEL(int i); //Rotate elements left

    Vector<E, S> rotateER(int i); //Rotate elements right

    Vector<E, S> shiftEL(int i); //shift elements left

    Vector<E, S> shiftER(int i); //shift elements right

    //Blend, etc.
    Vector<E, S> blend(Vector<E, S> o, Mask<E, S> m);

    //Shuffles
    Vector<E, S> shuffle(Vector<E, S> o, Shuffle<E, S> s); //TODO: Example

    Vector<E, S> swizzle(Shuffle<E, S> s);


    //Conversions
    default <F, Z extends Shape<Vector<?, ?>>> Vector<F, Z> reshape(Class<F> type, Z shape) {
        Vector.Species<F, Z> species = speciesInstance(type, shape);
        int blen = Math.max(species.bitSize(), bitSize());
        ByteBuffer bb = ByteBuffer.allocate(blen).order(ByteOrder.nativeOrder());
        intoByteBuffer(bb, 0);
        return species.fromByteBuffer(bb, 0);
    }

    //Costless vector cast.  Bit-wise contents preserved
    default <F> Vector<F, S> rebracket(Class<F> type) { return reshape(type, shape());}

    //Size-fixed semantic cast
    <F, Z extends Shape<Vector<?, ?>>>
    Vector<F, Z> cast(Class<F> type, Z shape);

    default <Z extends Shape<Vector<?, ?>>>
    Vector<E, Z> resize(Z shape) {
        return reshape(elementType(), shape);
    }

    default <F> Vector<F, S> cast(Class<F> type) {
        return cast(type, shape());
    }


    //Array stores

    void intoByteArray(byte[] bs, int ix);

    void intoByteArray(byte[] bs, int ix, Mask<E, S> m);

    void intoByteBuffer(ByteBuffer bb);

    void intoByteBuffer(ByteBuffer bb, Mask<E, S> m);

    void intoByteBuffer(ByteBuffer bb, int ix);

    void intoByteBuffer(ByteBuffer bb, int ix, Mask<E, S> m);


    interface Species<E, S extends Vector.Shape<Vector<?, ?>>> {
        Class<E> elementType();

        int elementSize();

        S shape();

        default int length() { return shape().length(this); }

        default int bitSize() { return shape().bitSize(); }

        // Factory

        Vector<E, S> zero();

        Vector<E, S> fromByteArray(byte[] bs, int ix);

        Vector<E, S> fromByteArray(byte[] bs, int ix, Mask<E, S> m);

        Vector<E, S> fromByteBuffer(ByteBuffer bb);

        Vector<E, S> fromByteBuffer(ByteBuffer bb, Mask<E, S> m);

        Vector<E, S> fromByteBuffer(ByteBuffer bb, int ix);

        Vector<E, S> fromByteBuffer(ByteBuffer bb, int ix, Mask<E, S> m);

        //Mask and shuffle constructions
        default Mask<E, S> constantMask(boolean... bits) {
            return new GenericMask<>(this, bits);
        }

        default Shuffle<E, S> constantShuffle(int... ixs) {
            return new GenericShuffle<>(this, ixs);
        }
    }

    interface Shape<V extends Vector<?, ?> /*extends Vector<?, Shape<V>>*/> {
        int bitSize();  // usually 64, 128, 256, etc.

        default int length(Species<?, ?> s) { return bitSize() / s.elementSize(); }  // usually bitSize / sizeof(s.elementType)
    }

    interface Mask<E, S extends Shape<Vector<?, ?>>> {
        default int length() { return species().length(); }

        long toLong();

        boolean[] toArray();

        boolean anyTrue();

        boolean allTrue();

        //TODO: counting trues? LZ count
        Species<E, S> species();

        <Z> Vector<Z, S> toVector(Class<Z> e);

        default boolean getElement(int i) { return i < 64 ? ((toLong() >>> i) & 1) != 0 : toArray()[i]; }

        <F, Z extends Vector.Shape<Vector<?, ?>>>
        Mask<F, Z> reshape(Class<F> type, Z shape);

        default <Z>
        Mask<Z, S> rebracket(Class<Z> e) {
            return reshape(e, species().shape());
        }

        default <Z extends Vector.Shape<Vector<?, ?>>> Mask<E, Z>
        resize(Z shape) {
            return reshape(species().elementType(), shape);
        }

    }

    interface Shuffle<E, S extends Shape<Vector<?, ?>>> {
        default int length() {return getSpecies().length(); }

        int[] toArray();

        Species<E, S> getSpecies();

        Vector<Integer, S> toVector();

        <E> Vector<E, S> toVector(Class<E> type);

        default int getElement(int i) { return toArray()[i]; }
    }

    @SuppressWarnings("unchecked")
    static <E, S extends Vector.Shape<Vector<?, ?>>> Vector.Species<E, S> speciesInstance(Class<E> c, S s) {
        Vector.Species<E, S> res = null;

        //Float
        if (c == Float.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Float64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Float128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Float256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Float512Vector.SPECIES;
            //Double
        }
        else if (c == Double.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Double64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Double128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Double256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Double512Vector.SPECIES;
            //Byte
        }
        else if (c == Byte.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Byte64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Byte128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Byte256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Byte512Vector.SPECIES;
            //Short
        }
        else if (c == Short.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Short64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Short128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Short256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Short512Vector.SPECIES;
            //Int
        }
        else if (c == Integer.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Int64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Int128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Int256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Int512Vector.SPECIES;
            //Long
        }
        else if (c == Long.class) {
            if (s == Shapes.S_64_BIT)
                res = (Vector.Species<E, S>) Long64Vector.SPECIES;
            else if (s == Shapes.S_128_BIT)
                res = (Vector.Species<E, S>) Long128Vector.SPECIES;
            else if (s == Shapes.S_256_BIT)
                res = (Vector.Species<E, S>) Long256Vector.SPECIES;
            else if (s == Shapes.S_512_BIT)
                res = (Vector.Species<E, S>) Long512Vector.SPECIES;
        }
        return res;
    }
}
