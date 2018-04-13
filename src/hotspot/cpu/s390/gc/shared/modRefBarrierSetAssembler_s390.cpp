/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, SAP SE. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/modRefBarrierSetAssembler.hpp"

#define __ masm->

void ModRefBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators, Register addr, Register count,
                                                                 bool do_return) {
  if (do_return) { __ z_br(Z_R14); }
}

void ModRefBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                   Register src, Register dst, Register count) {
  if (type == T_OBJECT || type == T_ARRAY) {
    gen_write_ref_array_pre_barrier(masm, decorators, dst, count);
  }
}

void ModRefBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                                   Register dst, Register count, bool do_return) {
  if (type == T_OBJECT || type == T_ARRAY) {
    gen_write_ref_array_post_barrier(masm, decorators, dst, count, do_return);
  } else {
    if (do_return) { __ z_br(Z_R14); }
  }
}

void ModRefBarrierSetAssembler::oop_store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                             const Address& dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
}

void ModRefBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                         const Address& dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  if (type == T_OBJECT || type == T_ARRAY) {
    oop_store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
  }
}
