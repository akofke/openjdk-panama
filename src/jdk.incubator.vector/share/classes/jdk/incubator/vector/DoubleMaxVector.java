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
import java.nio.DoubleBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import static jdk.incubator.vector.VectorIntrinsics.*;

@SuppressWarnings("cast")
final class DoubleMaxVector extends DoubleVector {
    static final DoubleMaxSpecies SPECIES = new DoubleMaxSpecies();

    static final DoubleMaxVector ZERO = new DoubleMaxVector();

    static final int LENGTH = SPECIES.length();

    // Index vector species
    private static final IntVector.IntSpecies INDEX_SPEC;
    static {
        int bitSize = Vector.bitSizeForVectorLength(int.class, LENGTH);
        Vector.Shape shape = Vector.shapeForVectorBitSize(bitSize);
        INDEX_SPEC = (IntVector.IntSpecies) Vector.species(int.class, shape);
    }

    private final double[] vec; // Don't access directly, use getElements() instead.

    private double[] getElements() {
        return VectorIntrinsics.maybeRebox(this).vec;
    }

    DoubleMaxVector() {
        vec = new double[SPECIES.length()];
    }

    DoubleMaxVector(double[] v) {
        vec = v;
    }

    @Override
    public int length() { return LENGTH; }

    // Unary operator

    @Override
    DoubleMaxVector uOp(FUnOp f) {
        double[] vec = getElements();
        double[] res = new double[length()];
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return new DoubleMaxVector(res);
    }

    @Override
    DoubleMaxVector uOp(Mask<Double> o, FUnOp f) {
        double[] vec = getElements();
        double[] res = new double[length()];
        boolean[] mbits = ((DoubleMaxMask)o).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return new DoubleMaxVector(res);
    }

    // Binary operator

    @Override
    DoubleMaxVector bOp(Vector<Double> o, FBinOp f) {
        double[] res = new double[length()];
        double[] vec1 = this.getElements();
        double[] vec2 = ((DoubleMaxVector)o).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new DoubleMaxVector(res);
    }

    @Override
    DoubleMaxVector bOp(Vector<Double> o1, Mask<Double> o2, FBinOp f) {
        double[] res = new double[length()];
        double[] vec1 = this.getElements();
        double[] vec2 = ((DoubleMaxVector)o1).getElements();
        boolean[] mbits = ((DoubleMaxMask)o2).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return new DoubleMaxVector(res);
    }

    // Trinary operator

