#
# Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

################################################################################
# Setup libclang from llvm project
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_LIBCLANG],
[
  AC_ARG_WITH([libclang], [AS_HELP_STRING([--with-libclang=<path to llvm>],
        [Specify path of llvm installation contains libclang. Pre-built llvm
         binary can be downloaded from http://llvm.org/releases/download.html])],
    [],
    [with_libclang=yes]
  )

  if test "x$with_libclang" = "xno"; then
    ENABLE_LIBCLANG="no"
  else
    ENABLE_LIBCLANG="yes"
    AC_ARG_WITH([libclang-include], [AS_HELP_STRING([--with-libclang-include=<path>],
        [Specify where to find libclang header files, clang-c/Index.h ])],
      [LIBCLANG_CPPFLAGS="-I$withval"],
      [LIBCLANG_CPPFLAGS=""]
    )
    AC_ARG_WITH([libclang-lib], [AS_HELP_STRING([--with-libclang-lib=<path>],
        [Specify where to find libclang binary, so/dylib/dll ])],
      [LIBCLANG_LDFLAGS="-L$withval"],
      [LIBCLANG_LDFLAGS=""]
    )

    if test "x$with_libclang" != "xyes"; then
      LIBCLANG_CPPFLAGS="-I$with_libclang/include"
      LIBCLANG_LDFLAGS="-L$with_libclang/lib"
    fi

    OLD_CPPFLAGS=$CPPFLAGS
    OLD_LDFLAGS=$LDFLAGS
    OLD_LIBS=$LIBS

    CPPFLAGS="$LIBCLANG_CPPFLAGS"
    LDFLAGS="$LIBCLANG_LDFLAGS"
    LIBS=""
    AC_CHECK_HEADER("clang-c/Index.h", [], [ENABLE_LIBCLANG="no"])
    if test "x$ENABLE_LIBCLANG" = "xyes"; then
      AC_CHECK_LIB(clang, clang_getClangVersion, [], [ENABLE_LIBCLANG="no"])
    fi

    if test "x$ENABLE_LIBCLANG" = "xno"; then
      AC_MSG_NOTICE([Cannot locate libclang! You can download pre-built llvm
        binary from http://llvm.org/releases/download.html, then specify the
        location using --with-libclang])
    fi

    LIBCLANG_LIBS="$LIBS"

    LIBS="$OLD_LIBS"
    LDFLAGS="$OLD_LDFLAGS"
    CPPFLAGS="$OLD_CPPFLAGS"
  fi

  if test "x$ENABLE_LIBCLANG" = "xno"; then
    LIBCLANG_CPPFLAGS=""
    LIBCLANG_LDFLAGS=""
    LIBCLANG_LIBS=""
  fi

  AC_SUBST(ENABLE_LIBCLANG)
  AC_SUBST(LIBCLANG_CPPFLAGS)
  AC_SUBST(LIBCLANG_LDFLAGS)
  AC_SUBST(LIBCLANG_LIBS)
])
