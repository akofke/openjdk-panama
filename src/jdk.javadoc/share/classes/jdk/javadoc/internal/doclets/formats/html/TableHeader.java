/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;

/**
 * A row header for an HTML table.
 *
 * The header contains a list of {@code <th>} cells, providing the column headers.
 * The attribute {@code scope="col"} is automatically added to each header cell.
 * In addition, a series of style class names can be specified, to be applied one per cell.
 *
 */
public class TableHeader {

    /**
     * The content to be put in each of the {@code <th>} cells in the header row.
     */
    private final List<Content> cellContents;
    /**
     * The style class names for each of the {@code <th>} cells in the header row.
     * If not set, default style names will be used.
     */
    private List<HtmlStyle> styles;

    /**
     * Creates a header row, with localized content for each cell.
     * Resources keys will be converted to content using {@link Contents#getContent(String)}.
     * @param contents a factory to get the content for each header cell.
     * @param colHeaderKeys the resource keys for the content in each cell.
     */
    TableHeader(Contents contents, String... colHeaderKeys) {
        this.cellContents = Arrays.stream(colHeaderKeys)
                .map((key) -> contents.getContent(key))
                .collect(Collectors.toList());
    }

    /**
     * Creates a header row, with specified content for each cell.
     * @param headerCellContents a content object for each header cell
     */
    TableHeader(Content... headerCellContents) {
        this.cellContents = Arrays.asList(headerCellContents);
    }

    /**
     * Set the style class names for each header cell.
     * The number of names must match the number of cells given to the constructor.
     * @param styles the style class names
     * @return this object
     */
    TableHeader styles(HtmlStyle... styles) {
        if (styles.length != cellContents.size()) {
            throw new IllegalStateException();
        }
        this.styles = Arrays.asList(styles);
        return this;
    }

    /**
     * Converts this header to a {@link Content} object, for use in an {@link HtmlTree}.
     * @returns a Content object
     */
    Content toContent() {
        String scope = "col";
        Content tr = new HtmlTree(HtmlTag.TR);
        int i = 0;
        for (Content cellContent : cellContents) {
            HtmlStyle style = (styles != null) ? styles.get(i)
                    : (i == 0) ? HtmlStyle.colFirst
                    : (i == (cellContents.size() - 1)) ? HtmlStyle.colLast
                    : (i == 1) ? HtmlStyle.colSecond : null;
            Content cell = (style == null) ? HtmlTree.TH(scope, cellContent)
                    : HtmlTree.TH(style, scope, cellContent);
            tr.addContent(cell);
            i++;
        }
        return tr;
    }

}
