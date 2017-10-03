/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6492694 8026567 8048351 8162363 8183511 8169819
 * @summary  Test package deprecation.
 * @author   bpatel
 * @library  ../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester TestPackageDeprecation
 * @run main TestPackageDeprecation
 */

public class TestPackageDeprecation extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestPackageDeprecation tester = new TestPackageDeprecation();
        tester.runTests();
    }

    @Test
    void testDefault() {
        javadoc("-d", "out-default",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", testSrc("C2.java"), testSrc("FooDepr.java"));
        checkExit(Exit.OK);

        checkOutput("pkg1/package-summary.html", true,
            "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated.</span>\n" +
            "<div class=\"block\"><span class=\"deprecationComment\">This package is Deprecated." +
            "</span></div>"
        );

        checkOutput("deprecated-list.html", true,
            "<li><a href=\"#package\">Packages</a></li>"
        );
    }

    @Test
    void testNoDeprecated() {
        javadoc("-d", "out-nodepr",
                "-sourcepath", testSrc,
                "-use",
                "-nodeprecated",
                "pkg", "pkg1", testSrc("C2.java"), testSrc("FooDepr.java"));
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", false,
                "pkg1");
        checkOutput("allclasses-frame.html", false,
                "FooDepr");
        checkOutput("class-use/C2.ModalExclusionType.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"#unnamed.package\">&lt;Unnamed&gt;</a></th>");

        checkFiles(false,
                "pkg1/package-summary.html",
                "FooDepr.html");
    }
}
