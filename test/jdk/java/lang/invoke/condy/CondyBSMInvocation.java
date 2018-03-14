/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8186046
 * @summary Test basic invocation of bootstrap methods
 * @library /java/lang/invoke/common
 * @modules java.base/jdk.experimental.bytecode
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run testng CondyBSMInvocation
 * @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 CondyBSMInvocation
 */


import org.testng.Assert;
import org.testng.annotations.Test;
import test.java.lang.invoke.lib.InstructionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Collections;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodType.methodType;

public class CondyBSMInvocation {
    static final MethodHandles.Lookup L = MethodHandles.lookup();


    @Test
    public void testNonexistent() throws Throwable {
        MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                L, "name", Object.class,
                "bsm", methodType(Object.class),
                S -> {});

        try {
            mh.invoke();
            Assert.fail("NoSuchMethodError expected to be thrown");
        } catch (NoSuchMethodError e) {
        }
    }


    public static Object _bsm() {
        return "0";
    }

    public static Object _bsm(Object a1) {
        return "0";
    }

    // Note: when pull mode is supported for a BSM this test case
    //       will fail and must be removed
    public static Object _bsm(Object a1, Object a2) {
        return "0";
    }

    @Test
    public void testWrongArity() throws Throwable {
        for (int i = 0; i < 3; i++) {
            final int n = i;
            MethodType mt = methodType(Object.class)
                    .appendParameterTypes(Collections.nCopies(n, Object.class));
            MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                    L, "name", Object.class,
                    "_bsm", mt,
                    S -> IntStream.range(0, n).forEach(S::add)
            );

            try {
                Object r = mh.invoke();
                Assert.fail("BootstrapMethodError expected to be thrown for arrity " + n);
            } catch (BootstrapMethodError e) {
                Throwable t = e.getCause();
                Assert.assertTrue(WrongMethodTypeException.class.isAssignableFrom(t.getClass()));
            }
        }
    }


    public static Object _bsm(String[] ss) {
        return "0";
    }

    public static Object _bsm(String a1, String a2, String a3) {
        return "0";
    }

    @Test
    public void testWrongSignature() throws Throwable {
        {
            MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                    L, "name", Object.class,
                    "_bsm", methodType(Object.class, String[].class),
                    S -> {}
            );

            try {
                Object r = mh.invoke();
                Assert.fail("BootstrapMethodError expected to be thrown");
            }
            catch (BootstrapMethodError e) {
                Throwable t = e.getCause();
                Assert.assertTrue(WrongMethodTypeException.class.isAssignableFrom(t.getClass()));
            }
        }

        {
            MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                    L, "name", Object.class,
                    "_bsm", methodType(Object.class, String.class, String.class, String.class),
                    S -> {}
            );

            try {
                Object r = mh.invoke();
                Assert.fail("BootstrapMethodError expected to be thrown");
            }
            catch (BootstrapMethodError e) {
                Throwable t = e.getCause();
                Assert.assertTrue(ClassCastException.class.isAssignableFrom(t.getClass()));
            }
        }
    }


    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type) {
        return "0";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1) {
        assertAll(a1);
        return "1";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2) {
        assertAll(a1, a2);
        return "2";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2, Object a3) {
        assertAll(a1, a2, a3);
        return "3";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2, Object a3, Object a4) {
        assertAll(a1, a2, a3, a4);
        return "4";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2, Object a3, Object a4, Object a5) {
        assertAll(a1, a2, a3, a4, a5);
        return "5";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) {
        assertAll(a1, a2, a3, a4, a5, a6);
        return "6";
    }

    public static Object bsm(MethodHandles.Lookup l, String name, Class<?> type,
                             Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) {
        assertAll(a1, a2, a3, a4, a5, a6, a7);
        return "7";
    }

    static void assertAll(Object... as) {
        for (int i = 0; i < as.length; i++) {
            Assert.assertEquals(as[i], i);
        }
    }

    @Test
    public void testArity() throws Throwable {
        for (int i = 0; i < 8; i++) {
            final int n = i;
            MethodType mt = methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class)
                    .appendParameterTypes(Collections.nCopies(n, Object.class));
            MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                    L, "name", Object.class,
                    "bsm", mt,
                    S -> IntStream.range(0, n).forEach(S::add)
                    );

            Object r = mh.invoke();
            Assert.assertEquals(r, Integer.toString(n));
        }
    }

    @Test
    public void testWrongNumberOfStaticArguments() throws Throwable {
        for (int i = 1; i < 8; i++) {
            final int n = i;
            MethodType mt = methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class)
                    .appendParameterTypes(Collections.nCopies(n, Object.class));
            MethodHandle mh = InstructionHelper.ldcDynamicConstant(
                    L, "name", Object.class,
                    "bsm", mt,
                    S -> IntStream.range(0, n - 1).forEach(S::add)
            );

            try {
                Object r = mh.invoke();
                Assert.fail("BootstrapMethodError expected to be thrown for arrity " + n);
            } catch (BootstrapMethodError e) {
                Throwable t = e.getCause();
                Assert.assertTrue(WrongMethodTypeException.class.isAssignableFrom(t.getClass()));
            }
        }
    }
}
