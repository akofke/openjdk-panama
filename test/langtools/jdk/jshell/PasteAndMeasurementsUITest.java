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

/**
 * @test
 * @bug 8182297
 * @summary Verify that pasting multi-line snippets works properly.
 * @library /tools/lib
 * @modules
 *     java.base/java.lang:open
 *     java.base/java.io:open
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build Compiler UITesting
 * @build PasteAndMeasurementsUITest
 * @run testng/othervm PasteAndMeasurementsUITest
 */

import java.io.Console;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.testng.annotations.Test;

@Test
public class PasteAndMeasurementsUITest extends UITesting {

    public void testPrevNextSnippet() throws Exception {
        System.setProperty(ANSI_SUPPORTED_PROPERTY, "true");
        Field cons = System.class.getDeclaredField("cons");
        cons.setAccessible(true);
        Constructor console = Console.class.getDeclaredConstructor();
        console.setAccessible(true);
        cons.set(null, console.newInstance());
        doRunTest((inputSink, out) -> {
            inputSink.write("void test1() {\nSystem.err.println(1);\n}\n" + LOC +
                            "void test2() {\nSystem.err.println(1);\n}\n" + LOC + LOC + LOC + LOC + LOC);
            waitOutput(out,       "\u001b\\[6nvoid test1\\(\\) \\{\n" +
                            "\u0006\u001b\\[6nSystem.err.println\\(1\\);\n" +
                            "\u0006\u001b\\[6n\\}\n" +
                            "\\|  created method test1\\(\\)\n" +
                            PROMPT + "\u001b\\[6nvoid test2\\(\\) \\{\n" +
                            "\u0006\u001b\\[6nSystem.err.println\\(1\\);\n" +
                            "\u0006\u001b\\[6n\\}\n" +
                            "\\|  created method test2\\(\\)\n" +
                            PROMPT + "\u001b\\[6n");
        });
    }
        private static final String ANSI_SUPPORTED_PROPERTY = "test.terminal.ansi.supported";
        private static final String LOC = "\033[12;1R";
}
