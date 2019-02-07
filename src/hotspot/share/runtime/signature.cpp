/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/valueKlass.hpp"
#include "runtime/signature.hpp"

// Implementation of SignatureIterator

// Signature syntax:
//
// Signature  = "(" {Parameter} ")" ReturnType.
// Parameter  = FieldType.
// ReturnType = FieldType | "V".
// FieldType  = "B" | "C" | "D" | "F" | "I" | "J" | "S" | "Z" | "L" ClassName ";" | "Q" ValueClassName ";" | "[" FieldType.
// ClassName  = string.


SignatureIterator::SignatureIterator(Symbol* signature) {
  _signature       = signature;
  _parameter_index = 0;
}

void SignatureIterator::expect(char c) {
  if (_signature->char_at(_index) != c) fatal("expecting %c", c);
  _index++;
}

int SignatureIterator::parse_type() {
  // Note: This function could be simplified by using "return T_XXX_size;"
  //       instead of the assignment and the break statements. However, it
  //       seems that the product build for win32_i486 with MS VC++ 6.0 doesn't
  //       work (stack underflow for some tests) - this seems to be a VC++ 6.0
  //       compiler bug (was problem - gri 4/27/2000).
  int size = -1;
  switch(_signature->char_at(_index)) {
    case 'B': do_byte  (); if (_parameter_index < 0 ) _return_type = T_BYTE;
              _index++; size = T_BYTE_size   ; break;
    case 'C': do_char  (); if (_parameter_index < 0 ) _return_type = T_CHAR;
              _index++; size = T_CHAR_size   ; break;
    case 'D': do_double(); if (_parameter_index < 0 ) _return_type = T_DOUBLE;
              _index++; size = T_DOUBLE_size ; break;
    case 'F': do_float (); if (_parameter_index < 0 ) _return_type = T_FLOAT;
              _index++; size = T_FLOAT_size  ; break;
    case 'I': do_int   (); if (_parameter_index < 0 ) _return_type = T_INT;
              _index++; size = T_INT_size    ; break;
    case 'J': do_long  (); if (_parameter_index < 0 ) _return_type = T_LONG;
              _index++; size = T_LONG_size   ; break;
    case 'S': do_short (); if (_parameter_index < 0 ) _return_type = T_SHORT;
              _index++; size = T_SHORT_size  ; break;
    case 'Z': do_bool  (); if (_parameter_index < 0 ) _return_type = T_BOOLEAN;
              _index++; size = T_BOOLEAN_size; break;
    case 'V': do_void  (); if (_parameter_index < 0 ) _return_type = T_VOID;
              _index++; size = T_VOID_size;  ; break;
    case 'L':
      { int begin = ++_index;
        Symbol* sig = _signature;
        while (sig->char_at(_index++) != ';') ;
        do_object(begin, _index);
      }
      if (_parameter_index < 0 ) _return_type = T_OBJECT;
      size = T_OBJECT_size;
      break;
    case 'Q':
      { int begin = ++_index;
      Symbol* sig = _signature;
      while (sig->char_at(_index++) != ';') ;
      do_valuetype(begin, _index);
      }
      if (_parameter_index < 0 ) _return_type = T_VALUETYPE;
      size = T_VALUETYPE_size;
      break;
    case '[':
      { int begin = ++_index;
        Symbol* sig = _signature;
        while (sig->char_at(_index) == '[') {
          _index++;
        }
        if (sig->char_at(_index) == 'L' || sig->char_at(_index) == 'Q') {
          while (sig->char_at(_index++) != ';') ;
        } else {
          _index++;
        }
        do_array(begin, _index);
       if (_parameter_index < 0 ) _return_type = T_ARRAY;
      }
      size = T_ARRAY_size;
      break;
    default:
      ShouldNotReachHere();
      break;
  }
  assert(size >= 0, "size must be set");
  return size;
}


void SignatureIterator::check_signature_end() {
  if (_index < _signature->utf8_length()) {
    tty->print_cr("too many chars in signature");
    _signature->print_value_on(tty);
    tty->print_cr(" @ %d", _index);
  }
}


void SignatureIterator::dispatch_field() {
  // no '(', just one (field) type
  _index = 0;
  _parameter_index = 0;
  parse_type();
  check_signature_end();
}


void SignatureIterator::iterate_parameters() {
  // Parse parameters
  _index = 0;
  _parameter_index = 0;
  expect('(');
  while (_signature->char_at(_index) != ')') _parameter_index += parse_type();
  expect(')');
  _parameter_index = 0;
}

