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

/*
 * @test
 * @library ..
 * @modules jdk.jextract
 * @build IncompleteArrayTest
 *
 * @run testng/othervm IncompleteArrayTest
 */

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.nio.file.Path;

public class IncompleteArrayTest extends JextractToolRunner {

    static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @Test
    public void testIncompleteArraySimple() {
        Path jar = getOutputFilePath("incompleteArray1.jar");
        Path input = getInputFilePath("incompleteArray1.h");
        run("-o", jar.toString(),
                input.toString())
                .checkSuccess()
                .checkMatchesOutput(".*WARNING: can not compute layout for type .+" +
                        " with flexible array member\\. Emitting undefined layout reference\\..*");
        try (Loader loader = classLoader(jar)) {
            // check if we can still load the class without NoClassDefFound
            assertNotNull(loader.loadClass("incompleteArray1"));
        } finally {
            deleteFile(jar);
        }
    }

    @Test
    public void testIncompleteArrayNested() {
        Path jar = getOutputFilePath("incompleteArray2.jar");
        Path input = getInputFilePath("incompleteArray2.h");
        run("-o", jar.toString(),
                input.toString())
                .checkSuccess()
                .checkMatchesOutput(".*WARNING: can not compute layout for type .+" +
                        " with flexible array member\\. Emitting undefined layout reference\\..*");
        try (Loader loader = classLoader(jar)) {
            assertNotNull(loader.loadClass("incompleteArray2"));
        } finally {
            deleteFile(jar);
        }
    }

    @Test
    public void testIncompleteArrayUnion() {
        if (IS_WINDOWS) { // MSVC extension
            Path jar = getOutputFilePath("incompleteArray3.jar");
            Path input = getInputFilePath("incompleteArray3.h");
            run("-o", jar.toString(),
                    input.toString())
                    .checkSuccess()
                    .checkMatchesOutput(".*WARNING: can not compute layout for type .+" +
                            " with flexible array member\\. Emitting undefined layout reference\\..*");
            try (Loader loader = classLoader(jar)) {
                assertNotNull(loader.loadClass("incompleteArray3"));
            } finally {
                deleteFile(jar);
            }
        }
    }

}
