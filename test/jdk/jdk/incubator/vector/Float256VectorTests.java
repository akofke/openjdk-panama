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
 * @run testng Float256VectorTests
 *
 */

import jdk.incubator.vector.Shapes;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.FloatVector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Float256VectorTests extends AbstractVectorTest {

    static final FloatVector.FloatSpecies<Shapes.S256Bit> SPECIES =
                FloatVector.species(Shapes.S_256_BIT);

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    interface FUnOp {
        float apply(float a);
    }

    static void assertArraysEquals(float[] a, float[] r, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    static void assertArraysEquals(float[] a, float[] r, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], "at index #" + i + ", input = " + a[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }

    interface FReductionOp {
      float apply(float[] a, int idx);
    }

    static void assertReductionArraysEquals(float[] a, float[] b, FReductionOp f) {
      int i = 0;
      try {
        for (; i < a.length; i += SPECIES.length()) {
          Assert.assertEquals(b[i], f.apply(a, i));
        }
      } catch (AssertionError e) {
        Assert.assertEquals(b[i], f.apply(a, i), "at index #" + i);
      }
    }
 
    interface FBoolReductionOp {
      boolean apply(boolean[] a, int idx);
    }

    static void assertReductionBoolArraysEquals(boolean[] a, boolean[] b, FBoolReductionOp f) {
      int i = 0;
      try {
        for (; i < a.length; i += SPECIES.length()) {
          Assert.assertEquals(f.apply(a, i), b[i]);
        }
      } catch (AssertionError e) {
        Assert.assertEquals(f.apply(a, i), b[i], "at index #" + i);
      }
    }

    interface FBinOp {
        float apply(float a, float b);
    }

    interface FBinMaskOp {
        float apply(float a, float b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
        }
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinOp f) {
        assertArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(float[] a, float[] b, float[] r, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }
    static boolean isWithin1Ulp(float actual, float expected) {
        if (Float.isNaN(expected) && !Float.isNaN(actual)) {
            return false;
        } else if (!Float.isNaN(expected) && Float.isNaN(actual)) {
            return false;
        }

        float low = Math.nextDown(expected);
        float high = Math.nextUp(expected);

        if (Float.compare(low, expected) > 0) {
            return false;
        }

        if (Float.compare(high, expected) < 0) {
            return false;
        }

        return true;
    }

    static void assertArraysEqualsWithinOneUlp(float[] a, float[] r, FUnOp mathf, FUnOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i])) == 0, "at index #" + i + ", input = " + a[i]);
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i])), "at index #" + i + ", input = " + a[i]);
        }
    }

    static void assertArraysEqualsWithinOneUlp(float[] a, float[] b, float[] r, FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i], b[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Float.compare(r[i], mathf.apply(a[i], b[i])) == 0, "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
        }
    }
    static final List<IntFunction<float[]>> FLOAT_GENERATORS = List.of(
            withToString("float[i * 5]", (int s) -> {
                return fill(s * 1000,
                            i -> (float)(i * 5));
            }),
            withToString("float[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (((float)(i + 1) == 0) ? 1 : (float)(i + 1)));
            }),
            withToString("float[cornerCaseValue(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerCaseValue(i));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<float[]>>> FLOAT_GENERATOR_PAIRS =
        Stream.of(FLOAT_GENERATORS.get(0)).
                flatMap(fa -> FLOAT_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatBinaryOpProvider() {
        return FLOAT_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpProvider() {
        return FLOAT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] floatUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> FLOAT_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<float[]>> FLOAT_COMPARE_GENERATORS = List.of(
            withToString("float[i]", (int s) -> {
                return fill(s * 1000,
                            i -> (float)i);
            }),
            withToString("float[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (float)(i + 1));
            }),
            withToString("float[i - 2]", (int s) -> {
                return fill(s * 1000,
                            i -> (float)(i - 2));
            }),
            withToString("float[zigZag(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> i%3 == 0 ? (float)i : (i%3 == 1 ? (float)(i + 1) : (float)(i - 2)));
            }),
            withToString("float[cornerCaseValue(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<List<IntFunction<float[]>>> FLOAT_COMPARE_GENERATOR_PAIRS =
        FLOAT_COMPARE_GENERATORS.stream().
                flatMap(fa -> FLOAT_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] floatCompareOpProvider() {
        return FLOAT_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    interface ToFloatF {
        float apply(int i);
    }

    static float[] fill(int s , ToFloatF f) {
        return fill(new float[s], f);
    }

    static float[] fill(float[] a, ToFloatF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }
    
    static float cornerCaseValue(int i) {
        switch(i % 7) {
            case 0:
                return Float.MAX_VALUE;
            case 1:
                return Float.MIN_VALUE;
            case 2:
                return Float.NEGATIVE_INFINITY;
            case 3:
                return Float.POSITIVE_INFINITY;
            case 4:
                return Float.NaN;
            case 5:
                return (float)0.0;
            default:
                return (float)-0.0;
        }
    }


    static float add(float a, float b) {
        return (float)(a + b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void addFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.add(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::add);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void addFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.add(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Float256VectorTests::add);
    }

    static float sub(float a, float b) {
        return (float)(a - b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void subFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.sub(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::sub);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void subFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.sub(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Float256VectorTests::sub);
    }


    static float div(float a, float b) {
        return (float)(a / b);
    }



    @Test(dataProvider = "floatBinaryOpProvider")
    static void divFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.div(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::div);
    }



    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void divFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.div(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Float256VectorTests::div);
    }


    static float mul(float a, float b) {
        return (float)(a * b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void mulFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.mul(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::mul);
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void mulFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.mul(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Float256VectorTests::mul);
    }










    static float max(float a, float b) {
        return (float)((a > b) ? a : b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void maxFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.max(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::max);
    }

    static float min(float a, float b) {
        return (float)((a < b) ? a : b);
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void minFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length()); 
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.min(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Float256VectorTests::min);
    }






    static float addAll(float[] a, int idx) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res += a[i];
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void addAllFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.addAll();
            }
        }

        assertReductionArraysEquals(a, r, Float256VectorTests::addAll);
    }
    static float subAll(float[] a, int idx) {
        float res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res -= a[i];
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void subAllFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.subAll();
            }
        }

        assertReductionArraysEquals(a, r, Float256VectorTests::subAll);
    }
    static float mulAll(float[] a, int idx) {
        float res = 1;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res *= a[i];
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void mulAllFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.mulAll();
            }
        }

        assertReductionArraysEquals(a, r, Float256VectorTests::mulAll);
    }
    static float minAll(float[] a, int idx) {
        float res = Float.MAX_VALUE;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res = (res < a[i])?res:a[i];
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void minAllFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.minAll();
            }
        }

        assertReductionArraysEquals(a, r, Float256VectorTests::minAll);
    }
    static float maxAll(float[] a, int idx) {
        float res = Float.MIN_VALUE;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res = (res > a[i])?res:a[i];
        }

        return res;
    }
    @Test(dataProvider = "floatUnaryOpProvider")
    static void maxAllFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.maxAll();
            }
        }

        assertReductionArraysEquals(a, r, Float256VectorTests::maxAll);
    }





    @Test(dataProvider = "floatCompareOpProvider")
    static void lessThanFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.lessThan(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] < b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void greaterThanFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.greaterThan(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] > b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void equalFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.equal(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] == b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void notEqualFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.notEqual(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] != b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void lessThanEqFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.lessThanEq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] <= b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "floatCompareOpProvider")
    static void greaterThanEqFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Float, Shapes.S256Bit> mv = av.greaterThanEq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] >= b[i + j]);
                }
            }
        }
    }


    static float blend(float a, float b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "floatBinaryOpMaskProvider")
    static void blendFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb,
                                          IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Float256VectorTests::blend);
    }

    static float sin(float a) {
        return (float)(Math.sin((double)a));
    }

    static float strictsin(float a) {
        return (float)(StrictMath.sin((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void sinFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.sin().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::sin, Float256VectorTests::strictsin);
    }


    static float exp(float a) {
        return (float)(Math.exp((double)a));
    }

    static float strictexp(float a) {
        return (float)(StrictMath.exp((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void expFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.exp().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::exp, Float256VectorTests::strictexp);
    }


    static float log1p(float a) {
        return (float)(Math.log1p((double)a));
    }

    static float strictlog1p(float a) {
        return (float)(StrictMath.log1p((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void log1pFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.log1p().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::log1p, Float256VectorTests::strictlog1p);
    }


    static float log(float a) {
        return (float)(Math.log((double)a));
    }

    static float strictlog(float a) {
        return (float)(StrictMath.log((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void logFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.log().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::log, Float256VectorTests::strictlog);
    }


    static float log10(float a) {
        return (float)(Math.log10((double)a));
    }

    static float strictlog10(float a) {
        return (float)(StrictMath.log10((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void log10Float256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.log10().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::log10, Float256VectorTests::strictlog10);
    }


    static float expm1(float a) {
        return (float)(Math.expm1((double)a));
    }

    static float strictexpm1(float a) {
        return (float)(StrictMath.expm1((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void expm1Float256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.expm1().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::expm1, Float256VectorTests::strictexpm1);
    }


    static float cos(float a) {
        return (float)(Math.cos((double)a));
    }

    static float strictcos(float a) {
        return (float)(StrictMath.cos((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void cosFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.cos().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::cos, Float256VectorTests::strictcos);
    }


    static float tan(float a) {
        return (float)(Math.tan((double)a));
    }

    static float stricttan(float a) {
        return (float)(StrictMath.tan((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void tanFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.tan().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::tan, Float256VectorTests::stricttan);
    }


    static float sinh(float a) {
        return (float)(Math.sinh((double)a));
    }

    static float strictsinh(float a) {
        return (float)(StrictMath.sinh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void sinhFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.sinh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::sinh, Float256VectorTests::strictsinh);
    }


    static float cosh(float a) {
        return (float)(Math.cosh((double)a));
    }

    static float strictcosh(float a) {
        return (float)(StrictMath.cosh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void coshFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.cosh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::cosh, Float256VectorTests::strictcosh);
    }


    static float tanh(float a) {
        return (float)(Math.tanh((double)a));
    }

    static float stricttanh(float a) {
        return (float)(StrictMath.tanh((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void tanhFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.tanh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::tanh, Float256VectorTests::stricttanh);
    }


    static float asin(float a) {
        return (float)(Math.asin((double)a));
    }

    static float strictasin(float a) {
        return (float)(StrictMath.asin((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void asinFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.asin().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::asin, Float256VectorTests::strictasin);
    }


    static float acos(float a) {
        return (float)(Math.acos((double)a));
    }

    static float strictacos(float a) {
        return (float)(StrictMath.acos((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void acosFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.acos().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::acos, Float256VectorTests::strictacos);
    }


    static float atan(float a) {
        return (float)(Math.atan((double)a));
    }

    static float strictatan(float a) {
        return (float)(StrictMath.atan((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void atanFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.atan().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::atan, Float256VectorTests::strictatan);
    }


    static float cbrt(float a) {
        return (float)(Math.cbrt((double)a));
    }

    static float strictcbrt(float a) {
        return (float)(StrictMath.cbrt((double)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void cbrtFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.cbrt().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Float256VectorTests::cbrt, Float256VectorTests::strictcbrt);
    }


    static float hypot(float a, float b) {
        return (float)(Math.hypot((double)a, (double)b));
    }

    static float stricthypot(float a, float b) {
        return (float)(StrictMath.hypot((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void hypotFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.hypot(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::hypot, Float256VectorTests::stricthypot);
    }


    static float pow(float a, float b) {
        return (float)(Math.pow((double)a, (double)b));
    }

    static float strictpow(float a, float b) {
        return (float)(StrictMath.pow((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void powFloat256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.pow(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::pow, Float256VectorTests::strictpow);
    }


    static float atan2(float a, float b) {
        return (float)(Math.atan2((double)a, (double)b));
    }

    static float strictatan2(float a, float b) {
        return (float)(StrictMath.atan2((double)a, (double)b));
    }

    @Test(dataProvider = "floatBinaryOpProvider")
    static void atan2Float256VectorTests(IntFunction<float[]> fa, IntFunction<float[]> fb) {
        float[] a = fa.apply(SPECIES.length());
        float[] b = fb.apply(SPECIES.length()); 
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                FloatVector<Shapes.S256Bit> bv = SPECIES.fromArray(b, i);
                av.atan2(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Float256VectorTests::atan2, Float256VectorTests::strictatan2);
    }


    static float neg(float a) {
        return (float)(-((float)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void negFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::neg);
    }

    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void negMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.neg(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::neg);
    }

    static float abs(float a) {
        return (float)(Math.abs((float)a));
    }

    @Test(dataProvider = "floatUnaryOpProvider")
    static void absFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::abs);
    }

    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void absMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.abs(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::abs);
    }





    static float sqrt(float a) {
        return (float)(Math.sqrt((double)a));
    }



    @Test(dataProvider = "floatUnaryOpProvider")
    static void sqrtFloat256VectorTests(IntFunction<float[]> fa) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.sqrt().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Float256VectorTests::sqrt);
    }



    @Test(dataProvider = "floatUnaryOpMaskProvider")
    static void sqrtMaskedFloat256VectorTests(IntFunction<float[]> fa,
                                                IntFunction<boolean[]> fm) {
        float[] a = fa.apply(SPECIES.length());
        float[] r = new float[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Float, Shapes.S256Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                FloatVector<Shapes.S256Bit> av = SPECIES.fromArray(a, i);
                av.sqrt(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Float256VectorTests::sqrt);
    }

}

