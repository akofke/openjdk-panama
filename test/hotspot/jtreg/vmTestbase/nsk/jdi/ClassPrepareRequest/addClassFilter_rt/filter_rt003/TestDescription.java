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
 * @summary converted from VM Testbase nsk/jdi/ClassPrepareRequest/addClassFilter_rt/filter_rt003.
 * VM Testbase keywords: [jpda, jdi]
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test for the implementation of an object of the type
 *     ClassPrepareRequest.
 *     The test checks up that a result of the method
 *     com.sun.jdi.ClassPrepareRequest.addClassFilter_rt()
 *     complies with its spec:
 *     public void addClassFilter(ReferenceType refType)
 *      Restricts the events generated by this request to be the preparation
 *      of the given reference type and any subtypes.
 *      An event will be generated for any prepared reference type
 *      that can be safely cast to the given reference type.
 *      Parameters: refType - the reference type to filter on.
 *      Throws: InvalidRequestStateException -
 *              if this request is currently enabled or has been deleted.
 *              Filters may be added only to disabled requests.
 *     The test checks up on the following assertion:
 *         Restricts the events generated by this request to be the preparation
 *         of the given reference type and any subtypes.
 *     The cases to test include re-invocations of the method
 *     addClassFilter() on the same ClassPrepareRequest object.
 *     There are two ClassPrepareRequests to check as follows:
 *     (1) For ClassPrepareRequest2, both invocations are with different
 *         ReferenceTypes restricting one ClassPrepare event to two classes.
 *         The test expects no ClassPrepare event will be received.
 *     (2) For ClassPrepareRequest1, both invocations are with the same
 *         ReferenceType restricting one ClassPrepare event to one class.
 *         The test expects this ClassPrepare event will be received.
 *     The test works as follows:
 *     The debugger program - nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt003;
 *     the debuggee program - nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt003a.
 *     Using nsk.jdi.share classes,
 *     the debugger gets the debuggee running on another JavaVM,
 *     creates the object debuggee.VM, and waits for VMStartEvent.
 *     Upon getting the debuggee VM started,
 *     the debugger calls corresponding debuggee.VM methods to get
 *     needed data and to perform checks.
 *     In case of error the test produces the return value 97 and
 *     a corresponding error message(s).
 *     Otherwise, the test is passed and produces
 *     the return value 95 and no message.
 * COMMENTS:
 *     4930911 TEST_BUG: filter_rt006 debuggee has a race
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt003
 *        nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt003a
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt003
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      "-debugee.vmkeys=${test.vm.opts} ${test.java.opts}"
 */

