/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 *  The implementation of the javadoc tool, and associated doclets.
 *
 *  <p>Internally, javadoc is composed of two primary parts:
 *  the {@link jdk.javadoc.internal.tool tool}, and a series of
 *  {@link jdk.javadoc.internal.doclets doclets}.
 *
 *  The tool provides a common infrastructure for command-line processing,
 *  and for reading the documentation comments in Java source files,
 *  while doclets provide a user-selectable backend for determining
 *  how to process the documentation comments.
 *
 *  <p><em>Historical Note:</em> Prior to the introduction of the
 *  {@link javax.lang.model Language Model API} in JDK 6, it was
 *  not unusual to use the {@link com.sun.javadoc} API as a
 *  modeling API. But the Language Model API, and associated
 *  {@link javax.annotation.processing Annotation Processing API}
 *  provided a better way to model programs, and in JDK 9,
 *  javadoc itself was converted to using the Language Model API,
 *  with the {@code com.sun.javadoc API} being deprecated for
 *  eventual removal.
 *  </p>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
package jdk.javadoc.internal;
