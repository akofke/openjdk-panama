/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.foreign.NativeTypes;
import java.foreign.Scope;
import java.foreign.annotations.NativeAddressof;
import java.foreign.annotations.NativeCallback;
import java.foreign.annotations.NativeGetter;
import java.foreign.annotations.NativeSetter;
import java.foreign.annotations.NativeStruct;
import java.foreign.memory.Array;
import java.foreign.memory.Callback;
import java.foreign.memory.Pointer;
import java.foreign.memory.Struct;

import static org.testng.Assert.*;

/*
 * @test
 * @run testng ArrayOverwriteTest
 */

public class ArrayOverwriteTest {

    @NativeStruct("[" +
            "[2i32](arr)" +
            "i32(x)" +
            "](MyStruct)")
    public interface MyStruct extends Struct<MyStruct> {
        @NativeGetter("arr")
        Array<Integer> arr$get();
        @NativeSetter("arr")
        void arr$set(Array<Integer> val);

        @NativeGetter("x")
        int x$get();
        @NativeSetter("x")
        void x$set(int val);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoFieldOverwrite() {
        try(Scope scope = Scope.globalScope().fork()) {
            MyStruct str = scope.allocateStruct(MyStruct.class);
            str.x$set(10);
            Array<Integer> ints = scope.allocateArray(NativeTypes.INT32, new int[] { 1, 2, 3 }); // 1 too big
            str.arr$set(ints); // should throw
        }
    }

}