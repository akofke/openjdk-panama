/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.dynalink.beans.test;

import static jdk.dynalink.StandardOperation.CALL;
import static jdk.dynalink.StandardOperation.GET_ELEMENT;
import static jdk.dynalink.StandardOperation.GET_METHOD;
import static jdk.dynalink.StandardOperation.GET_PROPERTY;
import static jdk.dynalink.StandardOperation.SET_ELEMENT;
import static jdk.dynalink.StandardOperation.SET_PROPERTY;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.CompositeOperation;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.support.SimpleRelinkableCallSite;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BeansLinkerTest {
    public static class Bean1 {
        public final int answer = 42;

        public String getName() {
            return "bean1";
        }

        public String someMethod(final String x) {
            return x + "-foo";
        }
    }

    @Test
    public static void testPublicFieldPropertyUnnamedGetter() {
        testGetterPermutations(GET_PROPERTY, (op) -> Assert.assertEquals(42, call(op, new Bean1(), "answer")));
    }

    @Test
    public static void testPublicFieldPropertyNamedGetter() {
        testGetterPermutations(GET_PROPERTY, (op) -> Assert.assertEquals(42, call(named("answer", op), new Bean1())));
    }

    @Test
    public static void testGetterPropertyUnnamedGetter() {
        testGetterPermutations(GET_PROPERTY, (op) -> Assert.assertEquals("bean1", call(op, new Bean1(), "name")));
    }

    @Test
    public static void testGetterPropertyNamedGetter() {
        testGetterPermutations(GET_PROPERTY, (op) -> Assert.assertEquals("bean1", call(named("name", op), new Bean1())));
    }

    @Test
    public static void testMethodUnnamedGetter() {
        testGetterPermutations(GET_METHOD, (op) -> Assert.assertEquals("bar-foo", call(call(op, new Bean1(), "someMethod"), new Bean1(), "bar")));
    }

    @Test
    public static void testMethodNamedGetter() {
        testGetterPermutations(GET_METHOD, (op) -> Assert.assertEquals("bar-foo", call(call(named("someMethod", op), new Bean1()), new Bean1(), "bar")));
    }

    private static final Map<String, String> MAP1 = new HashMap<>();
    static {
        MAP1.put("foo", "bar");
    }

    @Test
    public static void testElementUnnamedGetter() {
        testGetterPermutations(GET_ELEMENT, (op) -> Assert.assertEquals("bar", call(op, MAP1, "foo")));
    }

    @Test
    public static void testElementNamedGetter() {
        testGetterPermutations(GET_ELEMENT, (op) -> Assert.assertEquals("bar", call(named("foo", op), MAP1)));
    }

    public static class Bean2 {
        public int answer;
        private String name;

        public void setName(final String name) {
            this.name = name;
        }
    }

    @Test
    public static void testUnnamedFieldSetter() {
        testSetterPermutations(SET_PROPERTY, (op) -> {
            final Bean2 bean2 = new Bean2();
            call(op, bean2, "answer", 12);
            Assert.assertEquals(bean2.answer, 12);
        });
    }

    @Test
    public static void testNamedFieldSetter() {
        testSetterPermutations(SET_PROPERTY, (op) -> {
            final Bean2 bean2 = new Bean2();
            call(named("answer", op), bean2, 14);
            Assert.assertEquals(bean2.answer, 14);
        });
    }

    @Test
    public static void testUnnamedPropertySetter() {
        testSetterPermutations(SET_PROPERTY, (op) -> {
            final Bean2 bean2 = new Bean2();
            call(op, bean2, "name", "boo");
            Assert.assertEquals(bean2.name, "boo");
        });
    }

    @Test
    public static void testNamedPropertySetter() {
        testSetterPermutations(SET_PROPERTY, (op) -> {
            final Bean2 bean2 = new Bean2();
            call(named("name", op), bean2, "blah");
            Assert.assertEquals(bean2.name, "blah");
        });
    }

    private static final Pattern GET_ELEMENT_THEN_PROPERTY_PATTERN = Pattern.compile(".*GET_ELEMENT.*GET_PROPERTY.*");

    @Test
    public static void testUnnamedElementAndPropertyGetter() {
        final Map<String, Object> map = new HashMap<>();
        map.put("empty", true);
        testGetterPermutations(GET_ELEMENT_THEN_PROPERTY_PATTERN, 4, (op) -> Assert.assertEquals(true, call(op, map, "empty")));
    }

    @Test
    public static void testNamedElementAndPropertyGetter() {
        final Map<String, Object> map = new HashMap<>();
        map.put("empty", true);
        testGetterPermutations(GET_ELEMENT_THEN_PROPERTY_PATTERN, 4, (op) -> Assert.assertEquals(true, call(named("empty", op), map)));
    }

    private static final Pattern GET_PROPERTY_THEN_ELEMENT_PATTERN = Pattern.compile(".*GET_PROPERTY.*GET_ELEMENT.*");

    @Test
    public static void testUnnamedPropertyAndElementGetter() {
        final Map<String, Object> map = new HashMap<>();
        map.put("empty", true);
        testGetterPermutations(GET_PROPERTY_THEN_ELEMENT_PATTERN, 4, (op) -> Assert.assertEquals(false, call(op, map, "empty")));
    }

    @Test
    public static void testNamedPropertyAndElementGetter() {
        final Map<String, Object> map = new HashMap<>();
        map.put("empty", true);
        testGetterPermutations(GET_PROPERTY_THEN_ELEMENT_PATTERN, 4, (op) -> Assert.assertEquals(false, call(named("empty", op), map)));
    }

    public static class MapWithProperty extends HashMap<String, Object> {
        private String name;

        public void setName(final String name) {
            this.name = name;
        }
    }

    @Test
    public static void testUnnamedPropertyAndElementSetter() {
        final MapWithProperty map = new MapWithProperty();
        map.put("name", "element");

        call(ops(SET_PROPERTY, SET_ELEMENT), map, "name", "property");
        Assert.assertEquals("property", map.name);
        Assert.assertEquals("element", map.get("name"));

        call(ops(SET_ELEMENT, SET_PROPERTY), map, "name", "element2");
        Assert.assertEquals("property", map.name);
        Assert.assertEquals("element2", map.get("name"));
    }

    private static Operation[] GETTER_PERMUTATIONS = new Operation[] {
        GET_PROPERTY,
        GET_METHOD,
        GET_ELEMENT,
        ops(GET_PROPERTY, GET_ELEMENT),
        ops(GET_PROPERTY, GET_METHOD),
        ops(GET_ELEMENT,  GET_PROPERTY),
        ops(GET_ELEMENT,  GET_METHOD),
        ops(GET_METHOD,   GET_PROPERTY),
        ops(GET_METHOD,   GET_ELEMENT),
        ops(GET_PROPERTY, GET_ELEMENT,  GET_METHOD),
        ops(GET_PROPERTY, GET_METHOD,   GET_ELEMENT),
        ops(GET_ELEMENT,  GET_PROPERTY, GET_METHOD),
        ops(GET_ELEMENT,  GET_METHOD,   GET_PROPERTY),
        ops(GET_METHOD,   GET_PROPERTY, GET_ELEMENT),
        ops(GET_METHOD,   GET_ELEMENT,  GET_PROPERTY),
    };

    private static Operation[] SETTER_PERMUTATIONS = new Operation[] {
        SET_PROPERTY,
        SET_ELEMENT,
        ops(SET_PROPERTY, SET_ELEMENT),
        ops(SET_ELEMENT,  SET_PROPERTY)
    };

    private static void testPermutations(final Operation[] ops, final StandardOperation requiredOp, final int expectedCount, final Consumer<Operation> test) {
        testPermutationsWithFilter(ops, (op)->CompositeOperation.contains(op, requiredOp), expectedCount, test);
    }

    private static void testPermutations(final Operation[] ops, final Pattern regex, final int expectedCount, final Consumer<Operation> test) {
        testPermutationsWithFilter(ops, (op)->regex.matcher(op.toString()).matches(), expectedCount, test);
    }

    private static void testPermutationsWithFilter(final Operation[] ops, final Predicate<Operation> filter, final int expectedCount, final Consumer<Operation> test) {
        final int[] counter = new int[1];
        Stream.of(ops).filter(filter).forEach((op)-> { counter[0]++; test.accept(op); });
        Assert.assertEquals(counter[0], expectedCount);
    }

    private static void testGetterPermutations(final StandardOperation requiredOp, final Consumer<Operation> test) {
        testPermutations(GETTER_PERMUTATIONS, requiredOp, 11, test);
    }

    private static void testGetterPermutations(final Pattern regex, final int expectedCount, final Consumer<Operation> test) {
        testPermutations(GETTER_PERMUTATIONS, regex, expectedCount, test);
    }

    private static void testSetterPermutations(final StandardOperation requiredOp, final Consumer<Operation> test) {
        testPermutations(SETTER_PERMUTATIONS, requiredOp, 3, test);
    }

    private static Object call(final Operation op, final Object... args) {
        try {
            return new DynamicLinkerFactory().createLinker().link(
                    new SimpleRelinkableCallSite(new CallSiteDescriptor(
                            MethodHandles.publicLookup(), op, t(args.length))))
                            .dynamicInvoker().invokeWithArguments(args);
        } catch (final Error|RuntimeException e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static Object call(final Object... args) {
        return call(CALL, args);
    }

    private static Operation named(final Object name, final Operation... ops) {
        return new NamedOperation(ops(ops), name);
    }

    private static Operation ops(final Operation... ops) {
        return ops.length == 1 ? ops[0] : new CompositeOperation(ops);
    }

    private static MethodType t(final int argCount) {
        return MethodType.methodType(Object.class, Collections.nCopies(argCount, Object.class));
    }
}
