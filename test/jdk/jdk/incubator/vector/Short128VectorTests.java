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
 * or visit www.oracle.com if you need additional information or have
 * questions.
 */

/*
 * @test
 * @modules jdk.incubator.vector
 * @run testng Short128VectorTests
 *
 */

import jdk.incubator.vector.Shapes;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.ShortVector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Short128VectorTests extends AbstractVectorTest {

    static final ShortVector.ShortSpecies<Shapes.S128Bit> SPECIES = (ShortVector.ShortSpecies<Shapes.S128Bit>)
                Vector.speciesInstance(Short.class, Shapes.S_128_BIT);


    interface FUnOp {
        short apply(short a);
    }

    static void assertArraysEquals(short[] a, short[] r, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i]), r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(f.apply(a[i]), r[i], "at index #" + i);
        }
    }

    static void assertArraysEquals(short[] a, short[] r, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], r[i], "at index #" + i);
        }
    }

    interface FBinOp {
        short apply(short a, short b);
    }

    interface FBinMaskOp {
        short apply(short a, short b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEquals(short[] a, short[] b, short[] r, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i], b[i]), r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(f.apply(a[i], b[i]), r[i], "at index #" + i);
        }
    }

    static void assertArraysEquals(short[] a, short[] b, short[] r, boolean[] mask, FBinOp f) {
        assertArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(short[] a, short[] b, short[] r, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i], b[i], mask[i % SPECIES.length()]), r[i]);
            }
        } catch (AssertionError err) {
            Assert.assertEquals(f.apply(a[i], b[i], mask[i % SPECIES.length()]), r[i], "at index #" + i + ", a[i] = " + a[i] + ", b[i] = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }
    
    static final List<IntFunction<short[]>> SHORT_GENERATORS = List.of(
            withToString("short[i * 5]", (int s) -> {
                return fill(s * 1000,
                            i -> (short)(i * 5));
            }),
            withToString("short[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (((short)(i + 1) == 0) ? 1 : (short)(i + 1)));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<short[]>>> SHORT_GENERATOR_PAIRS =
        Stream.of(SHORT_GENERATORS.get(0)).
                flatMap(fa -> SHORT_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortBinaryOpProvider() {
        return SHORT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> SHORT_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpProvider() {
        return SHORT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> SHORT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<short[]>> SHORT_COMPARE_GENERATORS = List.of(
            withToString("short[i]", (int s) -> {
                return fill(s * 1000,
                            i -> (short)i);
            }),
            withToString("short[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (short)(i + 1));
            }),
            withToString("short[i - 2]", (int s) -> {
                return fill(s * 1000,
                            i -> (short)(i - 2));
            }),
            withToString("short[zigZag(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> i%3 == 0 ? (short)i : (i%3 == 1 ? (short)(i + 1) : (short)(i - 2)));
            }),
            withToString("short[cornerComparisonCase(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerComparisonCase(i));
            })
    );

    static final List<List<IntFunction<short[]>>> SHORT_COMPARE_GENERATOR_PAIRS =
        SHORT_COMPARE_GENERATORS.stream().
                flatMap(fa -> SHORT_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] shortCompareOpProvider() {
        return SHORT_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    interface ToShortF {
        short apply(int i);
    }

    static short[] fill(int s , ToShortF f) {
        return fill(new short[s], f);
    }

    static short[] fill(short[] a, ToShortF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }
    
    static short cornerComparisonCase(int i) {
        switch(i % 5) {
            case 0:
                return Short.MAX_VALUE;
            case 1:
                return Short.MIN_VALUE;
            case 2:
                return Short.MIN_VALUE;
            case 3:
                return Short.MAX_VALUE;
            default:
                return (short)0;
        }
    }


    static short add(short a, short b) {
        return (short)(a + b);
    }

    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void addShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.add(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::add);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void addShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.add(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::add);
    }

    static short sub(short a, short b) {
        return (short)(a - b);
    }

    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void subShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.sub(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::sub);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void subShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.sub(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::sub);
    }




    static short mul(short a, short b) {
        return (short)(a * b);
    }

    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void mulShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.mul(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::mul);
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void mulShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.mul(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::mul);
    }


    static short and(short a, short b) {
        return (short)(a & b);
    }



    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void andShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.and(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::and);
    }



    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void andShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.and(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::and);
    }



    static short or(short a, short b) {
        return (short)(a | b);
    }



    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void orShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.or(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::or);
    }



    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void orShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.or(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::or);
    }



    static short xor(short a, short b) {
        return (short)(a ^ b);
    }



    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void xorShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.xor(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::xor);
    }



    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void xorShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.xor(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::xor);
    }


    static short max(short a, short b) {
        return (short)(Math.max(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void maxShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.max(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::max);
    }

    static short min(short a, short b) {
        return (short)(Math.min(a, b));
    }

    @Test(dataProvider = "shortBinaryOpProvider", invocationCount = 10)
    static void minShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.min(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Short128VectorTests::min);
    }

    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void lessThanShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.lessThan(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] < b[i + j]);
            }
        }
    }
    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void greaterThanShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.greaterThan(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] > b[i + j]);
            }
        }
    }
    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void equalShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.equal(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] == b[i + j]);
            }
        }
    }
    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void notEqualShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.notEqual(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] != b[i + j]);
            }
        }
    }
    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void lessThanEqShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.lessThanEq(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] <= b[i + j]);
            }
        }
    }
    @Test(dataProvider = "shortCompareOpProvider", invocationCount = 10)
    static void greaterThanEqShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Short, Shapes.S128Bit> mv = av.greaterThanEq(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] >= b[i + j]);
            }
        }
    }
    static short blend(short a, short b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "shortBinaryOpMaskProvider", invocationCount = 10)
    static void blendShort128VectorTests(IntFunction<short[]> fa, IntFunction<short[]> fb,
                                          IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] b = fb.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            ShortVector<Shapes.S128Bit> bv = SPECIES.fromArray(b, i);
            av.blend(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Short128VectorTests::blend);
    }

    static short neg(short a) {
        return (short)(-((short)a));
    }

    @Test(dataProvider = "shortUnaryOpProvider", invocationCount = 10)
    static void negShort128VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.neg().intoArray(r, i);
        }

        assertArraysEquals(a, r, Short128VectorTests::neg);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void negMaskedShort128VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.neg(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Short128VectorTests::neg);
    }

    static short abs(short a) {
        return (short)(Math.abs((short)a));
    }

    @Test(dataProvider = "shortUnaryOpProvider", invocationCount = 10)
    static void absShort128VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.abs().intoArray(r, i);
        }

        assertArraysEquals(a, r, Short128VectorTests::abs);
    }

    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void absMaskedShort128VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.abs(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Short128VectorTests::abs);
    }


    static short not(short a) {
        return (short)(~((short)a));
    }



    @Test(dataProvider = "shortUnaryOpProvider", invocationCount = 10)
    static void notShort128VectorTests(IntFunction<short[]> fa) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.not().intoArray(r, i);
        }

        assertArraysEquals(a, r, Short128VectorTests::not);
    }



    @Test(dataProvider = "shortUnaryOpMaskProvider")
    static void notMaskedShort128VectorTests(IntFunction<short[]> fa,
                                                IntFunction<boolean[]> fm) {
        short[] a = fa.apply(SPECIES.length());
        short[] r = new short[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Short, Shapes.S128Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            ShortVector<Shapes.S128Bit> av = SPECIES.fromArray(a, i);
            av.not(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Short128VectorTests::not);
    }

}

