/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"

void LIR_OpShenandoahCompareAndSwap::emit_code(LIR_Assembler* masm) {
  Register addr = _addr->as_register_lo();
  Register newval = _new_value->as_register();
  Register cmpval = _cmp_value->as_register();
  Register tmp1 = _tmp1->as_register();
  Register tmp2 = _tmp2->as_register();
  assert(cmpval == rax, "wrong register");
  assert(newval != NULL, "new val must be register");
  assert(cmpval != newval, "cmp and new values must be in different registers");
  assert(cmpval != addr, "cmp and addr must be in different registers");
  assert(newval != addr, "new value and addr must be in different registers");
  ShenandoahBarrierSet::assembler()->cmpxchg_oop(masm->masm(), NULL, Address(addr, 0), cmpval, newval, true, true, tmp1, tmp2);
}

#ifdef ASSERT
#define __ gen->lir(__FILE__, __LINE__)->
#else
#define __ gen->lir()->
#endif

LIR_Opr ShenandoahBarrierSetC1::atomic_cmpxchg_at_resolved(LIRAccess& access, LIRItem& cmp_value, LIRItem& new_value) {

  if (access.is_oop()) {
    LIRGenerator* gen = access.gen();
    if (ShenandoahSATBBarrier) {
      pre_barrier(gen, access.access_emit_info(), access.decorators(), access.resolved_addr(),
                  LIR_OprFact::illegalOpr /* pre_val */);
    }
    if (ShenandoahCASBarrier) {
      cmp_value.load_item_force(FrameMap::rax_oop_opr);
      new_value.load_item();

      LIR_Opr t1 = gen->new_register(T_OBJECT);
      LIR_Opr t2 = gen->new_register(T_OBJECT);
      LIR_Opr addr = access.resolved_addr()->as_address_ptr()->base();

      __ append(new LIR_OpShenandoahCompareAndSwap(addr, cmp_value.result(), new_value.result(), t1, t2, LIR_OprFact::illegalOpr));

      LIR_Opr result = gen->new_register(T_INT);
      __ cmove(lir_cond_equal, LIR_OprFact::intConst(1), LIR_OprFact::intConst(0),
               result, T_INT);
      return result;
    }
  }
  return BarrierSetC1::atomic_cmpxchg_at_resolved(access, cmp_value, new_value);
}

LIR_Opr ShenandoahBarrierSetC1::atomic_xchg_at_resolved(LIRAccess& access, LIRItem& value) {
  LIRGenerator* gen = access.gen();
  BasicType type = access.type();

  LIR_Opr result = gen->new_register(type);
  value.load_item();
  LIR_Opr value_opr = value.result();

  if (access.is_oop()) {
    value_opr = storeval_barrier(access.gen(), value_opr, access.access_emit_info(), access.decorators());
  }

  // Because we want a 2-arg form of xchg and xadd
  __ move(value_opr, result);

  assert(type == T_INT || type == T_OBJECT || type == T_ARRAY LP64_ONLY( || type == T_LONG ), "unexpected type");
  __ xchg(access.resolved_addr(), result, result, LIR_OprFact::illegalOpr);

  if (access.is_oop()) {
    if (ShenandoahSATBBarrier) {
      pre_barrier(access.gen(), access.access_emit_info(), access.decorators(), LIR_OprFact::illegalOpr,
                  result /* pre_val */);
    }
  }

  return result;
}
