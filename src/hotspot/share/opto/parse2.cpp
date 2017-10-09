/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethodData.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileLog.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/idealGraphPrinter.hpp"
#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/parse.hpp"
#include "opto/runtime.hpp"
#include "opto/valuetypenode.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/sharedRuntime.hpp"

#ifndef PRODUCT
extern int explicit_null_checks_inserted,
           explicit_null_checks_elided;
#endif

//---------------------------------array_load----------------------------------
void Parse::array_load(BasicType elem_type) {
  const Type* elem = Type::TOP;
  Node* adr = array_addressing(elem_type, 0, &elem);
  if (stopped())  return;     // guaranteed null or range check
  Node* idx   = pop();   // Get from stack without popping
  Node* ary   = pop();   // in case of exception
  //dec_sp(2);                  // Pop array and index
  const TypeAryPtr* arytype = _gvn.type(ary)->is_aryptr();
  if (arytype->klass()->is_value_array_klass()) {
    ciValueArrayKlass* vak = arytype->klass()->as_value_array_klass();
    ValueTypeNode* vt = ValueTypeNode::make_from_flattened(this, vak->element_klass()->as_value_klass(), ary, adr);
    push(vt);
    return;
  }
  const TypeAryPtr* adr_type = TypeAryPtr::get_array_body_type(elem_type);
  Node* ld = make_load(control(), adr, elem, elem_type, adr_type, MemNode::unordered);
  push(ld);
}


//--------------------------------array_store----------------------------------
void Parse::array_store(BasicType elem_type) {
  const Type* elem = Type::TOP;
  Node* adr = array_addressing(elem_type, 1, &elem);
  if (stopped())  return;     // guaranteed null or range check
  Node* val = pop();
  dec_sp(2);                  // Pop array and index
  const TypeAryPtr* adr_type = TypeAryPtr::get_array_body_type(elem_type);
  if (elem == TypeInt::BOOL) {
    elem_type = T_BOOLEAN;
  }
  store_to_memory(control(), adr, val, elem_type, adr_type, StoreNode::release_if_reference(elem_type));
}


//------------------------------array_addressing-------------------------------
// Pull array and index from the stack.  Compute pointer-to-element.
Node* Parse::array_addressing(BasicType type, int vals, const Type* *result2) {
  Node *idx   = peek(0+vals);   // Get from stack without popping
  Node *ary   = peek(1+vals);   // in case of exception

  // Null check the array base, with correct stack contents
  ary = null_check(ary, T_ARRAY);
  // Compile-time detect of null-exception?
  if (stopped())  return top();

  const TypeAryPtr* arytype  = _gvn.type(ary)->is_aryptr();
  const TypeInt*    sizetype = arytype->size();
  const Type*       elemtype = arytype->elem();

  if (UseUniqueSubclasses && result2 != NULL) {
    const Type* el = elemtype->make_ptr();
    if (el && el->isa_instptr()) {
      const TypeInstPtr* toop = el->is_instptr();
      if (toop->klass()->as_instance_klass()->unique_concrete_subklass()) {
        // If we load from "AbstractClass[]" we must see "ConcreteSubClass".
        const Type* subklass = Type::get_const_type(toop->klass());
        elemtype = subklass->join_speculative(el);
      }
    }
  }

  // Check for big class initializers with all constant offsets
  // feeding into a known-size array.
  const TypeInt* idxtype = _gvn.type(idx)->is_int();
  // See if the highest idx value is less than the lowest array bound,
  // and if the idx value cannot be negative:
  bool need_range_check = true;
  if (idxtype->_hi < sizetype->_lo && idxtype->_lo >= 0) {
    need_range_check = false;
    if (C->log() != NULL)   C->log()->elem("observe that='!need_range_check'");
  }

  ciKlass * arytype_klass = arytype->klass();
  if ((arytype_klass != NULL) && (!arytype_klass->is_loaded())) {
    // Only fails for some -Xcomp runs
    // The class is unloaded.  We have to run this bytecode in the interpreter.
    uncommon_trap(Deoptimization::Reason_unloaded,
                  Deoptimization::Action_reinterpret,
                  arytype->klass(), "!loaded array");
    return top();
  }

  // Do the range check
  if (GenerateRangeChecks && need_range_check) {
    Node* tst;
    if (sizetype->_hi <= 0) {
      // The greatest array bound is negative, so we can conclude that we're
      // compiling unreachable code, but the unsigned compare trick used below
      // only works with non-negative lengths.  Instead, hack "tst" to be zero so
      // the uncommon_trap path will always be taken.
      tst = _gvn.intcon(0);
    } else {
      // Range is constant in array-oop, so we can use the original state of mem
      Node* len = load_array_length(ary);

      // Test length vs index (standard trick using unsigned compare)
      Node* chk = _gvn.transform( new CmpUNode(idx, len) );
      BoolTest::mask btest = BoolTest::lt;
      tst = _gvn.transform( new BoolNode(chk, btest) );
    }
    RangeCheckNode* rc = new RangeCheckNode(control(), tst, PROB_MAX, COUNT_UNKNOWN);
    _gvn.set_type(rc, rc->Value(&_gvn));
    if (!tst->is_Con()) {
      record_for_igvn(rc);
    }
    set_control(_gvn.transform(new IfTrueNode(rc)));
    // Branch to failure if out of bounds
    {
      PreserveJVMState pjvms(this);
      set_control(_gvn.transform(new IfFalseNode(rc)));
      if (C->allow_range_check_smearing()) {
        // Do not use builtin_throw, since range checks are sometimes
        // made more stringent by an optimistic transformation.
        // This creates "tentative" range checks at this point,
        // which are not guaranteed to throw exceptions.
        // See IfNode::Ideal, is_range_check, adjust_check.
        uncommon_trap(Deoptimization::Reason_range_check,
                      Deoptimization::Action_make_not_entrant,
                      NULL, "range_check");
      } else {
        // If we have already recompiled with the range-check-widening
        // heroic optimization turned off, then we must really be throwing
        // range check exceptions.
        builtin_throw(Deoptimization::Reason_range_check, idx);
      }
    }
  }
  // Check for always knowing you are throwing a range-check exception
  if (stopped())  return top();

  // Make array address computation control dependent to prevent it
  // from floating above the range check during loop optimizations.
  Node* ptr = array_element_address(ary, idx, type, sizetype, control());

  if (result2 != NULL)  *result2 = elemtype;

  assert(ptr != top(), "top should go hand-in-hand with stopped");

  return ptr;
}


// returns IfNode
IfNode* Parse::jump_if_fork_int(Node* a, Node* b, BoolTest::mask mask) {
  Node   *cmp = _gvn.transform( new CmpINode( a, b)); // two cases: shiftcount > 32 and shiftcount <= 32
  Node   *tst = _gvn.transform( new BoolNode( cmp, mask));
  IfNode *iff = create_and_map_if( control(), tst, ((mask == BoolTest::eq) ? PROB_STATIC_INFREQUENT : PROB_FAIR), COUNT_UNKNOWN );
  return iff;
}

// return Region node
Node* Parse::jump_if_join(Node* iffalse, Node* iftrue) {
  Node *region  = new RegionNode(3); // 2 results
  record_for_igvn(region);
  region->init_req(1, iffalse);
  region->init_req(2, iftrue );
  _gvn.set_type(region, Type::CONTROL);
  region = _gvn.transform(region);
  set_control (region);
  return region;
}


//------------------------------helper for tableswitch-------------------------
void Parse::jump_if_true_fork(IfNode *iff, int dest_bci_if_true, int prof_table_index) {
  // True branch, use existing map info
  { PreserveJVMState pjvms(this);
    Node *iftrue  = _gvn.transform( new IfTrueNode (iff) );
    set_control( iftrue );
    profile_switch_case(prof_table_index);
    merge_new_path(dest_bci_if_true);
  }

  // False branch
  Node *iffalse = _gvn.transform( new IfFalseNode(iff) );
  set_control( iffalse );
}

void Parse::jump_if_false_fork(IfNode *iff, int dest_bci_if_true, int prof_table_index) {
  // True branch, use existing map info
  { PreserveJVMState pjvms(this);
    Node *iffalse  = _gvn.transform( new IfFalseNode (iff) );
    set_control( iffalse );
    profile_switch_case(prof_table_index);
    merge_new_path(dest_bci_if_true);
  }

  // False branch
  Node *iftrue = _gvn.transform( new IfTrueNode(iff) );
  set_control( iftrue );
}

void Parse::jump_if_always_fork(int dest_bci, int prof_table_index) {
  // False branch, use existing map and control()
  profile_switch_case(prof_table_index);
  merge_new_path(dest_bci);
}


extern "C" {
  static int jint_cmp(const void *i, const void *j) {
    int a = *(jint *)i;
    int b = *(jint *)j;
    return a > b ? 1 : a < b ? -1 : 0;
  }
}


// Default value for methodData switch indexing. Must be a negative value to avoid
// conflict with any legal switch index.
#define NullTableIndex -1

class SwitchRange : public StackObj {
  // a range of integers coupled with a bci destination
  jint _lo;                     // inclusive lower limit
  jint _hi;                     // inclusive upper limit
  int _dest;
  int _table_index;             // index into method data table

public:
  jint lo() const              { return _lo;   }
  jint hi() const              { return _hi;   }
  int  dest() const            { return _dest; }
  int  table_index() const     { return _table_index; }
  bool is_singleton() const    { return _lo == _hi; }

  void setRange(jint lo, jint hi, int dest, int table_index) {
    assert(lo <= hi, "must be a non-empty range");
    _lo = lo, _hi = hi; _dest = dest; _table_index = table_index;
  }
  bool adjoinRange(jint lo, jint hi, int dest, int table_index) {
    assert(lo <= hi, "must be a non-empty range");
    if (lo == _hi+1 && dest == _dest && table_index == _table_index) {
      _hi = hi;
      return true;
    }
    return false;
  }

  void set (jint value, int dest, int table_index) {
    setRange(value, value, dest, table_index);
  }
  bool adjoin(jint value, int dest, int table_index) {
    return adjoinRange(value, value, dest, table_index);
  }

  void print() {
    if (is_singleton())
      tty->print(" {%d}=>%d", lo(), dest());
    else if (lo() == min_jint)
      tty->print(" {..%d}=>%d", hi(), dest());
    else if (hi() == max_jint)
      tty->print(" {%d..}=>%d", lo(), dest());
    else
      tty->print(" {%d..%d}=>%d", lo(), hi(), dest());
  }
};


