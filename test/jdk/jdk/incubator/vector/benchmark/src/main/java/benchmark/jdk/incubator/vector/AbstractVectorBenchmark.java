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
 * or visit www.oracle.com if you need additional information or have
 * questions.
 */

package benchmark.jdk.incubator.vector;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Random;
import java.util.function.IntFunction;

import jdk.incubator.vector.Shapes;
import jdk.incubator.vector.Vector;

public class AbstractVectorBenchmark {

    static final Random RANDOM = new Random(Integer.getInteger("jdk.incubator.vector.random-seed", 1337));

    boolean[] fillMask(int size, IntFunction<Boolean> f) {
        boolean[] array = new boolean[size];
        for (int i = 0; i < array.length; i++) {
            array[i] = f.apply(i);
        }
        return array;
    }

    int[] fillInt(int size, IntFunction<Integer> f) {
        int[] array = new int[size];
        for (int i = 0; i < array.length; i++) {
            array[i] = f.apply(i);
        }
        return array;
    }

    static Vector.Shape getMaxBitShape() {
        try {
            Class<?> clazz = Class.forName("jdk.incubator.vector.Shapes$SMaxBit");
            VarHandle privateHandle = MethodHandles.privateLookupIn(Shapes.class, MethodHandles.lookup())
                    .findStaticVarHandle(Shapes.class, "S_Max_BIT", clazz);
            return (Vector.Shape)(privateHandle.get());
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            throw new Error(e);
        }
    }
}
