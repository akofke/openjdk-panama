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

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.Vector;
import jdk.internal.misc.Unsafe;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test
 * @modules jdk.incubator.vector java.base/jdk.internal.misc
 * @run testng PreferredSpeciesTest
 */

public class PreferredSpeciesTest {
    static final Unsafe U = Unsafe.getUnsafe();

    @DataProvider
    public static Object[][] classesProvider() {
        return new Object[][]{
                {Byte.class, byte.class},
                {Short.class, short.class},
                {Integer.class, int.class},
                {Float.class, float.class},
                {Long.class, long.class},
                {Double.class, double.class},
        };
    }

    @Test(dataProvider = "classesProvider")
    void testVectorLength(Class<?> box, Class<?> prim) {
        Vector.Species<?, ?> species =
                Vector.preferredSpeciesInstance(box);

        Assert.assertEquals(species.length(), U.getMaxVectorSize(prim));
    }
}
