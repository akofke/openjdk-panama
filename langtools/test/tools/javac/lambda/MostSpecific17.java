/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8143852
 * @summary Rename functional interface method type parameters during most specific test
 * @compile MostSpecific17.java
 */
class MostSpecific17 {

    interface A<T> {}
    interface B<T> extends A<T> {}

    interface F1 { <X> A<? super X> apply(Object arg); }
    interface F2 { <Y> B<? super Y> apply(Object arg); }

    static void m1(F1 f) {}
    static void m1(F2 f) {}

    static B<Object> foo(Object in) { return null; }

    void test() {
        m1(MostSpecific17::foo);
    }

}