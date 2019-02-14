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

/*
* @test
* @run testng ArrayTest
*/

import java.foreign.*;
import java.foreign.memory.*;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class ArrayTest {

    @Test
    public void testIterate() {
        try(var scope = Scope.globalScope().fork()) {
            Array<Integer> array = scope.allocateArray(NativeTypes.INT32, 10);
            assertEquals(array.iterate().count(), 10);
            Pointer<Integer> last = array.elementPointer().offset(array.length());
            assertEquals(array.elementPointer().iterate(last).count(), 10);
            assertEquals(array.elementPointer().iterate(p -> !p.equals(last)).count(), 10);
        }
    }

}
