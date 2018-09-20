/*
 * Copyright (c) 2018, Google Inc. All rights reserved.
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
 */

/**
 * @test
 * @bug 8193037
 * @summary ensure annotations on package-infos loaded from the classpath are reported on all
 *     processing rounds
 * @library /tools/javac/lib
 * @modules java.compiler jdk.compiler
 * @build Processor
 * @compile package-info.java
 * @compile -processor Processor ClassAnnotations.java
 */
package p;

public class ClassAnnotations {}
