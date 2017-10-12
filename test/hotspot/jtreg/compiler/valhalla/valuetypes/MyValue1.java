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

package compiler.valhalla.valuetypes;

__ByValue public final class MyValue1 {
    static int s;
    static final long sf = ValueTypeTest.rL;
    final int x;
    final long y;
    final short z;
    final Integer o;
    final int[] oa;
    final MyValue2 v1;
    final MyValue2 v2;
    static final MyValue2 v3 = MyValue2.createWithFieldsInline(ValueTypeTest.rI, true);
    final int c;

    private MyValue1() {
        s = 0;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.o = null;
        this.oa = null;
        this.v1 = MyValue2.createDefaultInline();
        this.v2 = MyValue2.createDefaultInline();
        this.c = 0;
    }

    @DontInline
    __ValueFactory static MyValue1 createDefaultDontInline() {
        return createDefaultInline();
    }

    @ForceInline
    __ValueFactory static MyValue1 createDefaultInline() {
        return __MakeDefault MyValue1();
    }

    @DontInline
    static MyValue1 createWithFieldsDontInline(int x, long y) {
        return createWithFieldsInline(x, y);
    }

    @ForceInline
    static MyValue1 createWithFieldsInline(int x, long y) {
        MyValue1 v = createDefaultInline();
        v = setX(v, x);
        v = setY(v, y);
        v = setZ(v, (short)x);
        v = setO(v, new Integer(x));
        int[] oa = {x};
        v = setOA(v, oa);
        v = setV1(v, MyValue2.createWithFieldsInline(x, true));
        v = setV2(v, MyValue2.createWithFieldsInline(x, false));
        v = setC(v, ValueTypeTest.rI);
        return v;
    }

    // Hash only primitive and value type fields to avoid NullPointerException
    @ForceInline
    public long hashPrimitive() {
        return s + sf + x + y + z + c + v1.hash() + v2.hash() + v3.hash();
    }

    @ForceInline
    public long hash() {
        return hashPrimitive() + o + oa[0];
    }

    @DontCompile
    public long hashInterpreted() {
        return s + sf + x + y + z + o + oa[0] + c + v1.hashInterpreted() + v2.hashInterpreted() + v3.hashInterpreted();
    }

    @ForceInline
    public void print() {
        System.out.print("s=" + s + ", sf=" + sf + ", x=" + x + ", y=" + y + ", z=" + z + ", o=" + (o != null ? (Integer)o : "NULL") + ", oa=" + oa[0] + ", v1[");
        v1.print();
        System.out.print("], v2[");
        v2.print();
        System.out.print("], v3[");
        v3.print();
        System.out.print("], c=" + c);
    }

    @ForceInline
    __ValueFactory static MyValue1 setX(MyValue1 v, int x) {
        v.x = x;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setY(MyValue1 v, long y) {
        v.y = y;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setZ(MyValue1 v, short z) {
        v.z = z;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setO(MyValue1 v, Integer o) {
        v.o = o;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setOA(MyValue1 v, int[] oa) {
        v.oa = oa;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setC(MyValue1 v, int c) {
        v.c = c;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setV1(MyValue1 v, MyValue2 v1) {
        v.v1 = v1;
        return v;
    }

    @ForceInline
    __ValueFactory static MyValue1 setV2(MyValue1 v, MyValue2 v2) {
        v.v2 = v2;
        return v;
    }
}
