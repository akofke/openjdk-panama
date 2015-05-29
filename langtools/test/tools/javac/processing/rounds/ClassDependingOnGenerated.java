/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038455
 * @summary Verify situation when a classfile depends on another type, which is missing and
 *          generated by an annotation processor, is handled properly
 * @library /tools/javac/lib/
 * @modules jdk.compiler/com.sun.tools.javac.code
 * @clean *
 * @build ClassWithSuperType ClassDependingOnGenerated JavacTestingAbstractProcessor
 * @clean SuperClass
 * @compile -processor ClassDependingOnGenerated ClassDependingOnGeneratedSource.java
 */

import java.io.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;
import com.sun.tools.javac.code.Symbol.CompletionFailure;

public class ClassDependingOnGenerated extends JavacTestingAbstractProcessor {
    int round = 1;
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {

        try {
            processingEnv.getElementUtils().getTypeElement("SuperClass");
        } catch (CompletionFailure cf) {
            cf.printStackTrace();
        }
        if (round++ == 1) {
            try (Writer out = filer.createSourceFile("SuperClass").openWriter()) {
                String code = "class SuperClass { public int get() { return 0; } }";
                out.write(code);
                out.close();
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
            }
        }

        return false;
    }

}