//-------------------------------do_tableswitch--------------------------------
void Parse::do_tableswitch() {
  Node* lookup = pop();

  // Get information about tableswitch
  int default_dest = iter().get_dest_table(0);
  int lo_index     = iter().get_int_table(1);
  int hi_index     = iter().get_int_table(2);
  int len          = hi_index - lo_index + 1;

  if (len < 1) {
    // If this is a backward branch, add safepoint
    maybe_add_safepoint(default_dest);
    merge(default_dest);
    return;
  }

  // generate decision tree, using trichotomy when possible
  int rnum = len+2;
  bool makes_backward_branch = false;
  SwitchRange* ranges = NEW_RESOURCE_ARRAY(SwitchRange, rnum);
  int rp = -1;
  if (lo_index != min_jint) {
    ranges[++rp].setRange(min_jint, lo_index-1, default_dest, NullTableIndex);
  }
  for (int j = 0; j < len; j++) {
    jint match_int = lo_index+j;
    int  dest      = iter().get_dest_table(j+3);
    makes_backward_branch |= (dest <= bci());
    int  table_index = method_data_update() ? j : NullTableIndex;
    if (rp < 0 || !ranges[rp].adjoin(match_int, dest, table_index)) {
      ranges[++rp].set(match_int, dest, table_index);
    }
  }
  jint highest = lo_index+(len-1);
  assert(ranges[rp].hi() == highest, "");
  if (highest != max_jint
      && !ranges[rp].adjoinRange(highest+1, max_jint, default_dest, NullTableIndex)) {
    ranges[++rp].setRange(highest+1, max_jint, default_dest, NullTableIndex);
  }
  assert(rp < len+2, "not too many ranges");

  // Safepoint in case if backward branch observed
  if( makes_backward_branch && UseLoopSafepoints )
    add_safepoint();

  jump_switch_ranges(lookup, &ranges[0], &ranges[rp]);
}


//------------------------------do_lookupswitch--------------------------------
void Parse::do_lookupswitch() {
  Node *lookup = pop();         // lookup value
  // Get information about lookupswitch
  int default_dest = iter().get_dest_table(0);
  int len          = iter().get_int_table(1);

  if (len < 1) {    // If this is a backward branch, add safepoint
    maybe_add_safepoint(default_dest);
    merge(default_dest);
    return;
  }

  // generate decision tree, using trichotomy when possible
  jint* table = NEW_RESOURCE_ARRAY(jint, len*2);
  {
    for( int j = 0; j < len; j++ ) {
      table[j+j+0] = iter().get_int_table(2+j+j);
      table[j+j+1] = iter().get_dest_table(2+j+j+1);
    }
    qsort( table, len, 2*sizeof(table[0]), jint_cmp );
  }

  int rnum = len*2+1;
  bool makes_backward_branch = false;
  SwitchRange* ranges = NEW_RESOURCE_ARRAY(SwitchRange, rnum);
  int rp = -1;
  for( int j = 0; j < len; j++ ) {
    jint match_int   = table[j+j+0];
    int  dest        = table[j+j+1];
    int  next_lo     = rp < 0 ? min_jint : ranges[rp].hi()+1;
    int  table_index = method_data_update() ? j : NullTableIndex;
    makes_backward_branch |= (dest <= bci());
    if( match_int != next_lo ) {
      ranges[++rp].setRange(next_lo, match_int-1, default_dest, NullTableIndex);
    }
    if( rp < 0 || !ranges[rp].adjoin(match_int, dest, table_index) ) {
      ranges[++rp].set(match_int, dest, table_index);
    }
  }
  jint highest = table[2*(len-1)];
  assert(ranges[rp].hi() == highest, "");
  if( highest != max_jint
      && !ranges[rp].adjoinRange(highest+1, max_jint, default_dest, NullTableIndex) ) {
    ranges[++rp].setRange(highest+1, max_jint, default_dest, NullTableIndex);
  }
  assert(rp < rnum, "not too many ranges");

  // Safepoint in case backward branch observed
  if( makes_backward_branch && UseLoopSafepoints )
    add_safepoint();

  jump_switch_ranges(lookup, &ranges[0], &ranges[rp]);
}

//----------------------------create_jump_tables-------------------------------
bool Parse::create_jump_tables(Node* key_val, SwitchRange* lo, SwitchRange* hi) {
  // Are jumptables enabled
  if (!UseJumpTables)  return false;

  // Are jumptables supported
  if (!Matcher::has_match_rule(Op_Jump))  return false;

  // Don't make jump table if profiling
  if (method_data_update())  return false;

  // Decide if a guard is needed to lop off big ranges at either (or
  // both) end(s) of the input set. We'll call this the default target
  // even though we can't be sure that it is the true "default".

  bool needs_guard = false;
  int default_dest;
  int64_t total_outlier_size = 0;
  int64_t hi_size = ((int64_t)hi->hi()) - ((int64_t)hi->lo()) + 1;
  int64_t lo_size = ((int64_t)lo->hi()) - ((int64_t)lo->lo()) + 1;

  if (lo->dest() == hi->dest()) {
    total_outlier_size = hi_size + lo_size;
    default_dest = lo->dest();
  } else if (lo_size > hi_size) {
    total_outlier_size = lo_size;
    default_dest = lo->dest();
  } else {
    total_outlier_size = hi_size;
    default_dest = hi->dest();
  }

  // If a guard test will eliminate very sparse end ranges, then
  // it is worth the cost of an extra jump.
  if (total_outlier_size > (MaxJumpTableSparseness * 4)) {
    needs_guard = true;
    if (default_dest == lo->dest()) lo++;
    if (default_dest == hi->dest()) hi--;
  }

  // Find the total number of cases and ranges
  int64_t num_cases = ((int64_t)hi->hi()) - ((int64_t)lo->lo()) + 1;
  int num_range = hi - lo + 1;

  // Don't create table if: too large, too small, or too sparse.
  if (num_cases < MinJumpTableSize || num_cases > MaxJumpTableSize)
    return false;
  if (num_cases > (MaxJumpTableSparseness * num_range))
    return false;

  // Normalize table lookups to zero
  int lowval = lo->lo();
  key_val = _gvn.transform( new SubINode(key_val, _gvn.intcon(lowval)) );

  // Generate a guard to protect against input keyvals that aren't
  // in the switch domain.
  if (needs_guard) {
    Node*   size = _gvn.intcon(num_cases);
    Node*   cmp = _gvn.transform( new CmpUNode(key_val, size) );
    Node*   tst = _gvn.transform( new BoolNode(cmp, BoolTest::ge) );
    IfNode* iff = create_and_map_if( control(), tst, PROB_FAIR, COUNT_UNKNOWN);
    jump_if_true_fork(iff, default_dest, NullTableIndex);
  }

  // Create an ideal node JumpTable that has projections
  // of all possible ranges for a switch statement
  // The key_val input must be converted to a pointer offset and scaled.
  // Compare Parse::array_addressing above.

  // Clean the 32-bit int into a real 64-bit offset.
  // Otherwise, the jint value 0 might turn into an offset of 0x0800000000.
  const TypeInt* ikeytype = TypeInt::make(0, num_cases, Type::WidenMin);
  // Make I2L conversion control dependent to prevent it from
  // floating above the range check during loop optimizations.
  key_val = C->conv_I2X_index(&_gvn, key_val, ikeytype, control());

  // Shift the value by wordsize so we have an index into the table, rather
  // than a switch value
  Node *shiftWord = _gvn.MakeConX(wordSize);
  key_val = _gvn.transform( new MulXNode( key_val, shiftWord));

  // Create the JumpNode
  Node* jtn = _gvn.transform( new JumpNode(control(), key_val, num_cases) );

  // These are the switch destinations hanging off the jumpnode
  int i = 0;
  for (SwitchRange* r = lo; r <= hi; r++) {
    for (int64_t j = r->lo(); j <= r->hi(); j++, i++) {
      Node* input = _gvn.transform(new JumpProjNode(jtn, i, r->dest(), (int)(j - lowval)));
      {
        PreserveJVMState pjvms(this);
        set_control(input);
        jump_if_always_fork(r->dest(), r->table_index());
      }
    }
  }
  assert(i == num_cases, "miscount of cases");
  stop_and_kill_map();  // no more uses for this JVMS
  return true;
}

