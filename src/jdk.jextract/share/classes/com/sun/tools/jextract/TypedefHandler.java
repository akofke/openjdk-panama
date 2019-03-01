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
package com.sun.tools.jextract;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.sun.tools.jextract.parser.Parser;
import com.sun.tools.jextract.tree.HeaderTree;
import com.sun.tools.jextract.tree.SimpleTreeVisitor;
import com.sun.tools.jextract.tree.StructTree;
import com.sun.tools.jextract.tree.Tree;
import com.sun.tools.jextract.tree.TreeMaker;
import com.sun.tools.jextract.tree.TreePhase;
import com.sun.tools.jextract.tree.TreePrinter;
import com.sun.tools.jextract.tree.TypedefTree;
import jdk.internal.clang.Cursor;

/**
 * This visitor handles certain typedef declarations.
 *
 * 1. Remove redundant typedefs.
 * 2. Rename typedef'ed anonymous type definitions like
 *        typedef struct { int x; int y; } Point;
 */
final class TypedefHandler extends SimpleTreeVisitor<Void, Void>
        implements TreePhase {
    private final TreeMaker treeMaker = new TreeMaker();

    // Potential Tree instances that will go into transformed HeaderTree
    // are collected in this list.
    private List<Tree> decls = new ArrayList<>();

    // Tree instances that are to be replaced from "decls" list are
    // saved in the following Map.
    private final Map<Cursor, Tree> replacements = new HashMap<>();

    private final Log log;

    public TypedefHandler(Context ctx) {
        this.log = ctx.log;
    }

    @Override
    public HeaderTree transform(HeaderTree ht) {
        // Process all header declarations are collect potential
        // declarations that will go into transformed HeaderTree
        // into the this.decls field.
        ht.accept(this, null);

        // Replace trees from this.decls with Trees found in this.replacements.
        // We need this two step process so that named StructTree instances
        // will replace with original unnamed StructTree instances.
        List<Tree> newDecls = decls.stream().map(tx -> {
            if (replacements.containsKey(tx.cursor())) {
                return replacements.get(tx.cursor());
            } else {
                return tx;
            }
        }).collect(Collectors.toList());

        return treeMaker.createHeader(ht.cursor(), ht.path(), newDecls);
    }

    @Override
    public Void defaultAction(Tree tree, Void v) {
        decls.add(tree);
        return null;
    }

    @Override
    public Void visitHeader(HeaderTree ht, Void v) {
        ht.declarations().forEach(decl -> decl.accept(this, null));
        return null;
    }

    @Override
    public Void visitTypedef(TypedefTree tt, Void v) {
        Optional<Tree> def = tt.typeDefinition();
        if (def.isPresent()) {
            Tree defTree = def.get();
            if (defTree instanceof StructTree && defTree.name().isEmpty()) {
                /*
                 * typedef struct { int x; int y; } Point
                 *
                 * is mapped to two Cursors by clang. First one for anonymous struct decl.
                 * and second one for typedef decl. We map it as a single named struct
                 * declaration.
                 */
                replacements.put(defTree.cursor(), ((StructTree)defTree).withName(tt.name()));
                log.print(Level.FINE, () -> "Typedef " + defTree.type().spelling() + " as " + tt.name());
                return null;
            } else if (defTree.name().equals(tt.name())) {
                /*
                 * Remove redundant typedef like:
                 *
                 * typedef struct Point { int x; int y; } Point
                 * typedef enum Color { R, G, B} Color
                 * typedef struct Undef Undef
                 */
                return null;
            }
        }

        decls.add(tt);
        return null;
    }

    // test main to manually check this visitor
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Expected a header file");
            return;
        }

        Context context = Context.createDefault();
        Parser p = new Parser(context, true);
        Path builtinInc = Paths.get(System.getProperty("java.home"), "conf", "jextract");
        List<String> clangArgs = List.of("-I" + builtinInc);
        HeaderTree header = p.parse(Paths.get(args[0]), clangArgs);
        TreePrinter printer = new TreePrinter();
        TypedefHandler handler = new TypedefHandler(context);
        handler.transform(header).accept(printer, null);
    }
}
