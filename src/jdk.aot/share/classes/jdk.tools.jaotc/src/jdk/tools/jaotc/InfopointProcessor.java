/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.Relocation;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.InvokeTarget;

final class InfopointProcessor {

    private final DataBuilder dataBuilder;

    private final BinaryContainer binaryContainer;

    InfopointProcessor(DataBuilder dataBuilder) {
        this.dataBuilder = dataBuilder;
        this.binaryContainer = dataBuilder.getBinaryContainer();
    }

    /**
     * Parse an {@link Infopoint} generated by the compiler and create all needed binary section
     * constructs.
     *
     * @param methodInfo compiled method info
     * @param info info point being processed
     */
    void process(CompiledMethodInfo methodInfo, Infopoint info) {
        switch (info.reason) {
            case CALL:
                // All calls in compiled code need a symbol and relocation entry.
                processCallInfoPoint(methodInfo, (Call) info);
                break;
            case SAFEPOINT:
            case IMPLICIT_EXCEPTION:
            case METHOD_START:
            case METHOD_END:
            case BYTECODE_POSITION:
                break;
            default:
                throw new InternalError("Unknown info point reason: " + info.reason);
        }
        if (info.debugInfo == null) {
            return;
        }
        BytecodePosition bcp = info.debugInfo.getBytecodePosition();
        if (bcp == null) {
            return;
        }
        recordScopeKlasses(methodInfo, bcp, info.debugInfo.getVirtualObjectMapping());
    }

    private void recordScopeKlasses(CompiledMethodInfo methodInfo, BytecodePosition bcp, VirtualObject[] vos) {
        BytecodePosition caller = bcp.getCaller();
        if (caller != null) {
            recordScopeKlasses(methodInfo, caller, vos);
        }

        HotSpotResolvedJavaMethod m = (HotSpotResolvedJavaMethod) bcp.getMethod();
        HotSpotResolvedObjectType klass = m.getDeclaringClass();
        methodInfo.addDependentKlassData(binaryContainer, klass);

        if (vos == null) {
            return;
        }
        for (VirtualObject vo : vos) {
            HotSpotResolvedObjectType vk = (HotSpotResolvedObjectType) vo.getType();
            methodInfo.addDependentKlassData(binaryContainer, vk);
        }

    }

    /**
     * Process Call info points in Graal generated compilation result. We want to create one of the
     * following relocations: .text -> .hotspot.plt.linkage - Java method to Java method call .text
     * -> .text - Java method / Graal stub to Graal stub call .text -> .plt - Java method / Graal
     * stub to VM method call.
     *
     * @param methodInfo compiled method info
     * @param call call
     */
    private void processCallInfoPoint(CompiledMethodInfo methodInfo, Call call) {
        CallSiteRelocationInfo callSiteRelocation = getCallSiteRelocationInfo(call);
        CallSiteRelocationSymbol callSiteRelocationSymbol = getCallSiteRelocationSymbol(methodInfo, call, callSiteRelocation);

        Relocation relocation = new Relocation(methodInfo.getTextSectionOffset() + call.pcOffset, callSiteRelocation.type, call.size, binaryContainer.getCodeContainer(),
                        callSiteRelocationSymbol.symbol);
        binaryContainer.addRelocation(relocation);
    }

    /**
     * Get information about the call site. Name of the callee and relocation call type.
     */
    private static CallSiteRelocationInfo getCallSiteRelocationInfo(Call call) {
        InvokeTarget callTarget = call.target;
        if (callTarget instanceof HotSpotResolvedJavaMethod) {
            return new JavaCallSiteRelocationInfo(call, (HotSpotResolvedJavaMethod) callTarget);
        } else if (callTarget instanceof HotSpotForeignCallLinkage) {
            return new ForeignCallSiteRelocationInfo(call, (HotSpotForeignCallLinkage) callTarget);
        } else {
            throw new InternalError("Unhandled call type found in infopoint: " + callTarget);
        }
    }

    /**
     * Return a relocation symbol for the given call site.
     */
    private CallSiteRelocationSymbol getCallSiteRelocationSymbol(CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation) {
        switch (callSiteRelocation.type) {
            case STUB_CALL_DIRECT:
                return new StubDirectCallSiteRelocationSymbol(callSiteRelocation, binaryContainer);
            case FOREIGN_CALL_INDIRECT_GOT:
                return new ForeignGotCallSiteRelocationSymbol(mi, call, callSiteRelocation, dataBuilder);
            default:
                return new JavaCallSiteRelocationSymbol(mi, call, callSiteRelocation, binaryContainer);
        }
    }

}