//----------------------------jump_switch_ranges-------------------------------
void Parse::jump_switch_ranges(Node* key_val, SwitchRange *lo, SwitchRange *hi, int switch_depth) {
  Block* switch_block = block();

  if (switch_depth == 0) {
    // Do special processing for the top-level call.
    assert(lo->lo() == min_jint, "initial range must exhaust Type::INT");
    assert(hi->hi() == max_jint, "initial range must exhaust Type::INT");

    // Decrement pred-numbers for the unique set of nodes.
#ifdef ASSERT
    // Ensure that the block's successors are a (duplicate-free) set.
    int successors_counted = 0;  // block occurrences in [hi..lo]
    int unique_successors = switch_block->num_successors();
    for (int i = 0; i < unique_successors; i++) {
      Block* target = switch_block->successor_at(i);

      // Check that the set of successors is the same in both places.
      int successors_found = 0;
      for (SwitchRange* p = lo; p <= hi; p++) {
        if (p->dest() == target->start())  successors_found++;
      }
      assert(successors_found > 0, "successor must be known");
      successors_counted += successors_found;
    }
    assert(successors_counted == (hi-lo)+1, "no unexpected successors");
#endif

    // Maybe prune the inputs, based on the type of key_val.
    jint min_val = min_jint;
    jint max_val = max_jint;
    const TypeInt* ti = key_val->bottom_type()->isa_int();
    if (ti != NULL) {
      min_val = ti->_lo;
      max_val = ti->_hi;
      assert(min_val <= max_val, "invalid int type");
    }
    while (lo->hi() < min_val)  lo++;
    if (lo->lo() < min_val)  lo->setRange(min_val, lo->hi(), lo->dest(), lo->table_index());
    while (hi->lo() > max_val)  hi--;
    if (hi->hi() > max_val)  hi->setRange(hi->lo(), max_val, hi->dest(), hi->table_index());
  }

#ifndef PRODUCT
  if (switch_depth == 0) {
    _max_switch_depth = 0;
    _est_switch_depth = log2_intptr((hi-lo+1)-1)+1;
  }
#endif

  assert(lo <= hi, "must be a non-empty set of ranges");
  if (lo == hi) {
    jump_if_always_fork(lo->dest(), lo->table_index());
  } else {
    assert(lo->hi() == (lo+1)->lo()-1, "contiguous ranges");
    assert(hi->lo() == (hi-1)->hi()+1, "contiguous ranges");

    if (create_jump_tables(key_val, lo, hi)) return;

    int nr = hi - lo + 1;

    SwitchRange* mid = lo + nr/2;
    // if there is an easy choice, pivot at a singleton:
    if (nr > 3 && !mid->is_singleton() && (mid-1)->is_singleton())  mid--;

    assert(lo < mid && mid <= hi, "good pivot choice");
    assert(nr != 2 || mid == hi,   "should pick higher of 2");
    assert(nr != 3 || mid == hi-1, "should pick middle of 3");

    Node *test_val = _gvn.intcon(mid->lo());

    if (mid->is_singleton()) {
      IfNode *iff_ne = jump_if_fork_int(key_val, test_val, BoolTest::ne);
      jump_if_false_fork(iff_ne, mid->dest(), mid->table_index());

      // Special Case:  If there are exactly three ranges, and the high
      // and low range each go to the same place, omit the "gt" test,
      // since it will not discriminate anything.
      bool eq_test_only = (hi == lo+2 && hi->dest() == lo->dest());
      if (eq_test_only) {
        assert(mid == hi-1, "");
      }

      // if there is a higher range, test for it and process it:
      if (mid < hi && !eq_test_only) {
        // two comparisons of same values--should enable 1 test for 2 branches
        // Use BoolTest::le instead of BoolTest::gt
        IfNode *iff_le  = jump_if_fork_int(key_val, test_val, BoolTest::le);
        Node   *iftrue  = _gvn.transform( new IfTrueNode(iff_le) );
        Node   *iffalse = _gvn.transform( new IfFalseNode(iff_le) );
        { PreserveJVMState pjvms(this);
          set_control(iffalse);
          jump_switch_ranges(key_val, mid+1, hi, switch_depth+1);
        }
        set_control(iftrue);
      }

    } else {
      // mid is a range, not a singleton, so treat mid..hi as a unit
      IfNode *iff_ge = jump_if_fork_int(key_val, test_val, BoolTest::ge);

      // if there is a higher range, test for it and process it:
      if (mid == hi) {
        jump_if_true_fork(iff_ge, mid->dest(), mid->table_index());
      } else {
        Node *iftrue  = _gvn.transform( new IfTrueNode(iff_ge) );
        Node *iffalse = _gvn.transform( new IfFalseNode(iff_ge) );
        { PreserveJVMState pjvms(this);
          set_control(iftrue);
          jump_switch_ranges(key_val, mid, hi, switch_depth+1);
        }
        set_control(iffalse);
      }
    }

    // in any case, process the lower range
    jump_switch_ranges(key_val, lo, mid-1, switch_depth+1);
  }

  // Decrease pred_count for each successor after all is done.
  if (switch_depth == 0) {
    int unique_successors = switch_block->num_successors();
    for (int i = 0; i < unique_successors; i++) {
      Block* target = switch_block->successor_at(i);
      // Throw away the pre-allocated path for each unique successor.
      target->next_path_num();
    }
  }

#ifndef PRODUCT
  _max_switch_depth = MAX2(switch_depth, _max_switch_depth);
  if (TraceOptoParse && Verbose && WizardMode && switch_depth == 0) {
    SwitchRange* r;
    int nsing = 0;
    for( r = lo; r <= hi; r++ ) {
      if( r->is_singleton() )  nsing++;
    }
    tty->print(">>> ");
    _method->print_short_name();
    tty->print_cr(" switch decision tree");
    tty->print_cr("    %d ranges (%d singletons), max_depth=%d, est_depth=%d",
                  (int) (hi-lo+1), nsing, _max_switch_depth, _est_switch_depth);
    if (_max_switch_depth > _est_switch_depth) {
      tty->print_cr("******** BAD SWITCH DEPTH ********");
    }
    tty->print("   ");
    for( r = lo; r <= hi; r++ ) {
      r->print();
    }
    tty->cr();
  }
#endif
}

void Parse::modf() {
  Node *f2 = pop();
  Node *f1 = pop();
  Node* c = make_runtime_call(RC_LEAF, OptoRuntime::modf_Type(),
                              CAST_FROM_FN_PTR(address, SharedRuntime::frem),
                              "frem", NULL, //no memory effects
                              f1, f2);
  Node* res = _gvn.transform(new ProjNode(c, TypeFunc::Parms + 0));

  push(res);
}

void Parse::modd() {
  Node *d2 = pop_pair();
  Node *d1 = pop_pair();
  Node* c = make_runtime_call(RC_LEAF, OptoRuntime::Math_DD_D_Type(),
                              CAST_FROM_FN_PTR(address, SharedRuntime::drem),
                              "drem", NULL, //no memory effects
                              d1, top(), d2, top());
  Node* res_d   = _gvn.transform(new ProjNode(c, TypeFunc::Parms + 0));

#ifdef ASSERT
  Node* res_top = _gvn.transform(new ProjNode(c, TypeFunc::Parms + 1));
  assert(res_top == top(), "second value must be top");
#endif

  push_pair(res_d);
}

void Parse::l2f() {
  Node* f2 = pop();
  Node* f1 = pop();
  Node* c = make_runtime_call(RC_LEAF, OptoRuntime::l2f_Type(),
                              CAST_FROM_FN_PTR(address, SharedRuntime::l2f),
                              "l2f", NULL, //no memory effects
                              f1, f2);
  Node* res = _gvn.transform(new ProjNode(c, TypeFunc::Parms + 0));

  push(res);
}

void Parse::do_irem() {
  // Must keep both values on the expression-stack during null-check
  zero_check_int(peek());
  // Compile-time detect of null-exception?
  if (stopped())  return;

  Node* b = pop();
  Node* a = pop();

  const Type *t = _gvn.type(b);
  if (t != Type::TOP) {
    const TypeInt *ti = t->is_int();
    if (ti->is_con()) {
      int divisor = ti->get_con();
      // check for positive power of 2
      if (divisor > 0 &&
          (divisor & ~(divisor-1)) == divisor) {
        // yes !
        Node *mask = _gvn.intcon((divisor - 1));
        // Sigh, must handle negative dividends
        Node *zero = _gvn.intcon(0);
        IfNode *ifff = jump_if_fork_int(a, zero, BoolTest::lt);
        Node *iff = _gvn.transform( new IfFalseNode(ifff) );
        Node *ift = _gvn.transform( new IfTrueNode (ifff) );
        Node *reg = jump_if_join(ift, iff);
        Node *phi = PhiNode::make(reg, NULL, TypeInt::INT);
        // Negative path; negate/and/negate
        Node *neg = _gvn.transform( new SubINode(zero, a) );
        Node *andn= _gvn.transform( new AndINode(neg, mask) );
        Node *negn= _gvn.transform( new SubINode(zero, andn) );
        phi->init_req(1, negn);
        // Fast positive case
        Node *andx = _gvn.transform( new AndINode(a, mask) );
        phi->init_req(2, andx);
        // Push the merge
        push( _gvn.transform(phi) );
        return;
      }
    }
  }
  // Default case
  push( _gvn.transform( new ModINode(control(),a,b) ) );
}

// Handle jsr and jsr_w bytecode
void Parse::do_jsr() {
  assert(bc() == Bytecodes::_jsr || bc() == Bytecodes::_jsr_w, "wrong bytecode");

  // Store information about current state, tagged with new _jsr_bci
  int return_bci = iter().next_bci();
  int jsr_bci    = (bc() == Bytecodes::_jsr) ? iter().get_dest() : iter().get_far_dest();

  // Update method data
  profile_taken_branch(jsr_bci);

  // The way we do things now, there is only one successor block
  // for the jsr, because the target code is cloned by ciTypeFlow.
  Block* target = successor_for_bci(jsr_bci);

  // What got pushed?
  const Type* ret_addr = target->peek();
  assert(ret_addr->singleton(), "must be a constant (cloned jsr body)");

  // Effect on jsr on stack
  push(_gvn.makecon(ret_addr));

  // Flow to the jsr.
  merge(jsr_bci);
}

// Handle ret bytecode
void Parse::do_ret() {
  // Find to whom we return.
  assert(block()->num_successors() == 1, "a ret can only go one place now");
  Block* target = block()->successor_at(0);
  assert(!target->is_ready(), "our arrival must be expected");
  profile_ret(target->flow()->start());
  int pnum = target->next_path_num();
  merge_common(target, pnum);
}

static bool has_injected_profile(BoolTest::mask btest, Node* test, int& taken, int& not_taken) {
  if (btest != BoolTest::eq && btest != BoolTest::ne) {
    // Only ::eq and ::ne are supported for profile injection.
    return false;
  }
  if (test->is_Cmp() &&
      test->in(1)->Opcode() == Op_ProfileBoolean) {
    ProfileBooleanNode* profile = (ProfileBooleanNode*)test->in(1);
    int false_cnt = profile->false_count();
    int  true_cnt = profile->true_count();

    // Counts matching depends on the actual test operation (::eq or ::ne).
    // No need to scale the counts because profile injection was designed
    // to feed exact counts into VM.
    taken     = (btest == BoolTest::eq) ? false_cnt :  true_cnt;
    not_taken = (btest == BoolTest::eq) ?  true_cnt : false_cnt;

    profile->consume();
    return true;
  }
  return false;
}
//--------------------------dynamic_branch_prediction--------------------------
// Try to gather dynamic branch prediction behavior.  Return a probability
// of the branch being taken and set the "cnt" field.  Returns a -1.0
// if we need to use static prediction for some reason.
float Parse::dynamic_branch_prediction(float &cnt, BoolTest::mask btest, Node* test) {
  ResourceMark rm;

  cnt  = COUNT_UNKNOWN;

  int     taken = 0;
  int not_taken = 0;

  bool use_mdo = !has_injected_profile(btest, test, taken, not_taken);

  if (use_mdo) {
    // Use MethodData information if it is available
    // FIXME: free the ProfileData structure
    ciMethodData* methodData = method()->method_data();
    if (!methodData->is_mature())  return PROB_UNKNOWN;
    ciProfileData* data = methodData->bci_to_data(bci());
    if (data == NULL) {
      return PROB_UNKNOWN;
    }
    if (!data->is_JumpData())  return PROB_UNKNOWN;

    // get taken and not taken values
    taken = data->as_JumpData()->taken();
    not_taken = 0;
    if (data->is_BranchData()) {
      not_taken = data->as_BranchData()->not_taken();
    }

    // scale the counts to be commensurate with invocation counts:
    taken = method()->scale_count(taken);
    not_taken = method()->scale_count(not_taken);
  }

  // Give up if too few (or too many, in which case the sum will overflow) counts to be meaningful.
  // We also check that individual counters are positive first, otherwise the sum can become positive.
  if (taken < 0 || not_taken < 0 || taken + not_taken < 40) {
    if (C->log() != NULL) {
      C->log()->elem("branch target_bci='%d' taken='%d' not_taken='%d'", iter().get_dest(), taken, not_taken);
    }
    return PROB_UNKNOWN;
  }

  // Compute frequency that we arrive here
  float sum = taken + not_taken;
  // Adjust, if this block is a cloned private block but the
  // Jump counts are shared.  Taken the private counts for
  // just this path instead of the shared counts.
  if( block()->count() > 0 )
    sum = block()->count();
  cnt = sum / FreqCountInvocations;

  // Pin probability to sane limits
  float prob;
  if( !taken )
    prob = (0+PROB_MIN) / 2;
  else if( !not_taken )
    prob = (1+PROB_MAX) / 2;
  else {                         // Compute probability of true path
    prob = (float)taken / (float)(taken + not_taken);
    if (prob > PROB_MAX)  prob = PROB_MAX;
    if (prob < PROB_MIN)   prob = PROB_MIN;
  }

  assert((cnt > 0.0f) && (prob > 0.0f),
         "Bad frequency assignment in if");

  if (C->log() != NULL) {
    const char* prob_str = NULL;
    if (prob >= PROB_MAX)  prob_str = (prob == PROB_MAX) ? "max" : "always";
    if (prob <= PROB_MIN)  prob_str = (prob == PROB_MIN) ? "min" : "never";
    char prob_str_buf[30];
    if (prob_str == NULL) {
      sprintf(prob_str_buf, "%g", prob);
      prob_str = prob_str_buf;
    }
    C->log()->elem("branch target_bci='%d' taken='%d' not_taken='%d' cnt='%f' prob='%s'",
                   iter().get_dest(), taken, not_taken, cnt, prob_str);
  }
  return prob;
}

