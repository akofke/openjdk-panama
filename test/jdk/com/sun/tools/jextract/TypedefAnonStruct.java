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

package com.acme;

import java.foreign.annotations.NativeHeader;
import java.foreign.annotations.NativeLocation;
import java.foreign.annotations.NativeStruct;
import java.foreign.memory.Struct;
import java.foreign.memory.Pointer;

@NativeHeader(path="TypedefAnonStruct.h")
public interface TypedefAnonStruct {
    @NativeLocation(
      file="TypedefAnonStruct.h", line=24, column=9, USR="c:@SA@Point"
    )
    @NativeStruct("[" +
            "i32(get=i$get)(set=i$set)(ptr=i$ptr)" +
            "i32(get=j$get)(set=j$set)(ptr=j$ptr)" +
            "](Point)")
    public static interface Point extends Struct<Point> {
        @NativeLocation(
          file="TypedefAnonStruct.h", line=25, column=9, USR="c:@SA@Point@FI@i"
        )
        public int i$get();
        public void i$set(int arg);
        public Pointer<Integer> i$ptr();
        @NativeLocation(
          file="TypedefAnonStruct.h", line=25, column=12, USR="c:@SA@Point@FI@j"
        )
        public int j$get();
        public void j$set(int arg);
        public Pointer<Integer> j$ptr();
    }

    @NativeLocation(
      file="TypedefAnonStruct.h", line=28, column=9, USR="c:@SA@FPoint"
    )
    @NativeStruct("[" +
            "f32(get=x$get)(set=x$set)(ptr=x$ptr)" +
            "f32(get=y$get)(set=y$set)(ptr=y$ptr)" +
            "](FPoint)")
    public static interface FPoint extends Struct<FPoint> {
        @NativeLocation(
          file="TypedefAnonStruct.h", line=29, column=11, USR="c:@SA@FPoint@FI@x"
        )
        public float x$get();
        public void x$set(float arg);
        public Pointer<Float> x$ptr();
        @NativeLocation(
          file="TypedefAnonStruct.h", line=29, column=14, USR="c:@SA@FPoint@FI@y"
        )
        public float y$get();
        public void y$set(float arg);
        public Pointer<Float> y$ptr();
    }
}
