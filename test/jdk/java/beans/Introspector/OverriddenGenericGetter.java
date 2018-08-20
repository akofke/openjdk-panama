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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

/**
 * @test
 * @bug 8196373
 * @summary Introspector should work with overridden generic getter method
 */
public final class OverriddenGenericGetter {

    static class Parent<T> {
        private T value;
        public T getValue() {return value;}
        public final void setValue(T value) {this.value = value;}
    }

    static class ChildO extends Parent<Object> {
        public ChildO() {}
        @Override
        public Object getValue() {return super.getValue();}
    }

    static class ChildA extends Parent<ArithmeticException> {
        public ChildA() {}
        @Override
        public ArithmeticException getValue() {return super.getValue();}
    }

    static class ChildS extends Parent<String> {
        public ChildS() {}
        @Override
        public String getValue() {return super.getValue();}
    }

    public static void main(String[] args) throws Exception {
        testBehaviour(ChildA.class);
        testBehaviour(ChildO.class);
        testBehaviour(ChildS.class);
        test(ChildA.class, ArithmeticException.class);
        test(ChildO.class, Object.class);
        test(ChildS.class, String.class);
    }

    private static void testBehaviour(Class<?> beanClass) throws Exception {
        BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Object.class);
        PropertyDescriptor pd = beanInfo.getPropertyDescriptors()[0];
        Object bean = beanClass.getConstructor().newInstance();
        Method readMethod = pd.getReadMethod();
        Method writeMethod = pd.getWriteMethod();

        // check roundtrip for default null values
        writeMethod.invoke(bean,readMethod.invoke(bean));
        writeMethod.invoke(bean,readMethod.invoke(bean));

        // set property to non-default value
        // check roundtrip for non-default values
        Object param = pd.getPropertyType().getConstructor().newInstance();
        writeMethod.invoke(bean, param);
        writeMethod.invoke(bean, readMethod.invoke(bean));
        writeMethod.invoke(bean, readMethod.invoke(bean));
    }

    private static void test(Class<?> beanClass, Class<?> type) throws Exception {
        BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Object.class);
        PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
        if (pds.length != 1) {
            throw new RuntimeException("Wrong number of properties");
        }
        PropertyDescriptor pd = pds[0];
        String name = pd.getName();
        if (!name.equals("value")) {
            throw new RuntimeException("Wrong name: " + name);
        }

        Class<?> propertyType = pd.getPropertyType();
        if (propertyType != type) {
            throw new RuntimeException("Wrong property type: " + propertyType);
        }
        Method readMethod = pd.getReadMethod();
        if (readMethod == null) {
            throw new RuntimeException("Read method is null");
        }
        Class<?> returnType = readMethod.getReturnType();
        if (returnType != type) {
            throw new RuntimeException("Wrong return type; " + returnType);
        }
        Method writeMethod = pd.getWriteMethod();
        if (writeMethod == null) {
            throw new RuntimeException("Write method is null");
        }
        Class<?>[] parameterTypes = writeMethod.getParameterTypes();
        if (parameterTypes.length != 1) {
            throw new RuntimeException("Wrong parameters " + parameterTypes);
        }
        if (parameterTypes[0] != Object.class) {
            throw new RuntimeException("Wrong type: " + parameterTypes[0]);
        }
    }
}