// Optimized version of iterate_parameters when fingerprint is known
void SignatureIterator::iterate_parameters( uint64_t fingerprint ) {
  uint64_t saved_fingerprint = fingerprint;

  // Check for too many arguments
  if (fingerprint == (uint64_t)CONST64(-1)) {
    SignatureIterator::iterate_parameters();
    return;
  }

  assert(fingerprint, "Fingerprint should not be 0");

  _parameter_index = 0;
  fingerprint = fingerprint >> (static_feature_size + result_feature_size);
  while ( 1 ) {
    switch ( fingerprint & parameter_feature_mask ) {
      case bool_parm:
        do_bool();
        _parameter_index += T_BOOLEAN_size;
        break;
      case byte_parm:
        do_byte();
        _parameter_index += T_BYTE_size;
        break;
      case char_parm:
        do_char();
        _parameter_index += T_CHAR_size;
        break;
      case short_parm:
        do_short();
        _parameter_index += T_SHORT_size;
        break;
      case int_parm:
        do_int();
        _parameter_index += T_INT_size;
        break;
      case obj_parm:
        do_object(0, 0);
        _parameter_index += T_OBJECT_size;
        break;
      case long_parm:
        do_long();
        _parameter_index += T_LONG_size;
        break;
      case float_parm:
        do_float();
        _parameter_index += T_FLOAT_size;
        break;
      case double_parm:
        do_double();
        _parameter_index += T_DOUBLE_size;
        break;
      case done_parm:
        return;
        break;
      default:
        tty->print_cr("*** parameter is " UINT64_FORMAT, fingerprint & parameter_feature_mask);
        tty->print_cr("*** fingerprint is " PTR64_FORMAT, saved_fingerprint);
        ShouldNotReachHere();
        break;
    }
    fingerprint >>= parameter_feature_size;
  }
  _parameter_index = 0;
}


void SignatureIterator::iterate_returntype() {
  // Ignore parameters
  _index = 0;
  expect('(');
  Symbol* sig = _signature;
  // Need to skip over each type in the signature's argument list until a
  // closing ')' is found., then get the return type.  We cannot just scan
  // for the first ')' because ')' is a legal character in a type name.
  while (sig->char_at(_index) != ')') {
    switch(sig->char_at(_index)) {
      case 'B':
      case 'C':
      case 'D':
      case 'F':
      case 'I':
      case 'J':
      case 'S':
      case 'Z':
      case 'V':
        {
          _index++;
        }
        break;
      case 'Q':
      case 'L':
        {
          while (sig->char_at(_index++) != ';') ;
        }
        break;
      case '[':
        {
          int begin = ++_index;
          while (sig->char_at(_index) == '[') {
            _index++;
          }
          if (sig->char_at(_index) == 'L' || sig->char_at(_index) == 'Q' ) {
            while (sig->char_at(_index++) != ';') ;
          } else {
            _index++;
          }
        }
        break;
      default:
        ShouldNotReachHere();
        break;
    }
  }
  expect(')');
  // Parse return type
  _parameter_index = -1;
  parse_type();
  check_signature_end();
  _parameter_index = 0;
}


void SignatureIterator::iterate() {
  // Parse parameters
  _parameter_index = 0;
  _index = 0;
  expect('(');
  while (_signature->char_at(_index) != ')') _parameter_index += parse_type();
  expect(')');
  // Parse return type
  _parameter_index = -1;
  parse_type();
  check_signature_end();
  _parameter_index = 0;
}


// Implementation of SignatureStream
SignatureStream::SignatureStream(Symbol* signature, bool is_method) :
                   _signature(signature), _at_return_type(false) {
  _begin = _end = (is_method ? 1 : 0);  // skip first '(' in method signatures
  _names = new GrowableArray<Symbol*>(10);
  next();
}

SignatureStream::~SignatureStream() {
  // decrement refcount for names created during signature parsing
  for (int i = 0; i < _names->length(); i++) {
    _names->at(i)->decrement_refcount();
  }
}

bool SignatureStream::is_done() const {
  return _end > _signature->utf8_length();
}


void SignatureStream::next_non_primitive(int t) {
  switch (t) {
    case 'L': {
      _type = T_OBJECT;
      Symbol* sig = _signature;
      while (sig->char_at(_end++) != ';');
      break;
    }
    case 'Q': {
      _type = T_VALUETYPE;
      Symbol* sig = _signature;
      while (sig->char_at(_end++) != ';');
      break;
    }
    case '[': {
      _type = T_ARRAY;
      Symbol* sig = _signature;
      char c = sig->char_at(_end);
      while ('0' <= c && c <= '9') c = sig->char_at(_end++);
      while (sig->char_at(_end) == '[') {
        _end++;
        c = sig->char_at(_end);
        while ('0' <= c && c <= '9') c = sig->char_at(_end++);
      }
      switch(sig->char_at(_end)) {
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'S':
        case 'Z':_end++; break;
        default: {
          while (sig->char_at(_end++) != ';');
          break;
        }
      }
      break;
    }
    case ')': _end++; next(); _at_return_type = true; break;
    default : ShouldNotReachHere();
  }
}


