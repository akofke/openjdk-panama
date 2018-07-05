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
package jdk.internal.clang;

import java.foreign.memory.Pointer;

public class Diagnostic {
    final Pointer<Void> ptr;

    // Various Diagnostic severity levels - from Clang enum CXDiagnosticSeverity

    /**
     * A diagnostic that has been suppressed, e.g., by a command-line
     * option.
     */
    public static final int CXDiagnostic_Ignored = 0;

    /**
     * This diagnostic is a note that should be attached to the
     * previous (non-note) diagnostic.
     */
    public static final int CXDiagnostic_Note    = 1;

    /**
     * This diagnostic indicates suspicious code that may not be
     * wrong.
     */
    public static final int CXDiagnostic_Warning = 2;

    /**
     * This diagnostic indicates that the code is ill-formed.
     */
    public static final int CXDiagnostic_Error   = 3;

    /**
     * This diagnostic indicates that the code is ill-formed such
     * that future parser recovery is unlikely to produce useful
     * results.
     */
    public static final int CXDiagnostic_Fatal   = 4;

    Diagnostic(Pointer<Void> ptr) {
        this.ptr = ptr;
    }

    public int severity() {
        return LibClang.lib.clang_getDiagnosticSeverity(ptr);
    }

    public SourceLocation location() {
        return new SourceLocation(LibClang.lib.clang_getDiagnosticLocation(ptr));
    }

    public String spelling() {
        return LibClang.CXStrToString(
                LibClang.lib.clang_getDiagnosticSpelling(ptr));
    }

    public void dispose() {
        LibClang.lib.clang_disposeDiagnostic(ptr);
    }

    @Override
    public String toString() {
        return LibClang.CXStrToString(
                LibClang.lib.clang_formatDiagnostic(ptr,
                    LibClang.lib.clang_defaultDiagnosticDisplayOptions()));
    }
}
