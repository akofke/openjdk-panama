/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.runtime.defmeth;

import nsk.share.TestFailure;
import nsk.share.test.TestBase;
import vm.runtime.defmeth.shared.MemoryClassLoader;
import vm.runtime.defmeth.shared.annotation.KnownFailure;
import vm.runtime.defmeth.shared.annotation.NotApplicableFor;
import vm.runtime.defmeth.shared.builder.TestBuilder;
import vm.runtime.defmeth.shared.data.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import vm.runtime.defmeth.shared.DefMethTest;

import java.util.Map;

import static vm.runtime.defmeth.shared.ExecutionMode.*;

/**
 * Basic tests on some of the assertions from JVMS.
 */
public class BasicTest extends DefMethTest {

    public static void main(String[] args) {
        TestBase.runTest(new BasicTest(), args);
    }

    /*
     * JVMS 6.5 invokevirtual
     *
     * ...
     *
     * Runtime Exceptions
     *
     * Otherwise, if the resolved method is not signature polymorphic:
     *
     */

    /*
     * ...
     *
     * If the resolved method is declared in an interface and the class of
     * objectref does not implement the resolved interface, invokevirtual throws
     * an IncompatibleClassChangeError.
     */
    @KnownFailure(modes = {
        REFLECTION,                           // throws IAE
        INVOKE_GENERIC, INVOKE_WITH_ARGS})    // throws ClassCastException
    public void testInterfaceNotImplemented() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()V").emptyBody().build()
            .build();

        ConcreteClass C = b.clazz("C").build();

        Class expectedClass;
        if (factory.getExecutionMode().equals("REFLECTION")) {
            expectedClass = IllegalArgumentException.class;
        } else if (factory.getExecutionMode().equals("INVOKE_WITH_ARGS")) {
            // Notes from JDK-8029926 which details reasons behind CCE.
        // The code below demonstrates the use of a MethodHandle
            // of kind REF_invokeInterface pointing to method I.m.
            // Because 'invoke' is called, this MethodHandle is effectively
            // wrapped in the type adaptations necessary to accept a C
            // as the first argument. ***Before we even get to the point
            // of the invokeinterface call to I.m***, the adaptation
            // code must run, and that is where the ClassCastException occurs.
            // This exception can be thrown from code that is cleanly
            // compiled and does no bytecode generation, so an ICCE would
        // be inappropriate since no classes are changed.
            expectedClass = ClassCastException.class;
        } else {
            expectedClass = IncompatibleClassChangeError.class;
        }

        b.test().callSite(I, C, "m", "()V").throws_(expectedClass).done()

