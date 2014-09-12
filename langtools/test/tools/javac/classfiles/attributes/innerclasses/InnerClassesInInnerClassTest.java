/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8034854 8042251
 * @summary Testing InnerClasses_attribute of inner classes in inner class.
 * @library /tools/lib /tools/javac/lib ../lib
 * @build InnerClassesInInnerClassTestBase InnerClassesTestBase TestResult TestBase InMemoryFileManager ToolBox
 * @run main InnerClassesInInnerClassTest
 */

import java.util.List;

public class InnerClassesInInnerClassTest extends InnerClassesInInnerClassTestBase {

    public static void main(String[] args) throws TestFailedException {
        InnerClassesTestBase test = new InnerClassesInInnerClassTest();
        test.test("InnerClassesSrc$Inner", "Inner", "1");
    }

    @Override
    public void setProperties() {
        setHasSyntheticClass(true);
        setOuterClassType(ClassType.CLASS);
        setInnerClassType(ClassType.CLASS);
    }

    @Override
    public List<TestCase> generateTestCases() {
        setForbiddenWithoutStaticInOuterMods(true);
        List<TestCase> sources = super.generateTestCases();

        setForbiddenWithoutStaticInOuterMods(false);
        setOuterOtherModifiers(Modifier.EMPTY, Modifier.ABSTRACT, Modifier.FINAL);
        setInnerOtherModifiers(Modifier.EMPTY, Modifier.ABSTRACT, Modifier.FINAL);
        sources.addAll(super.generateTestCases());

        return sources;
    }
}
