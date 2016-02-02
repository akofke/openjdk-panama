/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

package pkg;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @deprecated interface_test1 passes.
 */
public class TestInterface {

    /**
     * @deprecated interface_test2 passes.
     */
    public int field;

    /**
     * @deprecated interface_test3 passes.
     */
    public TestInterface() {}

    /**
     * @deprecated interface_test4 passes.
     */
    public void method() {}

    /**
     * @deprecated methodA
     */
    public void methodA(Class<?> a) {}

    /**
     * @deprecated methodB
     */
    public void methodB(List<String> list){}

    /**
     * @deprecated methodC
     */
    public void methodC(List<List<String>> list){}
    /**
     * @deprecated methodD
     */
    public void methodD(List<? extends Iterator<String>> list){}
    /**
     * @deprecated methodE
     */
    public void methodE(Map<Integer, String> map){}
    /**
     * @deprecated methodF
     */
    public void methodF(List<? super Integer> list){}
}
