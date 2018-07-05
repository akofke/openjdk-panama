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
 * @bug 8205418
 * @summary Test the outcomes from Trees.getScope
 * @modules jdk.compiler
 */

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Scope;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import static javax.tools.JavaFileObject.Kind.SOURCE;

public class TestGetScopeResult {
    public static void main(String... args) throws IOException {
        new TestGetScopeResult().run();
    }

    public void run() throws IOException {
        String[] simpleLambda = {
            "s:java.lang.String",
            "i:Test.I",
            "super:java.lang.Object",
            "this:Test"
        };
        doTest("class Test { void test() { I i = s -> { }; } interface I { public void test(String s); } }",
               simpleLambda);
        doTest("class Test { void test() { I i = s -> { }; } interface I { public int test(String s); } }",
               simpleLambda);
        doTest("class Test { void test() { I i = s -> { }; } interface I { public String test(String s); } }",
               simpleLambda);
        doTest("class Test { void test() { I i; inv(s -> { }); } void inv(I i) { } interface I { public void test(String s); } }",
               simpleLambda);
        doTest("class Test { void test() { I i; inv(s -> { }); } void inv(I i) { } interface I { public int test(String s); } }",
               simpleLambda);
        doTest("class Test { void test() { I i; inv(s -> { }); } void inv(I i) { } interface I { public String test(String s); } }",
               simpleLambda);
        String[] dualLambda = {
            "s:java.lang.String",
            "i:Test.I1",
            "super:java.lang.Object",
            "this:Test",
            "s:java.lang.CharSequence",
            "i:Test.I1",
            "super:java.lang.Object",
            "this:Test"
        };
        doTest("class Test { void test() { I1 i; inv(s -> { }, s -> { }); } void inv(I1 i, I2 i) { } interface I1 { public String test(String s); } interface I2 { public void test(CharSequence s); } }",
               dualLambda);
        doTest("class Test { void test() { I1 i; inv(s -> { }, s -> { }); } void inv(I1 i, I2 i) { } interface I1 { public String test(String s); } interface I2 { public int test(CharSequence s); } }",
               dualLambda);
        String[] brokenType = {
            "s:<any>",
            "u:Undefined",
            "super:java.lang.Object",
            "this:Test"
        };
        doTest("class Test { void test() { Undefined u = s -> { }; } }",
               brokenType);
        String[] multipleCandidates1 = {
            "s:<any>",
            "super:java.lang.Object",
            "this:Test"
        };
        doTest("class Test { void test() { cand1(s -> { }); } void cand1(I1 i) { } void cand1(I2 i) { } interface I1 { public String test(String s); } interface I2 { public int test(CharSequence s); } }",
               multipleCandidates1);
        String[] multipleCandidates2 = {
            "s:java.lang.String",
            "super:java.lang.Object",
            "this:Test"
        };
        doTest("class Test { void test() { cand1(s -> { }); } void cand1(I1 i) { } void cand1(I2 i, int i) { } interface I1 { public String test(String s); } interface I2 { public int test(CharSequence s); } }",
               multipleCandidates2);
    }

    public void doTest(String code, String... expected) throws IOException {
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = c.getStandardFileManager(null, null, null)) {
            class MyFileObject extends SimpleJavaFileObject {
                MyFileObject() {
                    super(URI.create("myfo:///Test.java"), SOURCE);
                }
                @Override
                public String getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            }
            JavacTask t = (JavacTask) c.getTask(null, fm, null, null, null, List.of(new MyFileObject()));
            CompilationUnitTree cut = t.parse().iterator().next();
            t.analyze();

            List<String> actual = new ArrayList<>();

            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
                    Scope scope = Trees.instance(t).getScope(new TreePath(getCurrentPath(), node.getBody()));
                    while (scope.getEnclosingClass() != null) {
                        for (Element el : scope.getLocalElements()) {
                            actual.add(el.getSimpleName() + ":" +el.asType().toString());
                        }
                        scope = scope.getEnclosingScope();
                    }
                    return super.visitLambdaExpression(node, p);
                }
            }.scan(cut, null);

            List<String> expectedList = List.of(expected);

            if (!expectedList.equals(actual)) {
                throw new IllegalStateException("Unexpected scope content: " + actual);
            }
        }
    }

}

