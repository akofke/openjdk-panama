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


package org.graalvm.compiler.core.common;

import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

public enum SpeculativeExecutionAttacksMitigations {
    None,
    AllTargets,
    GuardTargets,
    NonDeoptGuardTargets;

    public static class Options {
        // @formatter:off
        @Option(help = "Select a strategy to mitigate speculative execution attacks (e.g., SPECTRE)", type = OptionType.User)
        public static final EnumOptionKey<SpeculativeExecutionAttacksMitigations> MitigateSpeculativeExecutionAttacks = new EnumOptionKey<>(None);
        @Option(help = "Use index masking after bounds check to mitigate speculative execution attacks", type = OptionType.User)
        public static final OptionKey<Boolean> UseIndexMasking = new OptionKey<>(false);
        // @formatter:on
    }
}
