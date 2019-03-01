/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.foreign.annotations.*;
import java.nio.file.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.tools.jextract.JextractTool;
import com.sun.tools.jextract.JarWriter;
import com.sun.tools.jextract.Log;
import com.sun.tools.jextract.Options;
import com.sun.tools.jextract.Writer;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.util.ASMifier;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.internal.org.objectweb.asm.util.Textifier;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import com.sun.tools.jextract.Context;

/*
 * @test
 * @summary Main test runner created all cases
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @modules java.base/jdk.internal.org.objectweb.asm.tree
 * @modules java.base/jdk.internal.org.objectweb.asm.util
 * @modules jdk.jextract/com.sun.tools.jextract
 * @build InMemoryFileManager
 * @run testng Runner
 */
public class Runner {
    private final Path[] javaSrcFiles;
    private final Context ctx;

    private InMemoryFileManager<StandardJavaFileManager> mfm;
    private ClassLoader expectedCL;
    private Map<String, byte[]> actualClz;
    private ClassLoader actualCL;
    private Writer writer;
    private Object[][] clz_data;

    public Runner(Path nativeSrc, String pkg, Path[] javaSrcFiles) {
        this.javaSrcFiles = javaSrcFiles;
        this.ctx = createContext(nativeSrc, pkg);
    }

    private static Context createContext(Path nativeSrc, String pkg) {
        if (!Files.isReadable(nativeSrc)) {
            throw new IllegalArgumentException("Cannot read the file: " + nativeSrc);
        }
        nativeSrc = nativeSrc.toAbsolutePath();
        Options.Builder options = Options.builder();
        options.setTargetPackage(pkg);
        return new Context(List.of(nativeSrc.toAbsolutePath()), options.build(), Log.createDefault());
    }

    private Writer extract() throws IOException {
        return new JextractTool(ctx).processHeaders();
    }

    private InMemoryFileManager<StandardJavaFileManager> compileJavaCode() {
        JavaCompiler cl = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager sfm = cl.getStandardFileManager(null, null, null);
        InMemoryFileManager<StandardJavaFileManager> fm = new InMemoryFileManager<>(sfm);
        JavaCompiler.CompilationTask task = cl.getTask(null, fm, null, null,
                null, sfm.getJavaFileObjects(javaSrcFiles));
        task.call();
        return fm;
    }

    @Test
    public void testJarManifest() throws IOException {
        // Get the jar
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new JarWriter(ctx, writer).writeJarFile(new JarOutputStream(bos), new String[0]);

        System.out.println("Jar built, verifying...");
        JarInputStream jis = new JarInputStream(new ByteArrayInputStream(bos.toByteArray()));

        // List all classes in the jar
        Set<String> files = new HashSet<>();
        for (JarEntry e = jis.getNextJarEntry(); e != null; e = jis.getNextJarEntry()) {
            if (e.isDirectory()) {
                continue;
            }
            String name = e.getName();
            if (! name.endsWith(".properties")) {
                if (! name.endsWith(".class")) {
                    // Should not have file not class files
                    System.err.println("Warning: unexpected file " + name);
                }
                name = name.substring(0, name.length() - 6);
                files.add(normalize(name));
            }
        }

        assertEquals(files, mfm.listClasses());
    }

    private void verifyNativeLocation(NativeLocation actual, NativeLocation expected) {
        // Only check the filename, not full path
        assertNotNull(actual);
        assertNotNull(expected);
        assertEquals(Paths.get(actual.file()).getFileName(),
                     Paths.get(expected.file()).getFileName());
        assertEquals(actual.line(), expected.line());
        assertEquals(actual.column(), expected.column());
    }

    private void verifyMethodAnnotation(Method actual, Method expected) {
        Annotation[] ea = expected.getAnnotations();

        for (Annotation a: ea) {
            if (a instanceof NativeLocation) {
                verifyNativeLocation(actual.getAnnotation(NativeLocation.class), (NativeLocation) a);
            } else {
                @SuppressWarnings("unchecked")
                Annotation e = actual.getAnnotation((Class)a.getClass().getInterfaces()[0]);
                assertNotNull(e);
                assertEquals(a, e);
            }
        }
    }

    @Test(dataProvider = "classes")
    public void testMethods(Class<?> actual, Class<?> expected) {
        Method[] am = actual.getMethods();
        Method[] em = expected.getMethods();
        HashMap<String, Method> ams = new HashMap<>();
        for (Method m: am) {
            ams.put(m.toGenericString(), m);
        }
        HashMap<String, Method> ems = new HashMap<>();
        for (Method m: em) {
            String sig = m.toGenericString();
            Method ma = ams.remove(sig);
            if (ma == null) {
                System.out.println("Missing " + sig);
                ems.put(m.toGenericString(), m);
            } else {
                assertEquals(ma.isVarArgs(), m.isVarArgs());
                try {
                    verifyMethodAnnotation(ma, m);
                } catch (Throwable t) {
                    fail("Failed method " + sig, t);
                }
            }
        }
        ams.keySet().forEach((sig) -> {
            System.out.println("Got: " + sig);
        });
        assertTrue(ams.isEmpty());
        assertTrue(ems.isEmpty());
    }