    @Override
    DoubleMaxVector tOp(Vector<Double> o1, Vector<Double> o2, FTriOp f) {
        double[] res = new double[length()];
        double[] vec1 = this.getElements();
        double[] vec2 = ((DoubleMaxVector)o1).getElements();
        double[] vec3 = ((DoubleMaxVector)o2).getElements();
        for (int i = 0; i < length(); i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return new DoubleMaxVector(res);
    }

    @Override
    DoubleMaxVector tOp(Vector<Double> o1, Vector<Double> o2, Mask<Double> o3, FTriOp f) {
        double[] res = new double[length()];
        double[] vec1 = getElements();
        double[] vec2 = ((DoubleMaxVector)o1).getElements();
        double[] vec3 = ((DoubleMaxVector)o2).getElements();
        boolean[] mbits = ((DoubleMaxMask)o3).getBits();
        for (int i = 0; i < length(); i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return new DoubleMaxVector(res);
    }

    @Override
    double rOp(double v, FBinOp f) {
        double[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    @Override
    @ForceInline
    public <F> Vector<F> cast(Species<F> s) {
        Objects.requireNonNull(s);
        if (s.length() != LENGTH)
            throw new IllegalArgumentException("Vector length this species length differ");

        return VectorIntrinsics.cast(
            DoubleMaxVector.class,
            double.class, LENGTH,
            s.vectorType(),
            s.elementType(), LENGTH,
            this, s,
            (species, vector) -> vector.castDefault(species)
        );
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private <F> Vector<F> castDefault(Species<F> s) {
        int limit = s.length();

        Class<?> stype = s.elementType();
        if (stype == byte.class) {
            byte[] a = new byte[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (byte) this.get(i);
            }
            return (Vector) ((ByteVector.ByteSpecies)s).fromArray(a, 0);
        } else if (stype == short.class) {
            short[] a = new short[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (short) this.get(i);
            }
            return (Vector) ((ShortVector.ShortSpecies)s).fromArray(a, 0);
        } else if (stype == int.class) {
            int[] a = new int[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (int) this.get(i);
            }
            return (Vector) ((IntVector.IntSpecies)s).fromArray(a, 0);
        } else if (stype == long.class) {
            long[] a = new long[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (long) this.get(i);
            }
            return (Vector) ((LongVector.LongSpecies)s).fromArray(a, 0);
        } else if (stype == float.class) {
            float[] a = new float[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (float) this.get(i);
            }
            return (Vector) ((FloatVector.FloatSpecies)s).fromArray(a, 0);
        } else if (stype == double.class) {
            double[] a = new double[limit];
            for (int i = 0; i < limit; i++) {
                a[i] = (double) this.get(i);
            }
            return (Vector) ((DoubleVector.DoubleSpecies)s).fromArray(a, 0);
        } else {
            throw new UnsupportedOperationException("Bad lane type for casting.");
        }
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public <F> Vector<F> reinterpret(Species<F> s) {
        Objects.requireNonNull(s);

        if(s.elementType().equals(double.class)) {
            return (Vector<F>) reshape((Species<Double>)s);
        }
        if(s.bitSize() == bitSize()) {
            return reinterpretType(s);
        }

        return defaultReinterpret(s);
    }

    @ForceInline
    private <F> Vector<F> reinterpretType(Species<F> s) {
        Objects.requireNonNull(s);

        Class<?> stype = s.elementType();
        if (stype == byte.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                ByteMaxVector.class,
                byte.class, ByteMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else if (stype == short.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                ShortMaxVector.class,
                short.class, ShortMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else if (stype == int.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                IntMaxVector.class,
                int.class, IntMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else if (stype == long.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                LongMaxVector.class,
                long.class, LongMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else if (stype == float.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                FloatMaxVector.class,
                float.class, FloatMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else if (stype == double.class) {
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                DoubleMaxVector.class,
                double.class, DoubleMaxVector.LENGTH,
                this, s,
                (species, vector) -> vector.defaultReinterpret(species)
            );
        } else {
            throw new UnsupportedOperationException("Bad lane type for casting.");
        }
    }

    @Override
    @ForceInline
    public DoubleVector reshape(Species<Double> s) {
        Objects.requireNonNull(s);
        if (s.bitSize() == 64 && (s instanceof Double64Vector.Double64Species)) {
            Double64Vector.Double64Species ts = (Double64Vector.Double64Species)s;
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                Double64Vector.class,
                double.class, Double64Vector.LENGTH,
                this, ts,
                (species, vector) -> (DoubleVector) vector.defaultReinterpret(species)
            );
        } else if (s.bitSize() == 128 && (s instanceof Double128Vector.Double128Species)) {
            Double128Vector.Double128Species ts = (Double128Vector.Double128Species)s;
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                Double128Vector.class,
                double.class, Double128Vector.LENGTH,
                this, ts,
                (species, vector) -> (DoubleVector) vector.defaultReinterpret(species)
            );
        } else if (s.bitSize() == 256 && (s instanceof Double256Vector.Double256Species)) {
            Double256Vector.Double256Species ts = (Double256Vector.Double256Species)s;
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                Double256Vector.class,
                double.class, Double256Vector.LENGTH,
                this, ts,
                (species, vector) -> (DoubleVector) vector.defaultReinterpret(species)
            );
        } else if (s.bitSize() == 512 && (s instanceof Double512Vector.Double512Species)) {
            Double512Vector.Double512Species ts = (Double512Vector.Double512Species)s;
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                Double512Vector.class,
                double.class, Double512Vector.LENGTH,
                this, ts,
                (species, vector) -> (DoubleVector) vector.defaultReinterpret(species)
            );
        } else if ((s.bitSize() > 0) && (s.bitSize() <= 2048)
                && (s.bitSize() % 128 == 0) && (s instanceof DoubleMaxVector.DoubleMaxSpecies)) {
            DoubleMaxVector.DoubleMaxSpecies ts = (DoubleMaxVector.DoubleMaxSpecies)s;
            return VectorIntrinsics.reinterpret(
                DoubleMaxVector.class,
                double.class, LENGTH,
                DoubleMaxVector.class,
                double.class, DoubleMaxVector.LENGTH,
                this, ts,
                (species, vector) -> (DoubleVector) vector.defaultReinterpret(species)
            );
        } else {
            throw new InternalError("Unimplemented size");
        }
    }

    // Binary operations with scalars

    @Override
    @ForceInline
    public DoubleVector add(double o) {
        return add(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector add(double o, Mask<Double> m) {
        return add(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector sub(double o) {
        return sub(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector sub(double o, Mask<Double> m) {
        return sub(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector mul(double o) {
        return mul(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector mul(double o, Mask<Double> m) {
        return mul(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector min(double o) {
        return min(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector max(double o) {
        return max(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> equal(double o) {
        return equal(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> notEqual(double o) {
        return notEqual(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> lessThan(double o) {
        return lessThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> lessThanEq(double o) {
        return lessThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> greaterThan(double o) {
        return greaterThan(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public Mask<Double> greaterThanEq(double o) {
        return greaterThanEq(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector blend(double o, Mask<Double> m) {
        return blend(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector div(double o) {
        return div(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector div(double o, Mask<Double> m) {
        return div(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector div(Vector<Double> v, Mask<Double> m) {
        return blend(div(v), m);
    }

    @Override
    @ForceInline
    public DoubleVector atan2(double o) {
        return atan2(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector atan2(double o, Mask<Double> m) {
        return atan2(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector pow(double o) {
        return pow(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector pow(double o, Mask<Double> m) {
        return pow(SPECIES.broadcast(o), m);
    }

    @Override
    @ForceInline
    public DoubleVector fma(double o1, double o2) {
        return fma(SPECIES.broadcast(o1), SPECIES.broadcast(o2));
    }

    @Override
    @ForceInline
    public DoubleVector fma(double o1, double o2, Mask<Double> m) {
        return fma(SPECIES.broadcast(o1), SPECIES.broadcast(o2), m);
    }

    @Override
    @ForceInline
    public DoubleVector hypot(double o) {
        return hypot(SPECIES.broadcast(o));
    }

    @Override
    @ForceInline
    public DoubleVector hypot(double o, Mask<Double> m) {
        return hypot(SPECIES.broadcast(o), m);
    }


    // Unary operations

    @ForceInline
    @Override
    public DoubleMaxVector neg(Mask<Double> m) {
        return blend(neg(), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector abs() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_ABS, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (double) Math.abs(a)));
    }

    @ForceInline
    @Override
    public DoubleMaxVector abs(Mask<Double> m) {
        return blend(abs(), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector neg() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_NEG, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (double) -a));
    }

    @Override
    @ForceInline
    public DoubleMaxVector div(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_DIV, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double)(a / b)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector sqrt() {
        return VectorIntrinsics.unaryOp(
            VECTOR_OP_SQRT, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> v1.uOp((i, a) -> (double) Math.sqrt((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector exp() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_EXP, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.exp((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector log1p() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_LOG1P, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.log1p((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector log() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_LOG, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.log((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector log10() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_LOG10, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.log10((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector expm1() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_EXPM1, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.expm1((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector cbrt() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_CBRT, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.cbrt((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector sin() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_SIN, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.sin((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector cos() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_COS, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.cos((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector tan() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_TAN, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.tan((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector asin() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_ASIN, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.asin((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector acos() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_ACOS, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.acos((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector atan() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_ATAN, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.atan((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector sinh() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_SINH, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.sinh((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector cosh() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_COSH, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.cosh((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector tanh() {
        return (DoubleMaxVector) VectorIntrinsics.unaryOp(
            VECTOR_OP_TANH, DoubleMaxVector.class, double.class, LENGTH,
            this,
            v1 -> ((DoubleMaxVector)v1).uOp((i, a) -> (double) Math.tanh((double) a)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector pow(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return (DoubleMaxVector) VectorIntrinsics.binaryOp(
            VECTOR_OP_POW, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> ((DoubleMaxVector)v1).bOp(v2, (i, a, b) -> (double)(Math.pow(a,b))));
    }

    @Override
    @ForceInline
    public DoubleMaxVector hypot(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return (DoubleMaxVector) VectorIntrinsics.binaryOp(
            VECTOR_OP_HYPOT, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> ((DoubleMaxVector)v1).bOp(v2, (i, a, b) -> (double)(Math.hypot(a,b))));
    }

    @Override
    @ForceInline
    public DoubleMaxVector atan2(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return (DoubleMaxVector) VectorIntrinsics.binaryOp(
            VECTOR_OP_ATAN2, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> ((DoubleMaxVector)v1).bOp(v2, (i, a, b) -> (double)(Math.atan2(a,b))));
    }


    // Binary operations

    @Override
    @ForceInline
    public DoubleMaxVector add(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_ADD, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double)(a + b)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector add(Vector<Double> v, Mask<Double> m) {
        return blend(add(v), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector sub(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_SUB, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double)(a - b)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector sub(Vector<Double> v, Mask<Double> m) {
        return blend(sub(v), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector mul(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MUL, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double)(a * b)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector mul(Vector<Double> v, Mask<Double> m) {
        return blend(mul(v), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector min(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return (DoubleMaxVector) VectorIntrinsics.binaryOp(
            VECTOR_OP_MIN, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double) Math.min(a, b)));
    }

    @Override
    @ForceInline
    public DoubleMaxVector min(Vector<Double> v, Mask<Double> m) {
        return blend(min(v), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector max(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;
        return VectorIntrinsics.binaryOp(
            VECTOR_OP_MAX, DoubleMaxVector.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bOp(v2, (i, a, b) -> (double) Math.max(a, b)));
        }

    @Override
    @ForceInline
    public DoubleMaxVector max(Vector<Double> v, Mask<Double> m) {
        return blend(max(v), m);
    }


    // Ternary operations

    @Override
    @ForceInline
    public DoubleMaxVector fma(Vector<Double> o1, Vector<Double> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        DoubleMaxVector v1 = (DoubleMaxVector)o1;
        DoubleMaxVector v2 = (DoubleMaxVector)o2;
        return VectorIntrinsics.ternaryOp(
            VECTOR_OP_FMA, DoubleMaxVector.class, double.class, LENGTH,
            this, v1, v2,
            (w1, w2, w3) -> w1.tOp(w2, w3, (i, a, b, c) -> Math.fma(a, b, c)));
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public double addAll() {
        long bits = (long) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_ADD, DoubleMaxVector.class, double.class, LENGTH,
                                this,
                                v -> {
                                    double r = v.rOp((double) 0, (i, a, b) -> (double) (a + b));
                                    return (long)Double.doubleToLongBits(r);
                                });
        return Double.longBitsToDouble(bits);
    }

    @Override
    @ForceInline
    public double subAll() {
        long bits = (long) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_SUB, DoubleMaxVector.class, double.class, LENGTH,
                                this,
                                v -> {
                                    double r = v.rOp((double) 0, (i, a, b) -> (double) (a - b));
                                    return (long)Double.doubleToLongBits(r);
                                });
        return Double.longBitsToDouble(bits);
    }

    @Override
    @ForceInline
    public double mulAll() {
        long bits = (long) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_MUL, DoubleMaxVector.class, double.class, LENGTH,
                                this,
                                v -> {
                                    double r = v.rOp((double) 1, (i, a, b) -> (double) (a * b));
                                    return (long)Double.doubleToLongBits(r);
                                });
        return Double.longBitsToDouble(bits);
    }

    @Override
    @ForceInline
    public double minAll() {
        long bits = (long) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_MIN, DoubleMaxVector.class, double.class, LENGTH,
                                this,
                                v -> {
                                    double r = v.rOp(Double.MAX_VALUE , (i, a, b) -> (double) Math.min(a, b));
                                    return (long)Double.doubleToLongBits(r);
                                });
        return Double.longBitsToDouble(bits);
    }

    @Override
    @ForceInline
    public double maxAll() {
        long bits = (long) VectorIntrinsics.reductionCoerced(
                                VECTOR_OP_MAX, DoubleMaxVector.class, double.class, LENGTH,
                                this,
                                v -> {
                                    double r = v.rOp(Double.MIN_VALUE , (i, a, b) -> (double) Math.max(a, b));
                                    return (long)Double.doubleToLongBits(r);
                                });
        return Double.longBitsToDouble(bits);
    }


    @Override
    @ForceInline
    public double addAll(Mask<Double> m) {
        return blend(SPECIES.broadcast((double) 0), m).addAll();
    }

    @Override
    @ForceInline
    public double subAll(Mask<Double> m) {
        return blend(SPECIES.broadcast((double) 0), m).subAll();
    }

    @Override
    @ForceInline
    public double mulAll(Mask<Double> m) {
        return blend(SPECIES.broadcast((double) 1), m).mulAll();
    }

    @Override
    @ForceInline
    public double minAll(Mask<Double> m) {
        return blend(SPECIES.broadcast(Double.MAX_VALUE), m).minAll();
    }

    @Override
    @ForceInline
    public double maxAll(Mask<Double> m) {
        return blend(SPECIES.broadcast(Double.MIN_VALUE), m).maxAll();
    }

    @Override
    @ForceInline
    public Shuffle<Double> toShuffle() {
        double[] a = toArray();
        int[] sa = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            sa[i] = (int) a[i];
        }
        return SPECIES.shuffleFromArray(sa, 0);
    }

    // Memory operations

    private static final int ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_DOUBLE_INDEX_SCALE);

    @Override
    @ForceInline
    public void intoArray(double[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
        VectorIntrinsics.store(DoubleMaxVector.class, double.class, LENGTH,
                               a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                               this,
                               a, ix,
                               (arr, idx, v) -> v.forEach((i, e) -> arr[idx + i] = e));
    }

    @Override
    @ForceInline
    public final void intoArray(double[] a, int ax, Mask<Double> m) {
        DoubleMaxVector oldVal = SPECIES.fromArray(a, ax);
        DoubleMaxVector newVal = oldVal.blend(this, m);
        newVal.intoArray(a, ax);
    }
    @Override
    @ForceInline
    public void intoArray(double[] a, int ix, int[] b, int iy) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        // Index vector: vix[0:n] = i -> ix + indexMap[iy + i]
        IntVector vix = INDEX_SPEC.fromArray(b, iy).add(ix);

        vix = VectorIntrinsics.checkIndex(vix, a.length);

        VectorIntrinsics.storeWithMap(DoubleMaxVector.class, double.class, LENGTH, vix.getClass(),
                               a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET, vix,
                               this,
                               a, ix, b, iy,
                               (arr, idx, v, indexMap, idy) -> v.forEach((i, e) -> arr[idx+indexMap[idy+i]] = e));
    }

     @Override
     @ForceInline
     public final void intoArray(double[] a, int ax, Mask<Double> m, int[] b, int iy) {
         // @@@ This can result in out of bounds errors for unset mask lanes
         DoubleMaxVector oldVal = SPECIES.fromArray(a, ax, b, iy);
         DoubleMaxVector newVal = oldVal.blend(this, m);
         newVal.intoArray(a, ax, b, iy);
     }

    @Override
    @ForceInline
    public void intoByteArray(byte[] a, int ix) {
        Objects.requireNonNull(a);
        ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
        VectorIntrinsics.store(DoubleMaxVector.class, double.class, LENGTH,
                               a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                               this,
                               a, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = ByteBuffer.wrap(c, idx, c.length - idx).order(ByteOrder.nativeOrder());
                                   DoubleBuffer tb = bbc.asDoubleBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public final void intoByteArray(byte[] a, int ix, Mask<Double> m) {
        DoubleMaxVector oldVal = SPECIES.fromByteArray(a, ix);
        DoubleMaxVector newVal = oldVal.blend(this, m);
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
        VectorIntrinsics.store(DoubleMaxVector.class, double.class, LENGTH,
                               U.getReference(bb, BYTE_BUFFER_HB), ix + U.getLong(bb, BUFFER_ADDRESS),
                               this,
                               bb, ix,
                               (c, idx, v) -> {
                                   ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                   DoubleBuffer tb = bbc.asDoubleBuffer();
                                   v.forEach((i, e) -> tb.put(e));
                               });
    }

    @Override
    @ForceInline
    public void intoByteBuffer(ByteBuffer bb, int ix, Mask<Double> m) {
        DoubleMaxVector oldVal = SPECIES.fromByteBuffer(bb, ix);
        DoubleMaxVector newVal = oldVal.blend(this, m);
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

        DoubleMaxVector that = (DoubleMaxVector) o;
        return this.equal(that).allTrue();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vec);
    }

    // Binary test

    @Override
    DoubleMaxMask bTest(Vector<Double> o, FBinTest f) {
        double[] vec1 = getElements();
        double[] vec2 = ((DoubleMaxVector)o).getElements();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return new DoubleMaxMask(bits);
    }

    // Comparisons

    @Override
    @ForceInline
    public DoubleMaxMask equal(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return VectorIntrinsics.compare(
            BT_eq, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a == b));
    }

    @Override
    @ForceInline
    public DoubleMaxMask notEqual(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return VectorIntrinsics.compare(
            BT_ne, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a != b));
    }

    @Override
    @ForceInline
    public DoubleMaxMask lessThan(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return VectorIntrinsics.compare(
            BT_lt, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a < b));
    }

    @Override
    @ForceInline
    public DoubleMaxMask lessThanEq(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return VectorIntrinsics.compare(
            BT_le, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a <= b));
    }

    @Override
    @ForceInline
    public DoubleMaxMask greaterThan(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return (DoubleMaxMask) VectorIntrinsics.compare(
            BT_gt, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a > b));
    }

    @Override
    @ForceInline
    public DoubleMaxMask greaterThanEq(Vector<Double> o) {
        Objects.requireNonNull(o);
        DoubleMaxVector v = (DoubleMaxVector)o;

        return VectorIntrinsics.compare(
            BT_ge, DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v,
            (v1, v2) -> v1.bTest(v2, (i, a, b) -> a >= b));
    }

    // Foreach

    @Override
    void forEach(FUnCon f) {
        double[] vec = getElements();
        for (int i = 0; i < length(); i++) {
            f.apply(i, vec[i]);
        }
    }

    @Override
    void forEach(Mask<Double> o, FUnCon f) {
        boolean[] mbits = ((DoubleMaxMask)o).getBits();
        forEach((i, a) -> {
            if (mbits[i]) { f.apply(i, a); }
        });
    }

    LongMaxVector toBits() {
        double[] vec = getElements();
        long[] res = new long[this.species().length()];
        for(int i = 0; i < this.species().length(); i++){
            res[i] = Double.doubleToLongBits(vec[i]);
        }
        return new LongMaxVector(res);
    }


    @Override
    public DoubleMaxVector rotateEL(int j) {
        double[] vec = getElements();
        double[] res = new double[length()];
        for (int i = 0; i < length(); i++){
            res[(j + i) % length()] = vec[i];
        }
        return new DoubleMaxVector(res);
    }

    @Override
    public DoubleMaxVector rotateER(int j) {
        double[] vec = getElements();
        double[] res = new double[length()];
        for (int i = 0; i < length(); i++){
            int z = i - j;
            if(j < 0) {
                res[length() + z] = vec[i];
            } else {
                res[z] = vec[i];
            }
        }
        return new DoubleMaxVector(res);
    }

    @Override
    public DoubleMaxVector shiftEL(int j) {
        double[] vec = getElements();
        double[] res = new double[length()];
        for (int i = 0; i < length() - j; i++) {
            res[i] = vec[i + j];
        }
        return new DoubleMaxVector(res);
    }

    @Override
    public DoubleMaxVector shiftER(int j) {
        double[] vec = getElements();
        double[] res = new double[length()];
        for (int i = 0; i < length() - j; i++){
            res[i + j] = vec[i];
        }
        return new DoubleMaxVector(res);
    }

    @Override
    @ForceInline
    public DoubleMaxVector rearrange(Vector<Double> v,
                                  Shuffle<Double> s, Mask<Double> m) {
        return this.rearrange(s).blend(v.rearrange(s), m);
    }

    @Override
    @ForceInline
    public DoubleMaxVector rearrange(Shuffle<Double> o1) {
        Objects.requireNonNull(o1);
        DoubleMaxShuffle s =  (DoubleMaxShuffle)o1;

        return VectorIntrinsics.rearrangeOp(
            DoubleMaxVector.class, DoubleMaxShuffle.class, double.class, LENGTH,
            this, s,
            (v1, s_) -> v1.uOp((i, a) -> {
                int ei = s_.getElement(i);
                return v1.get(ei);
            }));
    }

    @Override
    @ForceInline
    public DoubleMaxVector blend(Vector<Double> o1, Mask<Double> o2) {
        Objects.requireNonNull(o1);
        Objects.requireNonNull(o2);
        DoubleMaxVector v = (DoubleMaxVector)o1;
        DoubleMaxMask   m = (DoubleMaxMask)o2;

        return VectorIntrinsics.blend(
            DoubleMaxVector.class, DoubleMaxMask.class, double.class, LENGTH,
            this, v, m,
            (v1, v2, m_) -> v1.bOp(v2, (i, a, b) -> m_.getElement(i) ? b : a));
    }

    // Accessors

    @Override
    public double get(int i) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        long bits = (long) VectorIntrinsics.extract(
                                DoubleMaxVector.class, double.class, LENGTH,
                                this, i,
                                (vec, ix) -> {
                                    double[] vecarr = vec.getElements();
                                    return (long)Double.doubleToLongBits(vecarr[ix]);
                                });
        return Double.longBitsToDouble(bits);
    }

    @Override
    public DoubleMaxVector with(int i, double e) {
        if (i < 0 || i >= LENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + LENGTH);
        }
        return VectorIntrinsics.insert(
                                DoubleMaxVector.class, double.class, LENGTH,
                                this, i, (long)Double.doubleToLongBits(e),
                                (v, ix, bits) -> {
                                    double[] res = v.getElements().clone();
                                    res[ix] = Double.longBitsToDouble((long)bits);
                                    return new DoubleMaxVector(res);
                                });
    }

    // Mask

    static final class DoubleMaxMask extends AbstractMask<Double> {
        static final DoubleMaxMask TRUE_MASK = new DoubleMaxMask(true);
        static final DoubleMaxMask FALSE_MASK = new DoubleMaxMask(false);

        private final boolean[] bits; // Don't access directly, use getBits() instead.

        public DoubleMaxMask(boolean[] bits) {
            this(bits, 0);
        }

        public DoubleMaxMask(boolean[] bits, int offset) {
            boolean[] a = new boolean[species().length()];
            for (int i = 0; i < a.length; i++) {
                a[i] = bits[offset + i];
            }
            this.bits = a;
        }

        public DoubleMaxMask(boolean val) {
            boolean[] bits = new boolean[species().length()];
            Arrays.fill(bits, val);
            this.bits = bits;
        }

        boolean[] getBits() {
            return VectorIntrinsics.maybeRebox(this).bits;
        }

        @Override
        DoubleMaxMask uOp(MUnOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new DoubleMaxMask(res);
        }

        @Override
        DoubleMaxMask bOp(Mask<Double> o, MBinOp f) {
            boolean[] res = new boolean[species().length()];
            boolean[] bits = getBits();
            boolean[] mbits = ((DoubleMaxMask)o).getBits();
            for (int i = 0; i < species().length(); i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new DoubleMaxMask(res);
        }

        @Override
        public DoubleMaxSpecies species() {
            return SPECIES;
        }

        @Override
        @ForceInline
        public <F> Mask<F> cast(Species<F> s) {
            if (s.length() != LENGTH)
                throw new IllegalArgumentException("This mask's length and given species length differ");
            return s.maskFromArray(toArray(), 0);
        }

        @Override
        public DoubleMaxVector toVector() {
            double[] res = new double[species().length()];
            boolean[] bits = getBits();
            for (int i = 0; i < species().length(); i++) {
                // -1 will result in the most significant bit being set in
                // addition to some or all other bits
                res[i] = (double) (bits[i] ? -1 : 0);
            }
            return new DoubleMaxVector(res);
        }

        // Unary operations

        @Override
        @ForceInline
        public DoubleMaxMask not() {
            return (DoubleMaxMask) VectorIntrinsics.unaryOp(
                                             VECTOR_OP_NOT, DoubleMaxMask.class, long.class, LENGTH,
                                             this,
                                             (m1) -> m1.uOp((i, a) -> !a));
        }

        // Binary operations

        @Override
        @ForceInline
        public DoubleMaxMask and(Mask<Double> o) {
            Objects.requireNonNull(o);
            DoubleMaxMask m = (DoubleMaxMask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_AND, DoubleMaxMask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public DoubleMaxMask or(Mask<Double> o) {
            Objects.requireNonNull(o);
            DoubleMaxMask m = (DoubleMaxMask)o;
            return VectorIntrinsics.binaryOp(VECTOR_OP_OR, DoubleMaxMask.class, long.class, LENGTH,
                                             this, m,
                                             (m1, m2) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorIntrinsics.test(COND_notZero, DoubleMaxMask.class, long.class, LENGTH,
                                         this, this,
                                         (m1, m2) -> super.anyTrue());
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorIntrinsics.test(COND_carrySet, DoubleMaxMask.class, long.class, LENGTH,
                                         this, species().maskAllTrue(),
                                         (m1, m2) -> super.allTrue());
        }
    }

    // Shuffle

    static final class DoubleMaxShuffle extends AbstractShuffle<Double> {
        DoubleMaxShuffle(byte[] reorder) {
            super(reorder);
        }

        public DoubleMaxShuffle(int[] reorder) {
            super(reorder);
        }

        public DoubleMaxShuffle(int[] reorder, int i) {
            super(reorder, i);
        }

        public DoubleMaxShuffle(IntUnaryOperator f) {
            super(f);
        }

        @Override
        public DoubleMaxSpecies species() {
            return SPECIES;
        }

        @Override
        @ForceInline
        public <F> Shuffle<F> cast(Species<F> s) {
            if (s.length() != LENGTH)
                throw new IllegalArgumentException("This shuffle and the given species's length differ");
            return s.shuffleFromArray(toArray(), 0);
        }

        @Override
        public DoubleMaxVector toVector() {
            double[] va = new double[SPECIES.length()];
            for (int i = 0; i < va.length; i++) {
              va[i] = (double) getElement(i);
            }
            return species().fromArray(va, 0);
        }

        @Override
        public DoubleMaxShuffle rearrange(Vector.Shuffle<Double> o) {
            DoubleMaxShuffle s = (DoubleMaxShuffle) o;
            byte[] r = new byte[reorder.length];
            for (int i = 0; i < reorder.length; i++) {
                r[i] = reorder[s.reorder[i]];
            }
            return new DoubleMaxShuffle(r);
        }
    }

    // Species

    @Override
    public DoubleMaxSpecies species() {
        return SPECIES;
    }

    static final class DoubleMaxSpecies extends DoubleSpecies {
        static final int BIT_SIZE = Shape.S_Max_BIT.bitSize();

        static final int LENGTH = BIT_SIZE / Double.SIZE;

        @Override
        public String toString() {
           StringBuilder sb = new StringBuilder("Shape[");
           sb.append(bitSize()).append(" bits, ");
           sb.append(length()).append(" ").append(double.class.getSimpleName()).append("s x ");
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
        public Class<Double> elementType() {
            return double.class;
        }

        @Override
        @ForceInline
        public int elementSize() {
            return Double.SIZE;
        }

        @Override
        @ForceInline
        @SuppressWarnings("unchecked")
        Class<?> vectorType() {
            return DoubleMaxVector.class;
        }

        @Override
        @ForceInline
        public Shape shape() {
            return Shape.S_Max_BIT;
        }

        @Override
        DoubleMaxVector op(FOp f) {
            double[] res = new double[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = f.apply(i);
            }
            return new DoubleMaxVector(res);
        }

        @Override
        DoubleMaxVector op(Mask<Double> o, FOp f) {
            double[] res = new double[length()];
            boolean[] mbits = ((DoubleMaxMask)o).getBits();
            for (int i = 0; i < length(); i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return new DoubleMaxVector(res);
        }

        @Override
        DoubleMaxMask opm(FOpm f) {
            boolean[] res = new boolean[length()];
            for (int i = 0; i < length(); i++) {
                res[i] = (boolean)f.apply(i);
            }
            return new DoubleMaxMask(res);
        }

        // Factories

        @Override
        public DoubleMaxMask maskFromValues(boolean... bits) {
            return new DoubleMaxMask(bits);
        }

        @Override
        public DoubleMaxShuffle shuffle(IntUnaryOperator f) {
            return new DoubleMaxShuffle(f);
        }

        @Override
        public DoubleMaxShuffle shuffleIota() {
            return new DoubleMaxShuffle(AbstractShuffle.IDENTITY);
        }

        @Override
        public DoubleMaxShuffle shuffleFromValues(int... ixs) {
            return new DoubleMaxShuffle(ixs);
        }

        @Override
        public DoubleMaxShuffle shuffleFromArray(int[] ixs, int i) {
            return new DoubleMaxShuffle(ixs, i);
        }

        @Override
        @ForceInline
        public DoubleMaxVector zero() {
            return VectorIntrinsics.broadcastCoerced(DoubleMaxVector.class, double.class, LENGTH,
                                                     Double.doubleToLongBits(0.0f),
                                                     (z -> ZERO));
        }

        @Override
        @ForceInline
        public DoubleMaxVector broadcast(double e) {
            return VectorIntrinsics.broadcastCoerced(
                DoubleMaxVector.class, double.class, LENGTH,
                Double.doubleToLongBits(e),
                ((long bits) -> SPECIES.op(i -> Double.longBitsToDouble((long)bits))));
        }

        @Override
        @ForceInline
        public DoubleMaxMask maskAllTrue() {
            return VectorIntrinsics.broadcastCoerced(DoubleMaxMask.class, long.class, LENGTH,
                                                     (long)-1,
                                                     (z -> DoubleMaxMask.TRUE_MASK));
        }

        @Override
        @ForceInline
        public DoubleMaxMask maskAllFalse() {
            return VectorIntrinsics.broadcastCoerced(DoubleMaxMask.class, long.class, LENGTH,
                                                     0,
                                                     (z -> DoubleMaxMask.FALSE_MASK));
        }

        @Override
        @ForceInline
        public DoubleMaxVector scalars(double... es) {
            Objects.requireNonNull(es);
            int ix = VectorIntrinsics.checkIndex(0, es.length, LENGTH);
            return VectorIntrinsics.load(DoubleMaxVector.class, double.class, LENGTH,
                                         es, Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                                         es, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public DoubleMaxMask maskFromArray(boolean[] bits, int ix) {
            Objects.requireNonNull(bits);
            ix = VectorIntrinsics.checkIndex(ix, bits.length, LENGTH);
            return VectorIntrinsics.load(DoubleMaxMask.class, long.class, LENGTH,
                                         bits, (((long)ix) << Unsafe.ARRAY_BOOLEAN_INDEX_SCALE)+ Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                                         bits, ix,
                                         (c, idx) -> opm(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public DoubleMaxVector fromArray(double[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, LENGTH);
            return VectorIntrinsics.load(DoubleMaxVector.class, double.class, LENGTH,
                                         a, (((long) ix) << ARRAY_SHIFT) + Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> op(n -> c[idx + n]));
        }

        @Override
        @ForceInline
        public DoubleMaxVector fromArray(double[] a, int ax, Mask<Double> m) {
            return zero().blend(fromArray(a, ax), m);
        }

        @Override
        @ForceInline
        public DoubleMaxVector fromByteArray(byte[] a, int ix) {
            Objects.requireNonNull(a);
            ix = VectorIntrinsics.checkIndex(ix, a.length, bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(DoubleMaxVector.class, double.class, LENGTH,
                                         a, ((long) ix) + Unsafe.ARRAY_BYTE_BASE_OFFSET,
                                         a, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = ByteBuffer.wrap(c, idx, a.length - idx).order(ByteOrder.nativeOrder());
                                             DoubleBuffer tb = bbc.asDoubleBuffer();
                                             return op(i -> tb.get());
                                         });
        }
        @Override
        @ForceInline
        public DoubleMaxVector fromArray(double[] a, int ix, int[] b, int iy) {
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);

            // Index vector: vix[0:n] = i -> ix + indexMap[iy + i]
            IntVector vix = INDEX_SPEC.fromArray(b, iy).add(ix);

            vix = VectorIntrinsics.checkIndex(vix, a.length);

            return VectorIntrinsics.loadWithMap(DoubleMaxVector.class, double.class, LENGTH, vix.getClass(),
                                        a, Unsafe.ARRAY_DOUBLE_BASE_OFFSET, vix,
                                        a, ix, b, iy,
                                       (c, idx, indexMap, idy) -> op(n -> c[idx + indexMap[idy+n]]));
       }

       @Override
       @ForceInline
       public DoubleMaxVector fromArray(double[] a, int ax, Mask<Double> m, int[] indexMap, int j) {
           // @@@ This can result in out of bounds errors for unset mask lanes
           return zero().blend(fromArray(a, ax, indexMap, j), m);
       }


        @Override
        @ForceInline
        public DoubleMaxVector fromByteArray(byte[] a, int ix, Mask<Double> m) {
            return zero().blend(fromByteArray(a, ix), m);
        }

        @Override
        @ForceInline
        public DoubleMaxVector fromByteBuffer(ByteBuffer bb, int ix) {
            if (bb.order() != ByteOrder.nativeOrder()) {
                throw new IllegalArgumentException();
            }
            ix = VectorIntrinsics.checkIndex(ix, bb.limit(), bitSize() / Byte.SIZE);
            return VectorIntrinsics.load(DoubleMaxVector.class, double.class, LENGTH,
                                         U.getReference(bb, BYTE_BUFFER_HB), U.getLong(bb, BUFFER_ADDRESS) + ix,
                                         bb, ix,
                                         (c, idx) -> {
                                             ByteBuffer bbc = c.duplicate().position(idx).order(ByteOrder.nativeOrder());
                                             DoubleBuffer tb = bbc.asDoubleBuffer();
                                             return op(i -> tb.get());
                                         });
        }

        @Override
        @ForceInline
        public DoubleMaxVector fromByteBuffer(ByteBuffer bb, int ix, Mask<Double> m) {
            return zero().blend(fromByteBuffer(bb, ix), m);
        }
    }
}
