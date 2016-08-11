/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_FRAME_INLINE_HPP
#define SHARE_VM_RUNTIME_FRAME_INLINE_HPP

#include "interpreter/bytecodeInterpreter.hpp"
#include "interpreter/bytecodeInterpreter.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/method.hpp"
#include "runtime/frame.hpp"
#include "runtime/signature.hpp"
#include "utilities/macros.hpp"
#ifdef ZERO
# include "entryFrame_zero.hpp"
# include "fakeStubFrame_zero.hpp"
# include "interpreterFrame_zero.hpp"
# include "sharkFrame_zero.hpp"
#endif

#include CPU_HEADER_INLINE(frame)

inline bool frame::is_entry_frame() const {
  return StubRoutines::returns_to_call_stub(pc());
}

inline bool frame::is_stub_frame() const {
  return StubRoutines::is_stub_code(pc()) || (_cb != NULL && _cb->is_adapter_blob());
}

inline bool frame::is_first_frame() const {
  return is_entry_frame() && entry_frame_is_first();
}

#endif // SHARE_VM_RUNTIME_FRAME_INLINE_HPP
