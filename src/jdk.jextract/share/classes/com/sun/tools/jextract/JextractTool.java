/*
 *  Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

package com.sun.tools.jextract;

import com.sun.tools.jextract.parser.Parser;
import com.sun.tools.jextract.tree.FlexibleArrayWarningVisitor;
import com.sun.tools.jextract.tree.Tree;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JextractTool {
    private final HeaderResolver headerResolver;
    private final SymbolFilter symbolFilter;
    private final Parser parser;
    private final Function<HeaderFile, AsmCodeFactory> codeFactory;
    private final Collection<String> clangArgs;
    private final Collection<Path> sources;
    private final Context ctx;

    public JextractTool(Context ctx) {
        this.headerResolver = new HeaderResolver(ctx);
        this.parser = new Parser(ctx, Options.INCLUDE_MACROS);
        this.symbolFilter = new SymbolFilter(ctx);
        this.codeFactory = ctx.options.genStaticForwarder ?
                hf -> new AsmCodeFactoryExt(ctx, hf) :
                hf -> new AsmCodeFactory(ctx, hf);
        this.clangArgs = ctx.options.clangArgs;
        this.sources = ctx.sources;
        this.ctx = ctx;
        assert sources.size() > 0;
    }

    /** This is the main jextract entry point */
    public Writer processHeaders() {
        Path source = sources.size() > 1? generateTmpSource() : sources.iterator().next();
        Map<HeaderFile, List<Tree>> headerMap = Stream.of(parser.parse(source, clangArgs))
                .map(symbolFilter)
                .map(new TypedefHandler(ctx))
                .map(new EmptyNameHandler())
                .map(new DuplicateDeclarationHandler())
                .flatMap(h -> h.declarations().stream())
                .peek(new FlexibleArrayWarningVisitor(ctx))
                .collect(Collectors.groupingBy(this::headerFromDecl));

        //generate classes
        Map<String, byte[]> results = new LinkedHashMap<>();
        headerMap.forEach((hf, decls) -> generateHeader(hf, decls, results));
        return new Writer(ctx, results);
    }

    private void generateHeader(HeaderFile hf, List<Tree> decls, Map<String, byte[]> results) {
        TypeEnter enter = new TypeEnter(hf.dictionary());
        decls.forEach(t -> t.accept(enter, null));
        AsmCodeFactory cf = codeFactory.apply(hf);
        results.putAll(cf.generateNativeHeader(decls));
    }

    private HeaderFile headerFromDecl(Tree tree) {
        Path path = tree.cursor().getSourceLocation().getFileLocation().path();
        return headerResolver.headerFor(path);
    }

    private Path generateTmpSource() {
        assert sources.size() > 1;
        try {
            Path tmpFile = Files.createTempFile("jextract", ".h");
            tmpFile.toFile().deleteOnExit();
            Files.write(tmpFile, sources.stream().
                map(src -> "#include \"" + src + "\"").
                collect(Collectors.toList()));
            return tmpFile;
        } catch (IOException ioExp) {
            throw new UncheckedIOException(ioExp);
        }
    }
}