bool SignatureStream::is_object() const {
  return _type == T_OBJECT
      || _type == T_ARRAY
      || _type == T_VALUETYPE;
}

bool SignatureStream::is_array() const {
  return _type == T_ARRAY;
}

Symbol* SignatureStream::as_symbol(TRAPS) {
  // Create a symbol from for string _begin _end
  int begin = _begin;
  int end   = _end;

  if (_type == T_OBJECT || _type == T_VALUETYPE) {
    begin++;
    end--;
    if (begin == end) {
      return vmSymbols::java_lang_Object();
    }
  }

  // Save names for cleaning up reference count at the end of
  // SignatureStream scope.
  Symbol* name = SymbolTable::new_symbol(_signature, begin, end, CHECK_NULL);
  _names->push(name);  // save new symbol for decrementing later
  return name;
}

Klass* SignatureStream::as_klass(Handle class_loader, Handle protection_domain,
                                   FailureMode failure_mode, TRAPS) {
  if (!is_object())  return NULL;
  Symbol* name = as_symbol(CHECK_NULL);
  if (failure_mode == ReturnNull) {
    return SystemDictionary::resolve_or_null(name, class_loader, protection_domain, THREAD);
  } else {
    bool throw_error = (failure_mode == NCDFError);
    return SystemDictionary::resolve_or_fail(name, class_loader, protection_domain, throw_error, THREAD);
  }
}

oop SignatureStream::as_java_mirror(Handle class_loader, Handle protection_domain,
                                    FailureMode failure_mode, TRAPS) {
  if (!is_object())
    return Universe::java_mirror(type());
  Klass* klass = as_klass(class_loader, protection_domain, failure_mode, CHECK_NULL);
  if (klass == NULL)  return NULL;
  return _type == T_VALUETYPE ? ValueKlass::cast(InstanceKlass::cast(klass))->value_mirror() : klass->java_mirror();
}

Symbol* SignatureStream::as_symbol_or_null() {
  // Create a symbol from for string _begin _end
  ResourceMark rm;

  int begin = _begin;
  int end   = _end;

  if (_type == T_OBJECT || _type == T_VALUETYPE) {
    begin++;
    end--;
    if (begin == end) {
      return vmSymbols::java_lang_Object();
    }
  }

  char* buffer = NEW_RESOURCE_ARRAY(char, end - begin);
  for (int index = begin; index < end; index++) {
    buffer[index - begin] = _signature->char_at(index);
  }
  Symbol* result = SymbolTable::probe(buffer, end - begin);
  return result;
}

int SignatureStream::reference_parameter_count() {
  int args_count = 0;
  for ( ; !at_return_type(); next()) {
    if (is_object()) {
      args_count++;
    }
  }
  return args_count;
}

bool SignatureVerifier::is_valid_signature(Symbol* sig) {
  const char* signature = (const char*)sig->bytes();
  ssize_t len = sig->utf8_length();
  if (signature == NULL || signature[0] == '\0' || len < 1) {
    return false;
  } else if (signature[0] == '(') {
    return is_valid_method_signature(sig);
  } else {
    return is_valid_type_signature(sig);
  }
}

bool SignatureVerifier::is_valid_method_signature(Symbol* sig) {
  const char* method_sig = (const char*)sig->bytes();
  ssize_t len = sig->utf8_length();
  ssize_t index = 0;
  if (method_sig != NULL && len > 1 && method_sig[index] == '(') {
    ++index;
    while (index < len && method_sig[index] != ')') {
      ssize_t res = is_valid_type(&method_sig[index], len - index);
      if (res == -1) {
        return false;
      } else {
        index += res;
      }
    }
    if (index < len && method_sig[index] == ')') {
      // check the return type
      ++index;
      return (is_valid_type(&method_sig[index], len - index) == (len - index));
    }
  }
  return false;
}

bool SignatureVerifier::is_valid_type_signature(Symbol* sig) {
  const char* type_sig = (const char*)sig->bytes();
  ssize_t len = sig->utf8_length();
  return (type_sig != NULL && len >= 1 &&
          (is_valid_type(type_sig, len) == len));
}

