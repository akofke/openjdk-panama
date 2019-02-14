/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @run testng FunctionAccessTest
 * @run testng/othervm -Djdk.internal.foreign.UpcallHandler.FASTPATH=none -Djdk.internal.foreign.NativeInvoker.FASTPATH=none FunctionAccessTest
 */

import java.foreign.annotations.NativeGetter;
import java.foreign.annotations.NativeSetter;
import java.foreign.memory.*;
import java.lang.invoke.MethodHandles;
import java.foreign.Libraries;
import java.foreign.Library;
import java.foreign.Scope;
import java.foreign.annotations.NativeCallback;
import java.foreign.annotations.NativeHeader;
import java.foreign.annotations.NativeStruct;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class FunctionAccessTest {

    @Test
    public void testFunctionField() {
        Callback<NativeToIntFunction> funcOutsideScope = null;
        try (Scope s = Scope.globalScope().fork()) {
            MyStruct m = s.allocateStruct(MyStruct.class);
            NativeToIntFunction f = () -> 42;
            m.setFunction(s.allocateCallback(f));
            Callback<NativeToIntFunction> func = m.getFunction();
            //do a roundtrip
            m.setFunction(func);
            System.gc();
            func = m.getFunction();

            //check resource is still there
            assertEquals(func.asFunction().get(), 42);
            funcOutsideScope = func;
            //check that calling get twice yields same object we started with
            m.setFunction(func);
            assertEquals(func.asFunction().get(), 42);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        try {
             funcOutsideScope.asFunction(); // should throw!
             fail("exception not thrown!");
         } catch (IllegalStateException ex) {
             assertTrue(ex.getMessage().contains("Scope is not alive"));
         }
    }

    @NativeStruct("[" +
            "   u64(function):()i32" +
            "](MyStruct)")
    public interface MyStruct extends Struct<MyStruct> {
        @NativeGetter("function")
        Callback<NativeToIntFunction> getFunction();
        @NativeSetter("function")
        void setFunction(Callback<NativeToIntFunction> runnable);
    }

    @NativeCallback("()i32")
    public interface NativeToIntFunction {
        int get();
    }

    @Test
    public void testFunctionGlobal() {
        Library lib = Libraries.loadLibrary(MethodHandles.lookup(), "GlobalFunc");
        globalFunc gf = Libraries.bind(globalFunc.class, lib);
        Scope sc = Libraries.libraryScope(gf);
        assertEquals(gf.fp().asFunction().f(8), 64);
        gf.setFp(sc.allocateCallback(x -> x / 2));
        System.gc();
        assertEquals(gf.fp().asFunction().f(8), 4);
    }

    @NativeHeader(globals = "u64(fp):(i32)i32")
    public interface globalFunc {
        @NativeGetter("fp")
        Callback<Func> fp();
        @NativeSetter("fp")
        void setFp(Callback<Func> func);

        @NativeCallback("(i32)i32")
        interface Func {
            int f(int i);
        }
    }
}