//-----------------------------branch_prediction-------------------------------
float Parse::branch_prediction(float& cnt,
                               BoolTest::mask btest,
                               int target_bci,
                               Node* test) {
  float prob = dynamic_branch_prediction(cnt, btest, test);
  // If prob is unknown, switch to static prediction
  if (prob != PROB_UNKNOWN)  return prob;

  prob = PROB_FAIR;                   // Set default value
  if (btest == BoolTest::eq)          // Exactly equal test?
    prob = PROB_STATIC_INFREQUENT;    // Assume its relatively infrequent
  else if (btest == BoolTest::ne)
    prob = PROB_STATIC_FREQUENT;      // Assume its relatively frequent

  // If this is a conditional test guarding a backwards branch,
  // assume its a loop-back edge.  Make it a likely taken branch.
  if (target_bci < bci()) {
    if (is_osr_parse()) {    // Could be a hot OSR'd loop; force deopt
      // Since it's an OSR, we probably have profile data, but since
      // branch_prediction returned PROB_UNKNOWN, the counts are too small.
      // Let's make a special check here for completely zero counts.
      ciMethodData* methodData = method()->method_data();
      if (!methodData->is_empty()) {
        ciProfileData* data = methodData->bci_to_data(bci());
        // Only stop for truly zero counts, which mean an unknown part
        // of the OSR-ed method, and we want to deopt to gather more stats.
        // If you have ANY counts, then this loop is simply 'cold' relative
        // to the OSR loop.
        if (data == NULL ||
            (data->as_BranchData()->taken() +  data->as_BranchData()->not_taken() == 0)) {
          // This is the only way to return PROB_UNKNOWN:
          return PROB_UNKNOWN;
        }
      }
    }
    prob = PROB_STATIC_FREQUENT;     // Likely to take backwards branch
  }

  assert(prob != PROB_UNKNOWN, "must have some guess at this point");
  return prob;
}

// The magic constants are chosen so as to match the output of
// branch_prediction() when the profile reports a zero taken count.
// It is important to distinguish zero counts unambiguously, because
// some branches (e.g., _213_javac.Assembler.eliminate) validly produce
// very small but nonzero probabilities, which if confused with zero
// counts would keep the program recompiling indefinitely.
bool Parse::seems_never_taken(float prob) const {
  return prob < PROB_MIN;
}

// True if the comparison seems to be the kind that will not change its
// statistics from true to false.  See comments in adjust_map_after_if.
// This question is only asked along paths which are already
// classifed as untaken (by seems_never_taken), so really,
// if a path is never taken, its controlling comparison is
// already acting in a stable fashion.  If the comparison
// seems stable, we will put an expensive uncommon trap
// on the untaken path.
bool Parse::seems_stable_comparison() const {
  if (C->too_many_traps(method(), bci(), Deoptimization::Reason_unstable_if)) {
    return false;
  }
  return true;
}

//-------------------------------repush_if_args--------------------------------
// Push arguments of an "if" bytecode back onto the stack by adjusting _sp.
inline int Parse::repush_if_args() {
  if (PrintOpto && WizardMode) {
    tty->print("defending against excessive implicit null exceptions on %s @%d in ",
               Bytecodes::name(iter().cur_bc()), iter().cur_bci());
    method()->print_name(); tty->cr();
  }
  int bc_depth = - Bytecodes::depth(iter().cur_bc());
  assert(bc_depth == 1 || bc_depth == 2, "only two kinds of branches");
  DEBUG_ONLY(sync_jvms());   // argument(n) requires a synced jvms
  assert(argument(0) != NULL, "must exist");
  assert(bc_depth == 1 || argument(1) != NULL, "two must exist");
  inc_sp(bc_depth);
  return bc_depth;
}

//----------------------------------do_ifnull----------------------------------
void Parse::do_ifnull(BoolTest::mask btest, Node *c) {
  int target_bci = iter().get_dest();

  Block* branch_block = successor_for_bci(target_bci);
  Block* next_block   = successor_for_bci(iter().next_bci());

  float cnt;
  float prob = branch_prediction(cnt, btest, target_bci, c);
  if (prob == PROB_UNKNOWN) {
    // (An earlier version of do_ifnull omitted this trap for OSR methods.)
    if (PrintOpto && Verbose) {
      tty->print_cr("Never-taken edge stops compilation at bci %d", bci());
    }
    repush_if_args(); // to gather stats on loop
    // We need to mark this branch as taken so that if we recompile we will
    // see that it is possible. In the tiered system the interpreter doesn't
    // do profiling and by the time we get to the lower tier from the interpreter
    // the path may be cold again. Make sure it doesn't look untaken
    profile_taken_branch(target_bci, !ProfileInterpreter);
    uncommon_trap(Deoptimization::Reason_unreached,
                  Deoptimization::Action_reinterpret,
                  NULL, "cold");
    if (C->eliminate_boxing()) {
      // Mark the successor blocks as parsed
      branch_block->next_path_num();
      next_block->next_path_num();
    }
    return;
  }

  NOT_PRODUCT(explicit_null_checks_inserted++);

  // Generate real control flow
  Node   *tst = _gvn.transform( new BoolNode( c, btest ) );

  // Sanity check the probability value
  assert(prob > 0.0f,"Bad probability in Parser");
 // Need xform to put node in hash table
  IfNode *iff = create_and_xform_if( control(), tst, prob, cnt );
  assert(iff->_prob > 0.0f,"Optimizer made bad probability in parser");
  // True branch
  { PreserveJVMState pjvms(this);
    Node* iftrue  = _gvn.transform( new IfTrueNode (iff) );
    set_control(iftrue);

    if (stopped()) {            // Path is dead?
      NOT_PRODUCT(explicit_null_checks_elided++);
      if (C->eliminate_boxing()) {
        // Mark the successor block as parsed
        branch_block->next_path_num();
      }
    } else {                    // Path is live.
      // Update method data
      profile_taken_branch(target_bci);
      adjust_map_after_if(btest, c, prob, branch_block, next_block);
      if (!stopped()) {
        merge(target_bci);
      }
    }
  }

  // False branch
  Node* iffalse = _gvn.transform( new IfFalseNode(iff) );
  set_control(iffalse);

  if (stopped()) {              // Path is dead?
    NOT_PRODUCT(explicit_null_checks_elided++);
    if (C->eliminate_boxing()) {
      // Mark the successor block as parsed
      next_block->next_path_num();
    }
  } else  {                     // Path is live.
    // Update method data
    profile_not_taken_branch();
    adjust_map_after_if(BoolTest(btest).negate(), c, 1.0-prob,
                        next_block, branch_block);
  }
}

//------------------------------------do_if------------------------------------
void Parse::do_if(BoolTest::mask btest, Node* c) {
  int target_bci = iter().get_dest();

  Block* branch_block = successor_for_bci(target_bci);
  Block* next_block   = successor_for_bci(iter().next_bci());

  float cnt;
  float prob = branch_prediction(cnt, btest, target_bci, c);
  float untaken_prob = 1.0 - prob;

  if (prob == PROB_UNKNOWN) {
    if (PrintOpto && Verbose) {
      tty->print_cr("Never-taken edge stops compilation at bci %d", bci());
    }
    repush_if_args(); // to gather stats on loop
    // We need to mark this branch as taken so that if we recompile we will
    // see that it is possible. In the tiered system the interpreter doesn't
    // do profiling and by the time we get to the lower tier from the interpreter
    // the path may be cold again. Make sure it doesn't look untaken
    profile_taken_branch(target_bci, !ProfileInterpreter);
    uncommon_trap(Deoptimization::Reason_unreached,
                  Deoptimization::Action_reinterpret,
                  NULL, "cold");
    if (C->eliminate_boxing()) {
      // Mark the successor blocks as parsed
      branch_block->next_path_num();
      next_block->next_path_num();
    }
    return;
  }

  // Sanity check the probability value
  assert(0.0f < prob && prob < 1.0f,"Bad probability in Parser");

  bool taken_if_true = true;
  // Convert BoolTest to canonical form:
  if (!BoolTest(btest).is_canonical()) {
    btest         = BoolTest(btest).negate();
    taken_if_true = false;
    // prob is NOT updated here; it remains the probability of the taken
    // path (as opposed to the prob of the path guarded by an 'IfTrueNode').
  }
  assert(btest != BoolTest::eq, "!= is the only canonical exact test");

  Node* tst0 = new BoolNode(c, btest);
  Node* tst = _gvn.transform(tst0);
  BoolTest::mask taken_btest   = BoolTest::illegal;
  BoolTest::mask untaken_btest = BoolTest::illegal;

  if (tst->is_Bool()) {
    // Refresh c from the transformed bool node, since it may be
    // simpler than the original c.  Also re-canonicalize btest.
    // This wins when (Bool ne (Conv2B p) 0) => (Bool ne (CmpP p NULL)).
    // That can arise from statements like: if (x instanceof C) ...
    if (tst != tst0) {
      // Canonicalize one more time since transform can change it.
      btest = tst->as_Bool()->_test._test;
      if (!BoolTest(btest).is_canonical()) {
        // Reverse edges one more time...
        tst   = _gvn.transform( tst->as_Bool()->negate(&_gvn) );
        btest = tst->as_Bool()->_test._test;
        assert(BoolTest(btest).is_canonical(), "sanity");
        taken_if_true = !taken_if_true;
      }
      c = tst->in(1);
    }
    BoolTest::mask neg_btest = BoolTest(btest).negate();
    taken_btest   = taken_if_true ?     btest : neg_btest;
    untaken_btest = taken_if_true ? neg_btest :     btest;
  }

  // Generate real control flow
  float true_prob = (taken_if_true ? prob : untaken_prob);
  IfNode* iff = create_and_map_if(control(), tst, true_prob, cnt);
  assert(iff->_prob > 0.0f,"Optimizer made bad probability in parser");
  Node* taken_branch   = new IfTrueNode(iff);
  Node* untaken_branch = new IfFalseNode(iff);
  if (!taken_if_true) {  // Finish conversion to canonical form
    Node* tmp      = taken_branch;
    taken_branch   = untaken_branch;
    untaken_branch = tmp;
  }

  // Branch is taken:
  { PreserveJVMState pjvms(this);
    taken_branch = _gvn.transform(taken_branch);
    set_control(taken_branch);

    if (stopped()) {
      if (C->eliminate_boxing()) {
        // Mark the successor block as parsed
        branch_block->next_path_num();
      }
    } else {
      // Update method data
      profile_taken_branch(target_bci);
      adjust_map_after_if(taken_btest, c, prob, branch_block, next_block);
      if (!stopped()) {
        merge(target_bci);
      }
    }
  }

  untaken_branch = _gvn.transform(untaken_branch);
  set_control(untaken_branch);

  // Branch not taken.
  if (stopped()) {
    if (C->eliminate_boxing()) {
      // Mark the successor block as parsed
      next_block->next_path_num();
    }
  } else {
    // Update method data
    profile_not_taken_branch();
    adjust_map_after_if(untaken_btest, c, untaken_prob,
                        next_block, branch_block);
  }
}