        .run();
    }

    /*
     * ...
     *
     * Otherwise, if no method matching the resolved name and descriptor is
     * selected, invokevirtual throws an NoSuchMethodError.
     *
     * ...
     */
    @KnownFailure(modes = {
        DIRECT, REFLECTION, INVOKE_WITH_ARGS, // NSME, instead of AME
        INVOKE_EXACT, INVOKE_GENERIC, INDY})        // IncompatibleClassChangeError, instead of AME
    public void testNoMatch() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "(Ljava/lang/Object;)V").emptyBody().build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().callSite(C, C, "m",  "()I").throws_(NoSuchMethodError.class).done()
         .test().callSite(C, C, "m1", "()V").throws_(NoSuchMethodError.class).done()
         .test().callSite(C, C, "m", "(I)V").throws_(NoSuchMethodError.class).done()

        .run();
    }

    /*
     * ...
     *
     * 8025604: Updated specification text for both 5.4.3.3 and 5.4.4.4
     * "Otherwise, if any superinterface of C declares a method with the name and
     * descriptor specified by the method reference that has set neither
     * its ACC_PRIVATE flag nor its ACC_STATIC flag, one of these is arbitrarily
     * chosen and method lookup succeeds."
     * If method lookup fails, method resolution throws a NoSuchMethodError
     *
     * ...
     */
    public void testNonPublic() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m1", "()V").private_().emptyBody().build()
                .defaultMethod("m2", "()I").private_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().callSite(C, C, "m1",  "()V").throws_(NoSuchMethodError.class).done()
         .test().callSite(C, C, "m2",  "()I").throws_(NoSuchMethodError.class).done()

        .run();
    }

    /*
     * Default method override w/ non-public concrete method
     *
     * interface I { void m() default {} }
     * class C implements I {
     *   [private/package-private/protected]
     *   void m() {}
     * }
     *
     */
    @KnownFailure(modes = {INVOKE_EXACT, INVOKE_GENERIC, INVOKE_WITH_ARGS, INDY}) // NPE, instead of IAE
    public void testNonPublicOverride() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m","()V").emptyBody().build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I)
                .concreteMethod("m", "()V").private_().emptyBody().build()
            .build();

        ConcreteClass D = b.clazz("D").implement(I)
                .concreteMethod("m", "()V").protected_().emptyBody().build()
            .build();

         ConcreteClass E = b.clazz("E").implement(I)
                .concreteMethod("m", "()V").package_private().emptyBody().build()
            .build();

        b.test().callSite(I, C, "m", "()V").throws_(IllegalAccessError.class).done()
         .test().callSite(I, D, "m", "()V").throws_(IllegalAccessError.class).done()
         .test().callSite(I, E, "m", "()V").throws_(IllegalAccessError.class).done()

        .run();
    }


    /**
     * interface I {
     *   static { throw new RE()}
     *   public default void m() {}
     * }
     *
     * class C implements I {}
     *
     * TEST: C c = new C(); ==> LinkageError
     * Static initialization of class C will trigger
     * I's static initialization due to I's default method.
     */
    public void testStaticInit() throws ClassNotFoundException {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("<clinit>", "()V")
                    .flags(ACC_STATIC)
                    .throw_(RuntimeException.class)
                    .build()

                .defaultMethod("m", "()V")
                    .emptyBody().build()
                .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().callSite(I, C, "m", "()V").throws_(LinkageError.class).done()

        .run();
    }

    /**
     * interface I {
     *   static { throw new RE()}
     *   private default void m() {}
     * }
     *
     * class C implements I {}
     *
     * TEST: C c = new C(); ==> LinkageError
     * Static initialization of class C will trigger
     * I's static initialization due to I's private concrete method.
     */
    public void testStaticInitPrivate() throws ClassNotFoundException {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("<clinit>", "()V")
                    .flags(ACC_STATIC)
                    .throw_(RuntimeException.class)
                    .build()

                .defaultMethod("m", "()V")
                    .flags(ACC_PRIVATE)
                    .emptyBody().build()
                .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        Class expectedClass;
        if (factory.getExecutionMode().equals("REFLECTION")) {
            expectedClass = NoSuchMethodException.class;
        } else {
            expectedClass = LinkageError.class;
        }
        b.test().callSite(I, C, "m", "()V").throws_(expectedClass).done()

        .run();
    }

    /**
     * interface I {
     *   static { throw new RE()}
     * }
     * interface J {
     *   public default int m() { return 1}
     *
     * class C implements I,J {}
     *
     * TEST: C c = new C()
     *       c.m() returns 1
     * Static initialization of class C will not trigger
     * I's static initialization since I has no concrete methods.
     */
    public void testNotStaticInitNoDefault() throws ClassNotFoundException {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("<clinit>", "()V")
                    .flags(ACC_STATIC)
                    .throw_(RuntimeException.class)
                    .build()
                 .build();

        Interface J = b.intf("J")
                .defaultMethod("m", "()I")
                    .returns(1).build()
                .build();

        ConcreteClass C = b.clazz("C").implement(I,J).build();

        b.test().callSite(C, C, "m", "()I").returns(1).done()

        .run();
    }

    /*
     * Example A.10
     *
     * Let L1 and L2 be class loaders with different interpretations of the type "A".
     *
     * Loaded by L1:
     * public interface I { public default A m() { return null; } }
     *
     * Loaded by L2:
     * public class C implements I {}
     *
     * TEST: I o = new C(); o.m() ==> LinkageError;
     * TEST: C o = new C(); o.m() ==> LinkageError;
     */
    // disabling this test for REFLECTION and INVOKE for now until
    // loader constraint issue associated with those modes has been resolved
    @NotApplicableFor(modes = { REFLECTION, INVOKE_WITH_ARGS, INVOKE_EXACT, INVOKE_GENERIC, INDY })
    public void testLoaderConstraint() throws Exception{
        TestBuilder b = factory.getBuilder();

        ConcreteClass A = b.clazz("A").build();

        Interface I = b.intf("I")
                .defaultMethod("m", "()LA;").returnsNewInstance(A).build()
                .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().callSite(I, C, "m", "()LA;").throws_(LinkageError.class).done()
         .test().callSite(C, C, "m", "()LA;").throws_(LinkageError.class).done()

         .prepare(new TestBuilder.LoaderConstructor() {
                @Override
                public MemoryClassLoader construct(Map<String, byte[]> classFiles) {
                    final byte[] cfI = classFiles.get("I");
                    final byte[] cfC = classFiles.get("C");
                    final byte[] cfA = classFiles.get("A");
                    final byte[] cfT1 = classFiles.get("Test1_I_C_m");
                    final byte[] cfT2 = classFiles.get("Test2_C_C_m");

                    final ClassLoader l1 = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name) throws ClassNotFoundException {
                            switch (name) {
                                case "I":
                                    return defineClass(name, cfI, 0, cfI.length);
                                case "A":
                                    return defineClass(name, cfA, 0, cfA.length);
                                default:
                                    throw new ClassNotFoundException(name);
                            }
                        }
                    };

                    final MemoryClassLoader l2 = new MemoryClassLoader(classFiles) {
                        @Override
                        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                            if ("I".equals(name)) {
                                return l1.loadClass(name);
                            } else {
                                return super.loadClass(name, resolve);
                            }
                        }
                    };

                    try {
                        // Need to preload classes, otherwise A will be resolved in the same loader
                        l1.loadClass("A"); l1.loadClass("I");
                        l2.loadClass("A"); l2.loadClass("C");
                    } catch (ClassNotFoundException e) {
                        throw new TestFailure(e);
                    }

                    return l2;
                }
            }).run();
    }
}
