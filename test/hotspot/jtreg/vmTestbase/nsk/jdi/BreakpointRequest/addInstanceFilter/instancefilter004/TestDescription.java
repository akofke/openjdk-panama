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
 * @summary converted from VM Testbase nsk/jdi/BreakpointRequest/addInstanceFilter/instancefilter004.
 * VM Testbase keywords: [jpda, jdi]
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test for the implementation of an object of the type
 *     BreakpointRequest.
 *     The test checks up that a result of the method
 *     com.sun.jdi.BreakpointRequest.addInstanceFilter()
 *     complies with its spec:
 *     public void addInstanceFilter(ObjectReference instance)
 *      Restricts the events generated by this request to those in which
 *      the currently executing instance is the object specified.
 *      Not all targets support this operation.
 *      Use VirtualMachine.canUseInstanceFilters() to determine
 *      if the operation is supported.
 *      Parameters: instance - the object which must be the current instance
 *                             in order to pass this filter.
 *      Throws: UnsupportedOperationException -
 *              if the target virtual machine does not support this operation.
 *              InvalidRequestStateException -
 *              if this request is currently enabled or has been deleted.
 *              Filters may be added only to disabled requests.
 *     The test checks up on the following assertion:
 *         Restricts the events generated by this request to those in which
 *         the currently executing instance is the object specified.
 *     The cases to test includes re-invocation of the method
 *     addInstanceFilter() on the same BreakpointRequest object.
 *     There are two BreakpointRequests to check as follows:
 *     (1) For BreakpointRequest2, both invocations are with different
 *         ObjectReferences restricting one Breakpoint event to two objects.
 *         The test expects no Breakpoint event will be received.
 *     (2) For BreakpointRequest1, both invocations are with the same
 *         ObjectReference restricting one Breakpoint event to one object.
 *         The test expects this Breakpoint event will be received.
 *     The test works as follows:
 *     The debugger program - nsk.jdi.BreakpointRequest.addInstanceFilter.instancefilter004;
 *     the debuggee program - nsk.jdi.BreakpointRequest.addInstanceFilter.instancefilter004a.
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
 * @build nsk.jdi.BreakpointRequest.addInstanceFilter.instancefilter004
 *        nsk.jdi.BreakpointRequest.addInstanceFilter.instancefilter004a
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.jdi.BreakpointRequest.addInstanceFilter.instancefilter004
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      "-debugee.vmkeys=${test.vm.opts} ${test.java.opts}"
 */

