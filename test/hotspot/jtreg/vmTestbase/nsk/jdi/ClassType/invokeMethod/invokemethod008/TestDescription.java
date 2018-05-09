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
 *
 * @summary converted from VM Testbase nsk/jdi/ClassType/invokeMethod/invokemethod008.
 * VM Testbase keywords: [jpda, jdi]
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test checks up the method com.sun.jdi.ClassType.invokeMethod()
 *     Test checks up the following assertion:
 *         If the target VM is disconnected during the invoke (for example,
 *         through VirtualMachine.dispose()) the method invocation continues.
 * COMMENTS:
 *     The test was updated to avoid hanging of debugee's VM
 *                after invoking VirtualMachine.dispose().
 *                (see 4471237 VirtualMachine.dispose() hangs on Windows)
 *     Test was fixed according to test bug:
 *     4778296 TEST_BUG: debuggee VM intemittently hangs after resuming
 *     - handling VMStartEvent was removed from the debugger part of the test
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.jdi.ClassType.invokeMethod.invokemethod008
 *        nsk.jdi.ClassType.invokeMethod.invokemethod008a
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.jdi.ClassType.invokeMethod.invokemethod008
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      "-debugee.vmkeys=${test.vm.opts} ${test.java.opts}"
 */

