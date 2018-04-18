/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * <h2>HTTP Client and WebSocket APIs</h2>
 *
 * <p> Provides high-level client interfaces to HTTP (versions 1.1 and 2) and
 * low-level client interfaces to WebSocket. The main types defined are:
 *
 * <ul>
 *    <li>{@link java.net.http.HttpClient}</li>
 *    <li>{@link java.net.http.HttpRequest}</li>
 *    <li>{@link java.net.http.HttpResponse}</li>
 *    <li>{@link java.net.http.WebSocket}</li>
 * </ul>
 *
 * <p> The protocol-specific requirements are defined in the
 * <a href="https://tools.ietf.org/html/rfc7540">Hypertext Transfer Protocol
 * Version 2 (HTTP/2)</a>, the <a href="https://tools.ietf.org/html/rfc2616">
 * Hypertext Transfer Protocol (HTTP/1.1)</a>, and
 * <a href="https://tools.ietf.org/html/rfc6455">The WebSocket Protocol</a>.
 *
 * <p> Asynchronous tasks and dependent actions of returned {@link
 * java.util.concurrent.CompletableFuture} instances are executed on the threads
 * supplied by the client's {@link java.util.concurrent.Executor}, where
 * practical.
 *
 * <p> {@code CompletableFuture}s returned by this API will throw {@link
 * java.lang.UnsupportedOperationException} for their {@link
 * java.util.concurrent.CompletableFuture#obtrudeValue(Object) obtrudeValue}
 * and {@link java.util.concurrent.CompletableFuture#obtrudeException(Throwable)
 * obtrudeException} methods. Invoking the {@link
 * java.util.concurrent.CompletableFuture#cancel cancel} method on a {@code
 * CompletableFuture} returned by this API will not interrupt the underlying
 * operation, but may be useful to complete, exceptionally, dependent stages
 * that have not already completed.
 *
 * <p> Unless otherwise stated, {@code null} parameter values will cause methods
 * of all classes in this package to throw {@code NullPointerException}.
 *
 * @since 11
 */
package java.net.http;