// Checks to see if the type (not to go beyond 'limit') refers to a valid type.
// Returns -1 if it is not, or the index of the next character that is not part
// of the type.  The type encoding may end before 'limit' and that's ok.
ssize_t SignatureVerifier::is_valid_type(const char* type, ssize_t limit) {
  ssize_t index = 0;

  // Iterate over any number of array dimensions
  while (index < limit && type[index] == '[') ++index;
  if (index >= limit) {
    return -1;
  }
  switch (type[index]) {
    case 'B': case 'C': case 'D': case 'F': case 'I':
    case 'J': case 'S': case 'Z': case 'V':
      return index + 1;
    case 'Q': // fall through
    case 'L':
      for (index = index + 1; index < limit; ++index) {
        char c = type[index];
        if (c == ';') {
          return index + 1;
        }
        if (invalid_name_char(c)) {
          return -1;
        }
      }
      // fall through
    default: ; // fall through
  }
  return -1;
}

bool SignatureVerifier::invalid_name_char(char c) {
  switch (c) {
    case '\0': case '.': case ';': case '[':
      return true;
    default:
      return false;
  }
}

// Adds an argument to the signature
void SigEntry::add_entry(GrowableArray<SigEntry>* sig, BasicType bt, int offset) {
  sig->append(SigEntry(bt, offset));
  if (bt == T_LONG || bt == T_DOUBLE) {
    sig->append(SigEntry(T_VOID, offset)); // Longs and doubles take two stack slots
  }
}

// Inserts a reserved argument at position 'i'
void SigEntry::insert_reserved_entry(GrowableArray<SigEntry>* sig, int i, BasicType bt) {
  if (bt == T_OBJECT || bt == T_ARRAY || bt == T_VALUETYPE) {
    // Treat this as INT to not confuse the GC
    bt = T_INT;
  } else if (bt == T_LONG || bt == T_DOUBLE) {
    // Longs and doubles take two stack slots
    sig->insert_before(i, SigEntry(T_VOID, SigEntry::ReservedOffset));
  }
  sig->insert_before(i, SigEntry(bt, SigEntry::ReservedOffset));
}

// Returns true if the argument at index 'i' is a reserved argument
bool SigEntry::is_reserved_entry(const GrowableArray<SigEntry>* sig, int i) {
  return sig->at(i)._offset == SigEntry::ReservedOffset;
}

// Returns true if the argument at index 'i' is not a value type delimiter
bool SigEntry::skip_value_delimiters(const GrowableArray<SigEntry>* sig, int i) {
  return (sig->at(i)._bt != T_VALUETYPE &&
          (sig->at(i)._bt != T_VOID || sig->at(i-1)._bt == T_LONG || sig->at(i-1)._bt == T_DOUBLE));
}

// Fill basic type array from signature array
int SigEntry::fill_sig_bt(const GrowableArray<SigEntry>* sig, BasicType* sig_bt) {
  int count = 0;
  for (int i = 0; i < sig->length(); i++) {
    if (skip_value_delimiters(sig, i)) {
      sig_bt[count++] = sig->at(i)._bt;
    }
  }
  return count;
}

// Create a temporary symbol from the signature array
TempNewSymbol SigEntry::create_symbol(const GrowableArray<SigEntry>* sig) {
  ResourceMark rm;
  int length = sig->length();
  char* sig_str = NEW_RESOURCE_ARRAY(char, 2*length + 3);
  int idx = 0;
  sig_str[idx++] = '(';
  for (int i = 0; i < length; i++) {
    BasicType bt = sig->at(i)._bt;
    if (bt == T_VALUETYPE || bt == T_VOID) {
      // Ignore
    } else {
      if (bt == T_ARRAY) {
        bt = T_OBJECT; // We don't know the element type, treat as Object
      }
      sig_str[idx++] = type2char(bt);
      if (bt == T_OBJECT) {
        sig_str[idx++] = ';';
      }
    }
  }
  sig_str[idx++] = ')';
  sig_str[idx++] = '\0';
  return SymbolTable::new_symbol(sig_str, Thread::current());
}

// Increment signature iterator (skips value type delimiters and T_VOID) and check if next entry is reserved
bool SigEntry::next_is_reserved(ExtendedSignature& sig, BasicType& bt, bool can_be_void) {
  assert(can_be_void || bt != T_VOID, "should never see void");
  if (sig.at_end() || (can_be_void && type2size[bt] == 2 && (*sig)._offset != SigEntry::ReservedOffset)) {
    // Don't increment at the end or at a T_LONG/T_DOUBLE which will be followed by a (skipped) T_VOID
    return false;
  }
  assert(bt == T_VOID || type2wfield[bt] == type2wfield[(*sig)._bt], "inconsistent signature");
  ++sig;
  if (!sig.at_end() && (*sig)._offset == SigEntry::ReservedOffset) {
    bt = (*sig)._bt;
    return true;
  }
  return false;
}