    @Test(dataProvider = "classes")
    public void testAnnotations(Class<?> actual, Class<?> expected) {
        Annotation[] aa = actual.getAnnotations();
        Annotation[] ea = expected.getAnnotations();

        if (actual.getName().contains("$")) {
            assertTrue(actual.getName().contains("$"));
            assertTrue(expected.isMemberClass());
            assertTrue(actual.isMemberClass());
            if (actual.isAnnotationPresent(NativeStruct.class)) {
                NativeStruct ant = actual.getAnnotation(NativeStruct.class);
                assertNotNull(ant);
                assertEquals(ant, expected.getAnnotation(NativeStruct.class));
            } else if (actual.isAnnotationPresent(NativeFunction.class)) {
                NativeFunction cb = actual.getAnnotation(NativeFunction.class);
                assertNotNull(cb);
                assertEquals(cb, expected.getAnnotation(NativeFunction.class));
            }

            if (expected.isAnnotationPresent(NativeLocation.class)) {
                NativeLocation loc = actual.getAnnotation(NativeLocation.class);
                assertNotNull(loc);
                verifyNativeLocation(loc, expected.getAnnotation(NativeLocation.class));
            }
        } else {
            NativeHeader ah = actual.getAnnotation(NativeHeader.class);
            assertNotNull(ah);
            NativeHeader eh = expected.getAnnotation(NativeHeader.class);
            assertNotNull(eh);

            assertEquals(Paths.get(ah.path()).getFileName(),
                    Paths.get(eh.path()).getFileName());
        }

    }

    private void AsmCheckClass(String name) {
        ClassReader cr = new ClassReader(actualClz.get(name));
        PrintWriter pw = new PrintWriter(System.out);
        TraceClassVisitor tcv = new TraceClassVisitor(
                new CheckClassAdapter(new ClassNode()),
                new Textifier(), pw);
        try {
            cr.accept(tcv, ClassReader.SKIP_FRAMES);
        } catch (Throwable e) {
            tcv.p.print(pw);
            e.printStackTrace(pw);
        }
        pw.flush();
    }

    private void AsmifyClass(String name) throws IOException {
        ClassReader cr = new ClassReader(mfm.getClassData(name));
        PrintWriter pw = new PrintWriter(System.out);
        TraceClassVisitor tcv = new TraceClassVisitor(
                new CheckClassAdapter(new ClassNode()),
                new ASMifier(), pw);
        try {
            cr.accept(tcv, ClassReader.SKIP_FRAMES);
        } catch (Throwable e) {
            tcv.p.print(pw);
            e.printStackTrace(pw);
        }
        pw.flush();
    }

    @DataProvider(name = "classes")
    public Object[][] loadClasses() throws ClassNotFoundException, IOException {
        // compile();
        if (clz_data == null) {
            String[] clz_names = mfm.listClasses().toArray(new String[0]);
            final int len = clz_names.length;
            clz_data = new Object[len][2];
            for (int i = 0; i < len; i++) {
                String name = clz_names[i];
                System.err.println("Load class " + name);
                // AsmCheckClass(name);
                // AsmifyClass(name);
                clz_data[i][0] = actualCL.loadClass(name);
                clz_data[i][1] = expectedCL.loadClass(name);
            }
        }
        return clz_data;
    }

    @BeforeClass
    public void compile() throws IOException {
        System.out.println("Compiling...");
        mfm = compileJavaCode();
        writer = extract();
        actualClz = writer.results();
        expectedCL = mfm.getTheClassLoader();
        actualCL = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] byteCode = actualClz.get(canonicalize(name));
                if (byteCode == null) throw new ClassNotFoundException(name);
                return defineClass(name, byteCode, 0, byteCode.length);
            }
        };
        System.out.println("Done compile, ready for test");
        assertEquals(actualClz.keySet().stream()
                        .map(Runner::normalize)
                        .collect(Collectors.toSet()),
                mfm.listClasses());
        System.out.println("Compile result validated.");
    }

    private static String normalize(String classname) {
        return classname.replace('/', '.');
    }

    private static String canonicalize(String classname) {
        return classname.replace('.', '/');
    }

    private static Path[] paths(String testDir, String[] files, boolean platformDependent) {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        return Arrays.stream(files)
                .map(f -> Paths.get(testDir, "compare", isWindows && platformDependent ? "windows" : "", f))
                .toArray(Path[]::new);
    }

    @Factory(dataProvider = "cases")
    public static Object[] run(String nativeSrc, String pkgName, String[] javaSrcFiles, boolean platformDependent) {
        String testDir = System.getProperty("test.src", ".");
        System.out.println("Use test case files in " + testDir);
        return new Object[] {
            new Runner(Paths.get(testDir, nativeSrc), pkgName, paths(testDir, javaSrcFiles, platformDependent))
        };
    }

    @DataProvider(name = "cases")
    public static Object[][] cases() {
        return new Object[][] {
            { "simple.h", "com.acme", new String[] { "simple.java" }, true},
            { "recursive.h", "com.acme", new String[] { "recursive.java" }, false},
            { "TypedefAnonStruct.h", "com.acme", new String[] { "TypedefAnonStruct.java" }, false},
            { "pad.h", "com.acme", new String[] { "pad.java" }, false},
            { "bitfields.h", "com.acme", new String[] { "bitfields.java" }, true},
            { "globalFuncPointer.h", "com.acme", new String[] { "globalFuncPointer.java" }, false}
        };
    }
}
