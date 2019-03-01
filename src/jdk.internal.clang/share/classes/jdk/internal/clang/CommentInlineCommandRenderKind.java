/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public enum CommentInlineCommandRenderKind {
    Normal(0),
    Bold(1),
    Monospaced(2),
    Emphasized(3);

    private final int value;

    CommentInlineCommandRenderKind(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    private final static Map<Integer, CommentInlineCommandRenderKind> lookup;

    static {
        lookup = new HashMap<>();
        for (CommentInlineCommandRenderKind e: CommentInlineCommandRenderKind.values()) {
            lookup.put(e.value(), e);
        }
    }

    public final static CommentInlineCommandRenderKind valueOf(int value) {
        CommentInlineCommandRenderKind x = lookup.get(value);
        if (null == x) {
            throw new NoSuchElementException("kind = " + value);
        }
        return x;
    }
}