bool Parse::path_is_suitable_for_uncommon_trap(float prob) const {
  // Don't want to speculate on uncommon traps when running with -Xcomp
  if (!UseInterpreter) {
    return false;
  }
  return (seems_never_taken(prob) && seems_stable_comparison());
}

//----------------------------adjust_map_after_if------------------------------
// Adjust the JVM state to reflect the result of taking this path.
// Basically, it means inspecting the CmpNode controlling this
// branch, seeing how it constrains a tested value, and then
// deciding if it's worth our while to encode this constraint
// as graph nodes in the current abstract interpretation map.
void Parse::adjust_map_after_if(BoolTest::mask btest, Node* c, float prob,
                                Block* path, Block* other_path) {
  if (stopped() || !c->is_Cmp() || btest == BoolTest::illegal)
    return;                             // nothing to do

  bool is_fallthrough = (path == successor_for_bci(iter().next_bci()));

  if (path_is_suitable_for_uncommon_trap(prob)) {
    repush_if_args();
    uncommon_trap(Deoptimization::Reason_unstable_if,
                  Deoptimization::Action_reinterpret,
                  NULL,
                  (is_fallthrough ? "taken always" : "taken never"));
    return;
  }

  Node* val = c->in(1);
  Node* con = c->in(2);
  const Type* tcon = _gvn.type(con);
  const Type* tval = _gvn.type(val);
  bool have_con = tcon->singleton();
  if (tval->singleton()) {
    if (!have_con) {
      // Swap, so constant is in con.
      con  = val;
      tcon = tval;
      val  = c->in(2);
      tval = _gvn.type(val);
      btest = BoolTest(btest).commute();
      have_con = true;
    } else {
      // Do we have two constants?  Then leave well enough alone.
      have_con = false;
    }
  }
  if (!have_con)                        // remaining adjustments need a con
    return;

  sharpen_type_after_if(btest, con, tcon, val, tval);
}


static Node* extract_obj_from_klass_load(PhaseGVN* gvn, Node* n) {
  Node* ldk;
  if (n->is_DecodeNKlass()) {
    if (n->in(1)->Opcode() != Op_LoadNKlass) {
      return NULL;
    } else {
      ldk = n->in(1);
    }
  } else if (n->Opcode() != Op_LoadKlass) {
    return NULL;
  } else {
    ldk = n;
  }
  assert(ldk != NULL && ldk->is_Load(), "should have found a LoadKlass or LoadNKlass node");

  Node* adr = ldk->in(MemNode::Address);
  intptr_t off = 0;
  Node* obj = AddPNode::Ideal_base_and_offset(adr, gvn, off);
  if (obj == NULL || off != oopDesc::klass_offset_in_bytes()) // loading oopDesc::_klass?
    return NULL;
  const TypePtr* tp = gvn->type(obj)->is_ptr();
  if (tp == NULL || !(tp->isa_instptr() || tp->isa_aryptr())) // is obj a Java object ptr?
    return NULL;

  return obj;
}

void Parse::sharpen_type_after_if(BoolTest::mask btest,
                                  Node* con, const Type* tcon,
                                  Node* val, const Type* tval) {
  // Look for opportunities to sharpen the type of a node
  // whose klass is compared with a constant klass.
  if (btest == BoolTest::eq && tcon->isa_klassptr()) {
    Node* obj = extract_obj_from_klass_load(&_gvn, val);
    const TypeOopPtr* con_type = tcon->isa_klassptr()->as_instance_type();
    if (obj != NULL && (con_type->isa_instptr() || con_type->isa_aryptr())) {
       // Found:
       //   Bool(CmpP(LoadKlass(obj._klass), ConP(Foo.klass)), [eq])
       // or the narrowOop equivalent.
       const Type* obj_type = _gvn.type(obj);
       const TypeOopPtr* tboth = obj_type->join_speculative(con_type)->isa_oopptr();
       if (tboth != NULL && tboth->klass_is_exact() && tboth != obj_type &&
           tboth->higher_equal(obj_type)) {
          // obj has to be of the exact type Foo if the CmpP succeeds.
          int obj_in_map = map()->find_edge(obj);
          JVMState* jvms = this->jvms();
          if (obj_in_map >= 0 &&
              (jvms->is_loc(obj_in_map) || jvms->is_stk(obj_in_map))) {
            TypeNode* ccast = new CheckCastPPNode(control(), obj, tboth);
            const Type* tcc = ccast->as_Type()->type();
            assert(tcc != obj_type && tcc->higher_equal(obj_type), "must improve");
            // Delay transform() call to allow recovery of pre-cast value
            // at the control merge.
            _gvn.set_type_bottom(ccast);
            record_for_igvn(ccast);
            // Here's the payoff.
            replace_in_map(obj, ccast);
          }
       }
    }
  }

  int val_in_map = map()->find_edge(val);
  if (val_in_map < 0)  return;          // replace_in_map would be useless
  {
    JVMState* jvms = this->jvms();
    if (!(jvms->is_loc(val_in_map) ||
          jvms->is_stk(val_in_map)))
      return;                           // again, it would be useless
  }

  // Check for a comparison to a constant, and "know" that the compared
  // value is constrained on this path.
  assert(tcon->singleton(), "");
  ConstraintCastNode* ccast = NULL;
  Node* cast = NULL;

  switch (btest) {
  case BoolTest::eq:                    // Constant test?
    {
      const Type* tboth = tcon->join_speculative(tval);
      if (tboth == tval)  break;        // Nothing to gain.
      if (tcon->isa_int()) {
        ccast = new CastIINode(val, tboth);
      } else if (tcon == TypePtr::NULL_PTR) {
        // Cast to null, but keep the pointer identity temporarily live.
        ccast = new CastPPNode(val, tboth);
      } else {
        const TypeF* tf = tcon->isa_float_constant();
        const TypeD* td = tcon->isa_double_constant();
        // Exclude tests vs float/double 0 as these could be
        // either +0 or -0.  Just because you are equal to +0
        // doesn't mean you ARE +0!
        // Note, following code also replaces Long and Oop values.
        if ((!tf || tf->_f != 0.0) &&
            (!td || td->_d != 0.0))
          cast = con;                   // Replace non-constant val by con.
      }
    }
    break;

  case BoolTest::ne:
    if (tcon == TypePtr::NULL_PTR) {
      cast = cast_not_null(val, false);
    }
    break;

  default:
    // (At this point we could record int range types with CastII.)
    break;
  }

  if (ccast != NULL) {
    const Type* tcc = ccast->as_Type()->type();
    assert(tcc != tval && tcc->higher_equal(tval), "must improve");
    // Delay transform() call to allow recovery of pre-cast value
    // at the control merge.
    ccast->set_req(0, control());
    _gvn.set_type_bottom(ccast);
    record_for_igvn(ccast);
    cast = ccast;
  }

  if (cast != NULL) {                   // Here's the payoff.
    replace_in_map(val, cast);
  }
}

/**
 * Use speculative type to optimize CmpP node: if comparison is
 * against the low level class, cast the object to the speculative
 * type if any. CmpP should then go away.
 *
 * @param c  expected CmpP node
 * @return   result of CmpP on object casted to speculative type
 *
 */
Node* Parse::optimize_cmp_with_klass(Node* c) {
  // If this is transformed by the _gvn to a comparison with the low
  // level klass then we may be able to use speculation
  if (c->Opcode() == Op_CmpP &&
      (c->in(1)->Opcode() == Op_LoadKlass || c->in(1)->Opcode() == Op_DecodeNKlass) &&
      c->in(2)->is_Con()) {
    Node* load_klass = NULL;
    Node* decode = NULL;
    if (c->in(1)->Opcode() == Op_DecodeNKlass) {
      decode = c->in(1);
      load_klass = c->in(1)->in(1);
    } else {
      load_klass = c->in(1);
    }
    if (load_klass->in(2)->is_AddP()) {
      Node* addp = load_klass->in(2);
      Node* obj = addp->in(AddPNode::Address);
      const TypeOopPtr* obj_type = _gvn.type(obj)->is_oopptr();
      if (obj_type->speculative_type_not_null() != NULL) {
        ciKlass* k = obj_type->speculative_type();
        inc_sp(2);
        obj = maybe_cast_profiled_obj(obj, k);
        dec_sp(2);
        // Make the CmpP use the casted obj
        addp = basic_plus_adr(obj, addp->in(AddPNode::Offset));
        load_klass = load_klass->clone();
        load_klass->set_req(2, addp);
        load_klass = _gvn.transform(load_klass);
        if (decode != NULL) {
          decode = decode->clone();
          decode->set_req(1, load_klass);
          load_klass = _gvn.transform(decode);
        }
        c = c->clone();
        c->set_req(1, load_klass);
        c = _gvn.transform(c);
      }
    }
  }
  return c;
}

