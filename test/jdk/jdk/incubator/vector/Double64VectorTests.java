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
 * @run testng Double64VectorTests
 *
 */

import jdk.incubator.vector.Shapes;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.DoubleVector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Double64VectorTests extends AbstractVectorTest {

    static final DoubleVector.DoubleSpecies<Shapes.S64Bit> SPECIES =
                DoubleVector.species(Shapes.S_64_BIT);

    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 100);

    interface FUnOp {
        double apply(double a);
    }

    static void assertArraysEquals(double[] a, double[] r, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i]), "at index #" + i + ", input = " + a[i]);
        }
    }

    static void assertArraysEquals(double[] a, double[] r, boolean[] mask, FUnOp f) {
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
      double apply(double[] a, int idx);
    }

    static void assertReductionArraysEquals(double[] a, double[] b, FReductionOp f) {
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
        double apply(double a, double b);
    }

    interface FBinMaskOp {
        double apply(double a, double b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEquals(double[] a, double[] b, double[] r, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i]));
            }
        } catch (AssertionError e) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
        }
    }

    static void assertArraysEquals(double[] a, double[] b, double[] r, boolean[] mask, FBinOp f) {
        assertArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(double[] a, double[] b, double[] r, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]));
            }
        } catch (AssertionError err) {
            Assert.assertEquals(r[i], f.apply(a[i], b[i], mask[i % SPECIES.length()]), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }
    static boolean isWithin1Ulp(double actual, double expected) {
        if (Double.isNaN(expected) && !Double.isNaN(actual)) {
            return false;
        } else if (!Double.isNaN(expected) && Double.isNaN(actual)) {
            return false;
        }

        double low = Math.nextDown(expected);
        double high = Math.nextUp(expected);

        if (Double.compare(low, expected) > 0) {
            return false;
        }

        if (Double.compare(high, expected) < 0) {
            return false;
        }

        return true;
    }

    static void assertArraysEqualsWithinOneUlp(double[] a, double[] r, FUnOp mathf, FUnOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Double.compare(r[i], mathf.apply(a[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Double.compare(r[i], mathf.apply(a[i])) == 0, "at index #" + i + ", input = " + a[i]);
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i])), "at index #" + i + ", input = " + a[i]);
        }
    }

    static void assertArraysEqualsWithinOneUlp(double[] a, double[] b, double[] r, FBinOp mathf, FBinOp strictmathf) {
        int i = 0;
        try {
            // Check that result is within 1 ulp of strict math or equivalent to math implementation.
            for (; i < a.length; i++) {
                Assert.assertTrue(Double.compare(r[i], mathf.apply(a[i], b[i])) == 0 ||
                                    isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])));
            }
        } catch (AssertionError e) {
            Assert.assertTrue(Double.compare(r[i], mathf.apply(a[i], b[i])) == 0, "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
            Assert.assertTrue(isWithin1Ulp(r[i], strictmathf.apply(a[i], b[i])), "at index #" + i + ", input1 = " + a[i] + ", input2 = " + b[i]);
        }
    }
    static final List<IntFunction<double[]>> DOUBLE_GENERATORS = List.of(
            withToString("double[i * 5]", (int s) -> {
                return fill(s * 1000,
                            i -> (double)(i * 5));
            }),
            withToString("double[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (((double)(i + 1) == 0) ? 1 : (double)(i + 1)));
            }),
            withToString("double[cornerCaseValue(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerCaseValue(i));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<double[]>>> DOUBLE_GENERATOR_PAIRS =
        Stream.of(DOUBLE_GENERATORS.get(0)).
                flatMap(fa -> DOUBLE_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] boolUnaryOpProvider() {
        return BOOL_ARRAY_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] doubleBinaryOpProvider() {
        return DOUBLE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] doubleBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> DOUBLE_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] doubleUnaryOpProvider() {
        return DOUBLE_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] doubleUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> DOUBLE_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<double[]>> DOUBLE_COMPARE_GENERATORS = List.of(
            withToString("double[i]", (int s) -> {
                return fill(s * 1000,
                            i -> (double)i);
            }),
            withToString("double[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (double)(i + 1));
            }),
            withToString("double[i - 2]", (int s) -> {
                return fill(s * 1000,
                            i -> (double)(i - 2));
            }),
            withToString("double[zigZag(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> i%3 == 0 ? (double)i : (i%3 == 1 ? (double)(i + 1) : (double)(i - 2)));
            }),
            withToString("double[cornerCaseValue(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerCaseValue(i));
            })
    );

    static final List<List<IntFunction<double[]>>> DOUBLE_COMPARE_GENERATOR_PAIRS =
        DOUBLE_COMPARE_GENERATORS.stream().
                flatMap(fa -> DOUBLE_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] doubleCompareOpProvider() {
        return DOUBLE_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    interface ToDoubleF {
        double apply(int i);
    }

    static double[] fill(int s , ToDoubleF f) {
        return fill(new double[s], f);
    }

    static double[] fill(double[] a, ToDoubleF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }
    
    static double cornerCaseValue(int i) {
        switch(i % 7) {
            case 0:
                return Double.MAX_VALUE;
            case 1:
                return Double.MIN_VALUE;
            case 2:
                return Double.NEGATIVE_INFINITY;
            case 3:
                return Double.POSITIVE_INFINITY;
            case 4:
                return Double.NaN;
            case 5:
                return (double)0.0;
            default:
                return (double)-0.0;
        }
    }


    static double add(double a, double b) {
        return (double)(a + b);
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void addDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.add(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::add);
    }

    @Test(dataProvider = "doubleBinaryOpMaskProvider")
    static void addDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb,
                                          IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.add(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Double64VectorTests::add);
    }

    static double sub(double a, double b) {
        return (double)(a - b);
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void subDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.sub(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::sub);
    }

    @Test(dataProvider = "doubleBinaryOpMaskProvider")
    static void subDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb,
                                          IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.sub(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Double64VectorTests::sub);
    }


    static double div(double a, double b) {
        return (double)(a / b);
    }



    @Test(dataProvider = "doubleBinaryOpProvider")
    static void divDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.div(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::div);
    }



    @Test(dataProvider = "doubleBinaryOpMaskProvider")
    static void divDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb,
                                          IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.div(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Double64VectorTests::div);
    }


    static double mul(double a, double b) {
        return (double)(a * b);
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void mulDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.mul(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::mul);
    }

    @Test(dataProvider = "doubleBinaryOpMaskProvider")
    static void mulDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb,
                                          IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.mul(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Double64VectorTests::mul);
    }










    static double max(double a, double b) {
        return (double)((a > b) ? a : b);
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void maxDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.max(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::max);
    }

    static double min(double a, double b) {
        return (double)((a < b) ? a : b);
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void minDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length()); 
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];       

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.min(bv).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, Double64VectorTests::min);
    }






    static double addAll(double[] a, int idx) {
        double res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res += a[i];
        }

        return res;
    }
    @Test(dataProvider = "doubleUnaryOpProvider")
    static void addAllDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.addAll();
            }
        }

        assertReductionArraysEquals(a, r, Double64VectorTests::addAll);
    }
    static double subAll(double[] a, int idx) {
        double res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res -= a[i];
        }

        return res;
    }
    @Test(dataProvider = "doubleUnaryOpProvider")
    static void subAllDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.subAll();
            }
        }

        assertReductionArraysEquals(a, r, Double64VectorTests::subAll);
    }
    static double mulAll(double[] a, int idx) {
        double res = 1;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res *= a[i];
        }

        return res;
    }
    @Test(dataProvider = "doubleUnaryOpProvider")
    static void mulAllDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.mulAll();
            }
        }

        assertReductionArraysEquals(a, r, Double64VectorTests::mulAll);
    }
    static double minAll(double[] a, int idx) {
        double res = Double.MAX_VALUE;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res = (res < a[i])?res:a[i];
        }

        return res;
    }
    @Test(dataProvider = "doubleUnaryOpProvider")
    static void minAllDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.minAll();
            }
        }

        assertReductionArraysEquals(a, r, Double64VectorTests::minAll);
    }
    static double maxAll(double[] a, int idx) {
        double res = Double.MIN_VALUE;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res = (res > a[i])?res:a[i];
        }

        return res;
    }
    @Test(dataProvider = "doubleUnaryOpProvider")
    static void maxAllDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
              DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
              r[i] = av.maxAll();
            }
        }

        assertReductionArraysEquals(a, r, Double64VectorTests::maxAll);
    }





    @Test(dataProvider = "doubleCompareOpProvider")
    static void lessThanDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.lessThan(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] < b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "doubleCompareOpProvider")
    static void greaterThanDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.greaterThan(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] > b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "doubleCompareOpProvider")
    static void equalDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.equal(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] == b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "doubleCompareOpProvider")
    static void notEqualDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.notEqual(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] != b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "doubleCompareOpProvider")
    static void lessThanEqDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.lessThanEq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] <= b[i + j]);
                }
            }
        }
    }


    @Test(dataProvider = "doubleCompareOpProvider")
    static void greaterThanEqDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                Vector.Mask<Double, Shapes.S64Bit> mv = av.greaterThanEq(bv);

                // Check results as part of computation.
                for (int j = 0; j < SPECIES.length(); j++) {
                    Assert.assertEquals(mv.getElement(j), a[i + j] >= b[i + j]);
                }
            }
        }
    }


    static double blend(double a, double b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "doubleBinaryOpMaskProvider")
    static void blendDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb,
                                          IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.blend(bv, vmask).intoArray(r, i);
            }
        }
        assertArraysEquals(a, b, r, mask, Double64VectorTests::blend);
    }

    static double sin(double a) {
        return (double)(Math.sin((double)a));
    }

    static double strictsin(double a) {
        return (double)(StrictMath.sin((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void sinDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.sin().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::sin, Double64VectorTests::strictsin);
    }


    static double exp(double a) {
        return (double)(Math.exp((double)a));
    }

    static double strictexp(double a) {
        return (double)(StrictMath.exp((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void expDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.exp().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::exp, Double64VectorTests::strictexp);
    }


    static double log1p(double a) {
        return (double)(Math.log1p((double)a));
    }

    static double strictlog1p(double a) {
        return (double)(StrictMath.log1p((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void log1pDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.log1p().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::log1p, Double64VectorTests::strictlog1p);
    }


    static double log(double a) {
        return (double)(Math.log((double)a));
    }

    static double strictlog(double a) {
        return (double)(StrictMath.log((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void logDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.log().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::log, Double64VectorTests::strictlog);
    }


    static double log10(double a) {
        return (double)(Math.log10((double)a));
    }

    static double strictlog10(double a) {
        return (double)(StrictMath.log10((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void log10Double64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.log10().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::log10, Double64VectorTests::strictlog10);
    }


    static double expm1(double a) {
        return (double)(Math.expm1((double)a));
    }

    static double strictexpm1(double a) {
        return (double)(StrictMath.expm1((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void expm1Double64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.expm1().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::expm1, Double64VectorTests::strictexpm1);
    }


    static double cos(double a) {
        return (double)(Math.cos((double)a));
    }

    static double strictcos(double a) {
        return (double)(StrictMath.cos((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void cosDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.cos().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::cos, Double64VectorTests::strictcos);
    }


    static double tan(double a) {
        return (double)(Math.tan((double)a));
    }

    static double stricttan(double a) {
        return (double)(StrictMath.tan((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void tanDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.tan().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::tan, Double64VectorTests::stricttan);
    }


    static double sinh(double a) {
        return (double)(Math.sinh((double)a));
    }

    static double strictsinh(double a) {
        return (double)(StrictMath.sinh((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void sinhDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.sinh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::sinh, Double64VectorTests::strictsinh);
    }


    static double cosh(double a) {
        return (double)(Math.cosh((double)a));
    }

    static double strictcosh(double a) {
        return (double)(StrictMath.cosh((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void coshDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.cosh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::cosh, Double64VectorTests::strictcosh);
    }


    static double tanh(double a) {
        return (double)(Math.tanh((double)a));
    }

    static double stricttanh(double a) {
        return (double)(StrictMath.tanh((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void tanhDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.tanh().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::tanh, Double64VectorTests::stricttanh);
    }


    static double asin(double a) {
        return (double)(Math.asin((double)a));
    }

    static double strictasin(double a) {
        return (double)(StrictMath.asin((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void asinDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.asin().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::asin, Double64VectorTests::strictasin);
    }


    static double acos(double a) {
        return (double)(Math.acos((double)a));
    }

    static double strictacos(double a) {
        return (double)(StrictMath.acos((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void acosDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.acos().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::acos, Double64VectorTests::strictacos);
    }


    static double atan(double a) {
        return (double)(Math.atan((double)a));
    }

    static double strictatan(double a) {
        return (double)(StrictMath.atan((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void atanDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.atan().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::atan, Double64VectorTests::strictatan);
    }


    static double cbrt(double a) {
        return (double)(Math.cbrt((double)a));
    }

    static double strictcbrt(double a) {
        return (double)(StrictMath.cbrt((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void cbrtDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.cbrt().intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, r, Double64VectorTests::cbrt, Double64VectorTests::strictcbrt);
    }


    static double hypot(double a, double b) {
        return (double)(Math.hypot((double)a, (double)b));
    }

    static double stricthypot(double a, double b) {
        return (double)(StrictMath.hypot((double)a, (double)b));
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void hypotDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.hypot(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Double64VectorTests::hypot, Double64VectorTests::stricthypot);
    }


    static double pow(double a, double b) {
        return (double)(Math.pow((double)a, (double)b));
    }

    static double strictpow(double a, double b) {
        return (double)(StrictMath.pow((double)a, (double)b));
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void powDouble64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.pow(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Double64VectorTests::pow, Double64VectorTests::strictpow);
    }


    static double atan2(double a, double b) {
        return (double)(Math.atan2((double)a, (double)b));
    }

    static double strictatan2(double a, double b) {
        return (double)(StrictMath.atan2((double)a, (double)b));
    }

    @Test(dataProvider = "doubleBinaryOpProvider")
    static void atan2Double64VectorTests(IntFunction<double[]> fa, IntFunction<double[]> fb) {
        double[] a = fa.apply(SPECIES.length());
        double[] b = fb.apply(SPECIES.length()); 
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                DoubleVector<Shapes.S64Bit> bv = SPECIES.fromArray(b, i);
                av.atan2(bv).intoArray(r, i);
            }
        }

        assertArraysEqualsWithinOneUlp(a, b, r, Double64VectorTests::atan2, Double64VectorTests::strictatan2);
    }


    static double neg(double a) {
        return (double)(-((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void negDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.neg().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Double64VectorTests::neg);
    }

    @Test(dataProvider = "doubleUnaryOpMaskProvider")
    static void negMaskedDouble64VectorTests(IntFunction<double[]> fa,
                                                IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.neg(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Double64VectorTests::neg);
    }

    static double abs(double a) {
        return (double)(Math.abs((double)a));
    }

    @Test(dataProvider = "doubleUnaryOpProvider")
    static void absDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.abs().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Double64VectorTests::abs);
    }

    @Test(dataProvider = "doubleUnaryOpMaskProvider")
    static void absMaskedDouble64VectorTests(IntFunction<double[]> fa,
                                                IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.abs(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Double64VectorTests::abs);
    }





    static double sqrt(double a) {
        return (double)(Math.sqrt((double)a));
    }



    @Test(dataProvider = "doubleUnaryOpProvider")
    static void sqrtDouble64VectorTests(IntFunction<double[]> fa) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.sqrt().intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, Double64VectorTests::sqrt);
    }



    @Test(dataProvider = "doubleUnaryOpMaskProvider")
    static void sqrtMaskedDouble64VectorTests(IntFunction<double[]> fa,
                                                IntFunction<boolean[]> fm) {
        double[] a = fa.apply(SPECIES.length());
        double[] r = new double[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Double, Shapes.S64Bit> vmask = SPECIES.maskFromValues(mask);

        for (int ic = 0; ic < INVOC_COUNT; ic++) {
            for (int i = 0; i < a.length; i += SPECIES.length()) {
                DoubleVector<Shapes.S64Bit> av = SPECIES.fromArray(a, i);
                av.sqrt(vmask).intoArray(r, i);
            }
        }

        assertArraysEquals(a, r, mask, Double64VectorTests::sqrt);
    }

}

