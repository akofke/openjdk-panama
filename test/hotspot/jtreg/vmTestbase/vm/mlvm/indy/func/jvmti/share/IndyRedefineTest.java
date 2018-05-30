/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.indy.func.jvmti.share;

import java.io.File;
import java.lang.reflect.Method;

import vm.mlvm.share.MlvmTest;
import vm.share.options.Option;

public class IndyRedefineTest extends MlvmTest {

    @Option(name="dummyClassName", description="Redefined class FQDN")
    private String dummyClassName = null;

    private static final int CYCLES_COUNT = 2;

    public boolean run() throws Throwable {
        IndyRedefineClass.setRedefinedClassFileName(dummyClassName.replace('.', File.separatorChar));
        IndyRedefineClass.setRedefineTriggerMethodName("redefineNow");

        Class<?> dummyClass = Class.forName(dummyClassName);
        Method mInvokeTarget = dummyClass.getDeclaredMethod("invokeTarget");
        Method mIsRedefinedClass = dummyClass.getDeclaredMethod("isRedefinedClass");

        for ( int i = 0; i < CYCLES_COUNT; i++ ) {
            if ( ! (Boolean) mInvokeTarget.invoke(null) )
                markTestFailed("Error: the original target method was called\n");

            if ( ! (Boolean) mIsRedefinedClass.invoke(null) )
                markTestFailed("Error: the class was not redefined\n");
        }

        if ( ! IndyRedefineClass.checkStatus() )
            markTestFailed("Error: failures in native code. Please see verbose log");

        return true;
    }

    public static void main(String[] args) {
        MlvmTest.launch(args);
    }
}