//------------------------------do_one_bytecode--------------------------------
// Parse this bytecode, and alter the Parsers JVM->Node mapping
void Parse::do_one_bytecode() {
  Node *a, *b, *c, *d;          // Handy temps
  BoolTest::mask btest;
  int i;

  assert(!has_exceptions(), "bytecode entry state must be clear of throws");

  if (C->check_node_count(NodeLimitFudgeFactor * 5,
                          "out of nodes parsing method")) {
    return;
  }

#ifdef ASSERT
  // for setting breakpoints
  if (TraceOptoParse) {
    tty->print(" @");
    dump_bci(bci());
    tty->cr();
  }
#endif

  switch (bc()) {
  case Bytecodes::_nop:
    // do nothing
    break;
  case Bytecodes::_lconst_0:
    push_pair(longcon(0));
    break;

  case Bytecodes::_lconst_1:
    push_pair(longcon(1));
    break;

  case Bytecodes::_fconst_0:
    push(zerocon(T_FLOAT));
    break;

  case Bytecodes::_fconst_1:
    push(makecon(TypeF::ONE));
    break;

  case Bytecodes::_fconst_2:
    push(makecon(TypeF::make(2.0f)));
    break;

  case Bytecodes::_dconst_0:
    push_pair(zerocon(T_DOUBLE));
    break;

  case Bytecodes::_dconst_1:
    push_pair(makecon(TypeD::ONE));
    break;

  case Bytecodes::_iconst_m1:push(intcon(-1)); break;
  case Bytecodes::_iconst_0: push(intcon( 0)); break;
  case Bytecodes::_iconst_1: push(intcon( 1)); break;
  case Bytecodes::_iconst_2: push(intcon( 2)); break;
  case Bytecodes::_iconst_3: push(intcon( 3)); break;
  case Bytecodes::_iconst_4: push(intcon( 4)); break;
  case Bytecodes::_iconst_5: push(intcon( 5)); break;
  case Bytecodes::_bipush:   push(intcon(iter().get_constant_u1())); break;
  case Bytecodes::_sipush:   push(intcon(iter().get_constant_u2())); break;
  case Bytecodes::_aconst_null: push(null());  break;
  case Bytecodes::_ldc:
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
    // If the constant is unresolved, run this BC once in the interpreter.
    {
      ciConstant constant = iter().get_constant();
      if (constant.basic_type() == T_OBJECT &&
          !constant.as_object()->is_loaded()) {
        int index = iter().get_constant_pool_index();
        constantTag tag = iter().get_constant_pool_tag(index);
        uncommon_trap(Deoptimization::make_trap_request
                      (Deoptimization::Reason_unloaded,
                       Deoptimization::Action_reinterpret,
                       index),
                      NULL, tag.internal_name());
        break;
      }
      assert(constant.basic_type() != T_OBJECT || constant.as_object()->is_instance(),
             "must be java_mirror of klass");
      const Type* con_type = Type::make_from_constant(constant);
      if (con_type != NULL) {
        push_node(con_type->basic_type(), makecon(con_type));
      }
    }

    break;

  case Bytecodes::_aload_0:
    push( local(0) );
    break;
  case Bytecodes::_aload_1:
    push( local(1) );
    break;
  case Bytecodes::_aload_2:
    push( local(2) );
    break;
  case Bytecodes::_aload_3:
    push( local(3) );
    break;
  case Bytecodes::_aload:
  case Bytecodes::_vload:
    push( local(iter().get_index()) );
    break;

  case Bytecodes::_fload_0:
  case Bytecodes::_iload_0:
    push( local(0) );
    break;
  case Bytecodes::_fload_1:
  case Bytecodes::_iload_1:
    push( local(1) );
    break;
  case Bytecodes::_fload_2:
  case Bytecodes::_iload_2:
    push( local(2) );
    break;
  case Bytecodes::_fload_3:
  case Bytecodes::_iload_3:
    push( local(3) );
    break;
  case Bytecodes::_fload:
  case Bytecodes::_iload:
    push( local(iter().get_index()) );
    break;
  case Bytecodes::_lload_0:
    push_pair_local( 0 );
    break;
  case Bytecodes::_lload_1:
    push_pair_local( 1 );
    break;
  case Bytecodes::_lload_2:
    push_pair_local( 2 );
    break;
  case Bytecodes::_lload_3:
    push_pair_local( 3 );
    break;
  case Bytecodes::_lload:
    push_pair_local( iter().get_index() );
    break;

  case Bytecodes::_dload_0:
    push_pair_local(0);
    break;
  case Bytecodes::_dload_1:
    push_pair_local(1);
    break;
  case Bytecodes::_dload_2:
    push_pair_local(2);
    break;
  case Bytecodes::_dload_3:
    push_pair_local(3);
    break;
  case Bytecodes::_dload:
    push_pair_local(iter().get_index());
    break;
  case Bytecodes::_fstore_0:
  case Bytecodes::_istore_0:
  case Bytecodes::_astore_0:
    set_local( 0, pop() );
    break;
  case Bytecodes::_fstore_1:
  case Bytecodes::_istore_1:
  case Bytecodes::_astore_1:
    set_local( 1, pop() );
    break;
  case Bytecodes::_fstore_2:
  case Bytecodes::_istore_2:
  case Bytecodes::_astore_2:
    set_local( 2, pop() );
    break;
  case Bytecodes::_fstore_3:
  case Bytecodes::_istore_3:
  case Bytecodes::_astore_3:
    set_local( 3, pop() );
    break;
  case Bytecodes::_fstore:
  case Bytecodes::_istore:
  case Bytecodes::_astore:
  case Bytecodes::_vstore:
    set_local( iter().get_index(), pop() );
    break;
  // long stores
  case Bytecodes::_lstore_0:
    set_pair_local( 0, pop_pair() );
    break;
  case Bytecodes::_lstore_1:
    set_pair_local( 1, pop_pair() );
    break;
  case Bytecodes::_lstore_2:
    set_pair_local( 2, pop_pair() );
    break;
  case Bytecodes::_lstore_3:
    set_pair_local( 3, pop_pair() );
    break;
  case Bytecodes::_lstore:
    set_pair_local( iter().get_index(), pop_pair() );
    break;

  // double stores
  case Bytecodes::_dstore_0:
    set_pair_local( 0, dstore_rounding(pop_pair()) );
    break;
  case Bytecodes::_dstore_1:
    set_pair_local( 1, dstore_rounding(pop_pair()) );
    break;
  case Bytecodes::_dstore_2:
    set_pair_local( 2, dstore_rounding(pop_pair()) );
    break;
  case Bytecodes::_dstore_3:
    set_pair_local( 3, dstore_rounding(pop_pair()) );
    break;
  case Bytecodes::_dstore:
    set_pair_local( iter().get_index(), dstore_rounding(pop_pair()) );
    break;

  case Bytecodes::_pop:  dec_sp(1);   break;
  case Bytecodes::_pop2: dec_sp(2);   break;
  case Bytecodes::_swap:
    a = pop();
    b = pop();
    push(a);
    push(b);
    break;
  case Bytecodes::_dup:
    a = pop();
    push(a);
    push(a);
    break;
  case Bytecodes::_dup_x1:
    a = pop();
    b = pop();
    push( a );
    push( b );
    push( a );
    break;
  case Bytecodes::_dup_x2:
    a = pop();
    b = pop();
    c = pop();
    push( a );
    push( c );
    push( b );
    push( a );
    break;
  case Bytecodes::_dup2:
    a = pop();
    b = pop();
    push( b );
    push( a );
    push( b );
    push( a );
    break;

  case Bytecodes::_dup2_x1:
    // before: .. c, b, a
    // after:  .. b, a, c, b, a
    // not tested
    a = pop();
    b = pop();
    c = pop();
    push( b );
    push( a );
    push( c );
    push( b );
    push( a );
    break;
  case Bytecodes::_dup2_x2:
    // before: .. d, c, b, a
    // after:  .. b, a, d, c, b, a
    // not tested
    a = pop();
    b = pop();
    c = pop();
    d = pop();
    push( b );
    push( a );
    push( d );
    push( c );
    push( b );
    push( a );
    break;

  case Bytecodes::_arraylength: {
    // Must do null-check with value on expression stack
    Node *ary = null_check(peek(), T_ARRAY);
    // Compile-time detect of null-exception?
    if (stopped())  return;
    a = pop();
    push(load_array_length(a));
    break;
  }

  case Bytecodes::_baload: array_load(T_BYTE);   break;
  case Bytecodes::_caload: array_load(T_CHAR);   break;
  case Bytecodes::_iaload: array_load(T_INT);    break;
  case Bytecodes::_saload: array_load(T_SHORT);  break;
  case Bytecodes::_faload: array_load(T_FLOAT);  break;
  case Bytecodes::_vaload: array_load(T_VALUETYPE); break;
  case Bytecodes::_aaload: array_load(T_OBJECT); break;
  case Bytecodes::_laload: {
    a = array_addressing(T_LONG, 0);
    if (stopped())  return;     // guaranteed null or range check
    dec_sp(2);                  // Pop array and index
    push_pair(make_load(control(), a, TypeLong::LONG, T_LONG, TypeAryPtr::LONGS, MemNode::unordered));
    break;
  }
  case Bytecodes::_daload: {
    a = array_addressing(T_DOUBLE, 0);
    if (stopped())  return;     // guaranteed null or range check
    dec_sp(2);                  // Pop array and index
    push_pair(make_load(control(), a, Type::DOUBLE, T_DOUBLE, TypeAryPtr::DOUBLES, MemNode::unordered));
    break;
  }
  case Bytecodes::_bastore: array_store(T_BYTE);  break;
  case Bytecodes::_castore: array_store(T_CHAR);  break;
  case Bytecodes::_iastore: array_store(T_INT);   break;
  case Bytecodes::_sastore: array_store(T_SHORT); break;
  case Bytecodes::_fastore: array_store(T_FLOAT); break;
  case Bytecodes::_vastore: {
    d = array_addressing(T_OBJECT, 1);
    if (stopped())  return;     // guaranteed null or range check
    array_store_check(true);
    c = pop();                  // Oop to store
    b = pop();                  // index (already used)
    a = pop();                  // the array itself
    const TypeAryPtr* arytype = _gvn.type(a)->is_aryptr();
    const Type* elemtype = arytype->elem();

    if (elemtype->isa_valuetype()) {
      c->as_ValueType()->store_flattened(this, a, d);
      break;
    }

    const TypeAryPtr* adr_type = TypeAryPtr::OOPS;
    Node* oop = c->as_ValueType()->allocate(this)->get_oop();
    Node* store = store_oop_to_array(control(), a, d, adr_type, oop, elemtype->make_oopptr(), T_OBJECT,
                                     StoreNode::release_if_reference(T_OBJECT));
    break;
  }
  case Bytecodes::_aastore: {
    d = array_addressing(T_OBJECT, 1);
    if (stopped())  return;     // guaranteed null or range check
    array_store_check();
    c = pop();                  // Oop to store
    b = pop();                  // index (already used)
    a = pop();                  // the array itself
    const TypeOopPtr* elemtype  = _gvn.type(a)->is_aryptr()->elem()->make_oopptr();
    const TypeAryPtr* adr_type = TypeAryPtr::OOPS;
    Node* store = store_oop_to_array(control(), a, d, adr_type, c, elemtype, T_OBJECT,
                                     StoreNode::release_if_reference(T_OBJECT));
    break;
  }
  case Bytecodes::_lastore: {
    a = array_addressing(T_LONG, 2);
    if (stopped())  return;     // guaranteed null or range check
    c = pop_pair();
    dec_sp(2);                  // Pop array and index
    store_to_memory(control(), a, c, T_LONG, TypeAryPtr::LONGS, MemNode::unordered);
    break;
  }
  case Bytecodes::_dastore: {
    a = array_addressing(T_DOUBLE, 2);
    if (stopped())  return;     // guaranteed null or range check
    c = pop_pair();
    dec_sp(2);                  // Pop array and index
    c = dstore_rounding(c);
    store_to_memory(control(), a, c, T_DOUBLE, TypeAryPtr::DOUBLES, MemNode::unordered);
    break;
  }

  case Bytecodes::_getfield:
    do_getfield();
    break;

  case Bytecodes::_getstatic:
    do_getstatic();
    break;

  case Bytecodes::_putfield:
    do_putfield();
    break;

  case Bytecodes::_putstatic:
    do_putstatic();
    break;

  case Bytecodes::_irem:
    do_irem();
    break;
  case Bytecodes::_idiv:
    // Must keep both values on the expression-stack during null-check
    zero_check_int(peek());
    // Compile-time detect of null-exception?
    if (stopped())  return;
    b = pop();
    a = pop();
    push( _gvn.transform( new DivINode(control(),a,b) ) );
    break;
  case Bytecodes::_imul:
    b = pop(); a = pop();
    push( _gvn.transform( new MulINode(a,b) ) );
    break;
  case Bytecodes::_iadd:
    b = pop(); a = pop();
    push( _gvn.transform( new AddINode(a,b) ) );
    break;
  case Bytecodes::_ineg:
    a = pop();
    push( _gvn.transform( new SubINode(_gvn.intcon(0),a)) );
    break;
  case Bytecodes::_isub:
    b = pop(); a = pop();
    push( _gvn.transform( new SubINode(a,b) ) );
    break;
  case Bytecodes::_iand:
    b = pop(); a = pop();
    push( _gvn.transform( new AndINode(a,b) ) );
    break;
  case Bytecodes::_ior:
    b = pop(); a = pop();
    push( _gvn.transform( new OrINode(a,b) ) );
    break;
  case Bytecodes::_ixor:
    b = pop(); a = pop();
    push( _gvn.transform( new XorINode(a,b) ) );
    break;
  case Bytecodes::_ishl:
    b = pop(); a = pop();
    push( _gvn.transform( new LShiftINode(a,b) ) );
    break;
  case Bytecodes::_ishr:
    b = pop(); a = pop();
    push( _gvn.transform( new RShiftINode(a,b) ) );
    break;
  case Bytecodes::_iushr:
    b = pop(); a = pop();
    push( _gvn.transform( new URShiftINode(a,b) ) );
    break;

  case Bytecodes::_fneg:
    a = pop();
    b = _gvn.transform(new NegFNode (a));
    push(b);
    break;

  case Bytecodes::_fsub:
    b = pop();
    a = pop();
    c = _gvn.transform( new SubFNode(a,b) );
    d = precision_rounding(c);
    push( d );
    break;

  case Bytecodes::_fadd:
    b = pop();
    a = pop();
    c = _gvn.transform( new AddFNode(a,b) );
    d = precision_rounding(c);
    push( d );
    break;

  case Bytecodes::_fmul:
    b = pop();
    a = pop();
    c = _gvn.transform( new MulFNode(a,b) );
    d = precision_rounding(c);
    push( d );
    break;

  case Bytecodes::_fdiv:
    b = pop();
    a = pop();
    c = _gvn.transform( new DivFNode(0,a,b) );
    d = precision_rounding(c);
    push( d );
    break;

  case Bytecodes::_frem:
    if (Matcher::has_match_rule(Op_ModF)) {
      // Generate a ModF node.
      b = pop();
      a = pop();
      c = _gvn.transform( new ModFNode(0,a,b) );
      d = precision_rounding(c);
      push( d );
    }
    else {
      // Generate a call.
      modf();
    }
    break;

  case Bytecodes::_fcmpl:
    b = pop();
    a = pop();
    c = _gvn.transform( new CmpF3Node( a, b));
    push(c);
    break;
  case Bytecodes::_fcmpg:
    b = pop();
    a = pop();

    // Same as fcmpl but need to flip the unordered case.  Swap the inputs,
    // which negates the result sign except for unordered.  Flip the unordered
    // as well by using CmpF3 which implements unordered-lesser instead of
    // unordered-greater semantics.  Finally, commute the result bits.  Result
    // is same as using a CmpF3Greater except we did it with CmpF3 alone.
    c = _gvn.transform( new CmpF3Node( b, a));
    c = _gvn.transform( new SubINode(_gvn.intcon(0),c) );
    push(c);
    break;

  case Bytecodes::_f2i:
    a = pop();
    push(_gvn.transform(new ConvF2INode(a)));
    break;

  case Bytecodes::_d2i:
    a = pop_pair();
    b = _gvn.transform(new ConvD2INode(a));
    push( b );
    break;

  case Bytecodes::_f2d:
    a = pop();
    b = _gvn.transform( new ConvF2DNode(a));
    push_pair( b );
    break;

  case Bytecodes::_d2f:
    a = pop_pair();
    b = _gvn.transform( new ConvD2FNode(a));
    // This breaks _227_mtrt (speed & correctness) and _222_mpegaudio (speed)
    //b = _gvn.transform(new RoundFloatNode(0, b) );
    push( b );
    break;

  case Bytecodes::_l2f:
    if (Matcher::convL2FSupported()) {
      a = pop_pair();
      b = _gvn.transform( new ConvL2FNode(a));
      // For i486.ad, FILD doesn't restrict precision to 24 or 53 bits.
      // Rather than storing the result into an FP register then pushing
      // out to memory to round, the machine instruction that implements
      // ConvL2D is responsible for rounding.
      // c = precision_rounding(b);
      c = _gvn.transform(b);
      push(c);
    } else {
      l2f();
    }
    break;

  case Bytecodes::_l2d:
    a = pop_pair();
    b = _gvn.transform( new ConvL2DNode(a));
    // For i486.ad, rounding is always necessary (see _l2f above).
    // c = dprecision_rounding(b);
    c = _gvn.transform(b);
    push_pair(c);
    break;

  case Bytecodes::_f2l:
    a = pop();
    b = _gvn.transform( new ConvF2LNode(a));
    push_pair(b);
    break;

  case Bytecodes::_d2l:
    a = pop_pair();
    b = _gvn.transform( new ConvD2LNode(a));
    push_pair(b);
    break;

  case Bytecodes::_dsub:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new SubDNode(a,b) );
    d = dprecision_rounding(c);
    push_pair( d );
    break;

  case Bytecodes::_dadd:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new AddDNode(a,b) );
    d = dprecision_rounding(c);
    push_pair( d );
    break;

  case Bytecodes::_dmul:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new MulDNode(a,b) );
    d = dprecision_rounding(c);
    push_pair( d );
    break;

  case Bytecodes::_ddiv:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new DivDNode(0,a,b) );
    d = dprecision_rounding(c);
    push_pair( d );
    break;

  case Bytecodes::_dneg:
    a = pop_pair();
    b = _gvn.transform(new NegDNode (a));
    push_pair(b);
    break;

  case Bytecodes::_drem:
    if (Matcher::has_match_rule(Op_ModD)) {
      // Generate a ModD node.
      b = pop_pair();
      a = pop_pair();
      // a % b

      c = _gvn.transform( new ModDNode(0,a,b) );
      d = dprecision_rounding(c);
      push_pair( d );
    }
    else {
      // Generate a call.
      modd();
    }
    break;

  case Bytecodes::_dcmpl:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new CmpD3Node( a, b));
    push(c);
    break;

  case Bytecodes::_dcmpg:
    b = pop_pair();
    a = pop_pair();
    // Same as dcmpl but need to flip the unordered case.
    // Commute the inputs, which negates the result sign except for unordered.
    // Flip the unordered as well by using CmpD3 which implements
    // unordered-lesser instead of unordered-greater semantics.
    // Finally, negate the result bits.  Result is same as using a
    // CmpD3Greater except we did it with CmpD3 alone.
    c = _gvn.transform( new CmpD3Node( b, a));
    c = _gvn.transform( new SubINode(_gvn.intcon(0),c) );
    push(c);
    break;


    // Note for longs -> lo word is on TOS, hi word is on TOS - 1
  case Bytecodes::_land:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new AndLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lor:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new OrLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lxor:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new XorLNode(a,b) );
    push_pair(c);
    break;

  case Bytecodes::_lshl:
    b = pop();                  // the shift count
    a = pop_pair();             // value to be shifted
    c = _gvn.transform( new LShiftLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lshr:
    b = pop();                  // the shift count
    a = pop_pair();             // value to be shifted
    c = _gvn.transform( new RShiftLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lushr:
    b = pop();                  // the shift count
    a = pop_pair();             // value to be shifted
    c = _gvn.transform( new URShiftLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lmul:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new MulLNode(a,b) );
    push_pair(c);
    break;

  case Bytecodes::_lrem:
    // Must keep both values on the expression-stack during null-check
    assert(peek(0) == top(), "long word order");
    zero_check_long(peek(1));
    // Compile-time detect of null-exception?
    if (stopped())  return;
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new ModLNode(control(),a,b) );
    push_pair(c);
    break;

  case Bytecodes::_ldiv:
    // Must keep both values on the expression-stack during null-check
    assert(peek(0) == top(), "long word order");
    zero_check_long(peek(1));
    // Compile-time detect of null-exception?
    if (stopped())  return;
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new DivLNode(control(),a,b) );
    push_pair(c);
    break;

  case Bytecodes::_ladd:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new AddLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lsub:
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new SubLNode(a,b) );
    push_pair(c);
    break;
  case Bytecodes::_lcmp:
    // Safepoints are now inserted _before_ branches.  The long-compare
    // bytecode painfully produces a 3-way value (-1,0,+1) which requires a
    // slew of control flow.  These are usually followed by a CmpI vs zero and
    // a branch; this pattern then optimizes to the obvious long-compare and
    // branch.  However, if the branch is backwards there's a Safepoint
    // inserted.  The inserted Safepoint captures the JVM state at the
    // pre-branch point, i.e. it captures the 3-way value.  Thus if a
    // long-compare is used to control a loop the debug info will force
    // computation of the 3-way value, even though the generated code uses a
    // long-compare and branch.  We try to rectify the situation by inserting
    // a SafePoint here and have it dominate and kill the safepoint added at a
    // following backwards branch.  At this point the JVM state merely holds 2
    // longs but not the 3-way value.
    if( UseLoopSafepoints ) {
      switch( iter().next_bc() ) {
      case Bytecodes::_ifgt:
      case Bytecodes::_iflt:
      case Bytecodes::_ifge:
      case Bytecodes::_ifle:
      case Bytecodes::_ifne:
      case Bytecodes::_ifeq:
        // If this is a backwards branch in the bytecodes, add Safepoint
        maybe_add_safepoint(iter().next_get_dest());
      default:
        break;
      }
    }
    b = pop_pair();
    a = pop_pair();
    c = _gvn.transform( new CmpL3Node( a, b ));
    push(c);
    break;

  case Bytecodes::_lneg:
    a = pop_pair();
    b = _gvn.transform( new SubLNode(longcon(0),a));
    push_pair(b);
    break;
  case Bytecodes::_l2i:
    a = pop_pair();
    push( _gvn.transform( new ConvL2INode(a)));
    break;
  case Bytecodes::_i2l:
    a = pop();
    b = _gvn.transform( new ConvI2LNode(a));
    push_pair(b);
    break;
  case Bytecodes::_i2b:
    // Sign extend
    a = pop();
    a = _gvn.transform( new LShiftINode(a,_gvn.intcon(24)) );
    a = _gvn.transform( new RShiftINode(a,_gvn.intcon(24)) );
    push( a );
    break;
  case Bytecodes::_i2s:
    a = pop();
    a = _gvn.transform( new LShiftINode(a,_gvn.intcon(16)) );
    a = _gvn.transform( new RShiftINode(a,_gvn.intcon(16)) );
    push( a );
    break;
  case Bytecodes::_i2c:
    a = pop();
    push( _gvn.transform( new AndINode(a,_gvn.intcon(0xFFFF)) ) );
    break;

  case Bytecodes::_i2f:
    a = pop();
    b = _gvn.transform( new ConvI2FNode(a) ) ;
    c = precision_rounding(b);
    push (b);
    break;

  case Bytecodes::_i2d:
    a = pop();
    b = _gvn.transform( new ConvI2DNode(a));
    push_pair(b);
    break;

  case Bytecodes::_iinc:        // Increment local
    i = iter().get_index();     // Get local index
    set_local( i, _gvn.transform( new AddINode( _gvn.intcon(iter().get_iinc_con()), local(i) ) ) );
    break;

  // Exit points of synchronized methods must have an unlock node
  case Bytecodes::_return:
    return_current(NULL);
    break;

  case Bytecodes::_ireturn:
  case Bytecodes::_areturn:
  case Bytecodes::_vreturn:
  case Bytecodes::_freturn:
    return_current(pop());
    break;
  case Bytecodes::_lreturn:
    return_current(pop_pair());
    break;
  case Bytecodes::_dreturn:
    return_current(pop_pair());
    break;

  case Bytecodes::_athrow:
    // null exception oop throws NULL pointer exception
    null_check(peek());
    if (stopped())  return;
    // Hook the thrown exception directly to subsequent handlers.
    if (BailoutToInterpreterForThrows) {
      // Keep method interpreted from now on.
      uncommon_trap(Deoptimization::Reason_unhandled,
                    Deoptimization::Action_make_not_compilable);
      return;
    }
    if (env()->jvmti_can_post_on_exceptions()) {
      // check if we must post exception events, take uncommon trap if so (with must_throw = false)
      uncommon_trap_if_should_post_on_exceptions(Deoptimization::Reason_unhandled, false);
    }
    // Here if either can_post_on_exceptions or should_post_on_exceptions is false
    add_exception_state(make_exception_state(peek()));
    break;

  case Bytecodes::_goto:   // fall through
  case Bytecodes::_goto_w: {
    int target_bci = (bc() == Bytecodes::_goto) ? iter().get_dest() : iter().get_far_dest();

    // If this is a backwards branch in the bytecodes, add Safepoint
    maybe_add_safepoint(target_bci);

    // Update method data
    profile_taken_branch(target_bci);

    // Merge the current control into the target basic block
    merge(target_bci);

    // See if we can get some profile data and hand it off to the next block
    Block *target_block = block()->successor_for_bci(target_bci);
    if (target_block->pred_count() != 1)  break;
    ciMethodData* methodData = method()->method_data();
    if (!methodData->is_mature())  break;
    ciProfileData* data = methodData->bci_to_data(bci());
    assert( data->is_JumpData(), "" );
    int taken = ((ciJumpData*)data)->taken();
    taken = method()->scale_count(taken);
    target_block->set_count(taken);
    break;
  }

  case Bytecodes::_ifnull:    btest = BoolTest::eq; goto handle_if_null;
  case Bytecodes::_ifnonnull: btest = BoolTest::ne; goto handle_if_null;
  handle_if_null:
    // If this is a backwards branch in the bytecodes, add Safepoint
    maybe_add_safepoint(iter().get_dest());
    a = null();
    b = pop();
    if (!_gvn.type(b)->speculative_maybe_null() &&
        !too_many_traps(Deoptimization::Reason_speculate_null_check)) {
      inc_sp(1);
      Node* null_ctl = top();
      b = null_check_oop(b, &null_ctl, true, true, true);
      assert(null_ctl->is_top(), "no null control here");
      dec_sp(1);
    } else if (_gvn.type(b)->speculative_always_null() &&
               !too_many_traps(Deoptimization::Reason_speculate_null_assert)) {
      inc_sp(1);
      b = null_assert(b);
      dec_sp(1);
    }
    c = _gvn.transform( new CmpPNode(b, a) );
    do_ifnull(btest, c);
    break;

  case Bytecodes::_if_acmpeq: btest = BoolTest::eq; goto handle_if_acmp;
  case Bytecodes::_if_acmpne: btest = BoolTest::ne; goto handle_if_acmp;
  handle_if_acmp:
    // If this is a backwards branch in the bytecodes, add Safepoint
    maybe_add_safepoint(iter().get_dest());
    a = pop();
    b = pop();
    c = _gvn.transform( new CmpPNode(b, a) );
    c = optimize_cmp_with_klass(c);
    do_if(btest, c);
    break;

  case Bytecodes::_ifeq: btest = BoolTest::eq; goto handle_ifxx;
  case Bytecodes::_ifne: btest = BoolTest::ne; goto handle_ifxx;
  case Bytecodes::_iflt: btest = BoolTest::lt; goto handle_ifxx;
  case Bytecodes::_ifle: btest = BoolTest::le; goto handle_ifxx;
  case Bytecodes::_ifgt: btest = BoolTest::gt; goto handle_ifxx;
  case Bytecodes::_ifge: btest = BoolTest::ge; goto handle_ifxx;
  handle_ifxx:
    // If this is a backwards branch in the bytecodes, add Safepoint
    maybe_add_safepoint(iter().get_dest());
    a = _gvn.intcon(0);
    b = pop();
    c = _gvn.transform( new CmpINode(b, a) );
    do_if(btest, c);
    break;

  case Bytecodes::_if_icmpeq: btest = BoolTest::eq; goto handle_if_icmp;
  case Bytecodes::_if_icmpne: btest = BoolTest::ne; goto handle_if_icmp;
  case Bytecodes::_if_icmplt: btest = BoolTest::lt; goto handle_if_icmp;
  case Bytecodes::_if_icmple: btest = BoolTest::le; goto handle_if_icmp;
  case Bytecodes::_if_icmpgt: btest = BoolTest::gt; goto handle_if_icmp;
  case Bytecodes::_if_icmpge: btest = BoolTest::ge; goto handle_if_icmp;
  handle_if_icmp:
    // If this is a backwards branch in the bytecodes, add Safepoint
    maybe_add_safepoint(iter().get_dest());
    a = pop();
    b = pop();
    c = _gvn.transform( new CmpINode( b, a ) );
    do_if(btest, c);
    break;

  case Bytecodes::_tableswitch:
    do_tableswitch();
    break;

  case Bytecodes::_lookupswitch:
    do_lookupswitch();
    break;

  case Bytecodes::_invokestatic:
  case Bytecodes::_invokedynamic:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokeinterface:
    do_call();
    break;
  case Bytecodes::_checkcast:
    do_checkcast();
    break;
  case Bytecodes::_instanceof:
    do_instanceof();
    break;
  case Bytecodes::_anewarray:
    do_newarray();
    break;
  case Bytecodes::_newarray:
    do_newarray((BasicType)iter().get_index());
    break;
  case Bytecodes::_multianewarray:
    do_multianewarray();
    break;
  case Bytecodes::_new:
    do_new();
    break;
  case Bytecodes::_vdefault:
    do_vdefault();
    break;
  case Bytecodes::_vwithfield:
    do_vwithfield();
    break;

  case Bytecodes::_jsr:
  case Bytecodes::_jsr_w:
    do_jsr();
    break;

  case Bytecodes::_ret:
    do_ret();
    break;


  case Bytecodes::_monitorenter:
    do_monitor_enter();
    break;

  case Bytecodes::_monitorexit:
    do_monitor_exit();
    break;

  case Bytecodes::_vunbox:
    do_vunbox();
    break;

  case Bytecodes::_vbox:
    do_vbox();
    break;

  case Bytecodes::_breakpoint:
    // Breakpoint set concurrently to compile
    // %%% use an uncommon trap?
    C->record_failure("breakpoint in method");
    return;

  default:
#ifndef PRODUCT
    map()->dump(99);
#endif
    tty->print("\nUnhandled bytecode %s\n", Bytecodes::name(bc()) );
    ShouldNotReachHere();
  }

#ifndef PRODUCT
  IdealGraphPrinter *printer = C->printer();
  if (printer && printer->should_print(1)) {
    char buffer[256];
    sprintf(buffer, "Bytecode %d: %s", bci(), Bytecodes::name(bc()));
    bool old = printer->traverse_outs();
    printer->set_traverse_outs(true);
    printer->print_method(buffer, 4);
    printer->set_traverse_outs(old);
  }
#endif
}
