/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import java.io.*;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;


/**
 * Class for the Html format code generation.
 * Initializes PrintWriter with FileWriter, to enable print
 * related methods to generate the code to the named File through FileWriter.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class HtmlWriter {

    /**
     * The window title of this file.
     */
    protected String winTitle;

    /**
     * The configuration.
     */
    protected HtmlConfiguration configuration;

    private final DocFile docFile;

    protected Content script;


    /**
     * Constructor.
     *
     * @param configuration the configuration
     * @param path the directory path to be created for this file,
     *             or null if none to be created
     */
    public HtmlWriter(HtmlConfiguration configuration, DocPath path) {
        this.configuration = configuration;
        docFile = DocFile.createFileForOutput(configuration, path);

        // The following should be converted to shared Content objects
        // and moved to Contents, but that will require additional
        // changes at the use sites.
        Resources resources = configuration.getResources();
    }

    public void write(Content c) throws DocFileIOException {
        try (Writer writer = docFile.openWriter()) {
            c.write(writer, true);
        } catch (IOException e) {
            throw new DocFileIOException(docFile, DocFileIOException.Mode.WRITE, e);
        }
    }

    /**
     * Returns an HtmlTree for the SCRIPT tag.
     *
     * @return an HtmlTree for the SCRIPT tag
     */
    protected HtmlTree getWinTitleScript(){
        HtmlTree scriptTree = HtmlTree.SCRIPT();
        if(winTitle != null && winTitle.length() > 0) {
            String scriptCode = "<!--\n" +
                    "    try {\n" +
                    "        if (location.href.indexOf('is-external=true') == -1) {\n" +
                    "            parent.document.title=\"" + escapeJavaScriptChars(winTitle) + "\";\n" +
                    "        }\n" +
                    "    }\n" +
                    "    catch(err) {\n" +
                    "    }\n" +
                    "//-->\n";
            RawHtml scriptContent = new RawHtml(scriptCode.replace("\n", DocletConstants.NL));
            scriptTree.addContent(scriptContent);
        }
        return scriptTree;
    }

    /**
     * Returns a String with escaped special JavaScript characters.
     *
     * @param s String that needs to be escaped
     * @return a valid escaped JavaScript string
     */
    private static String escapeJavaScriptChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\'':
                    sb.append("\\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (ch < 32 || ch >= 127) {
                        sb.append(String.format("\\u%04X", (int)ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Returns a content tree for the SCRIPT tag for the main page(index.html).
     *
     * @return a content for the SCRIPT tag
     */
    protected Content getFramesJavaScript() {
        HtmlTree scriptTree = HtmlTree.SCRIPT();
        String scriptCode = "\n" +
                "    tmpTargetPage = \"\" + window.location.search;\n" +
                "    if (tmpTargetPage != \"\" && tmpTargetPage != \"undefined\")\n" +
                "        tmpTargetPage = tmpTargetPage.substring(1);\n" +
                "    if (tmpTargetPage.indexOf(\":\") != -1 || (tmpTargetPage != \"\" && !validURL(tmpTargetPage)))\n" +
                "        tmpTargetPage = \"undefined\";\n" +
                "    targetPage = tmpTargetPage;\n" +
                "    function validURL(url) {\n" +
                "        try {\n" +
                "            url = decodeURIComponent(url);\n" +
                "        }\n" +
                "        catch (error) {\n" +
                "            return false;\n" +
                "        }\n" +
                "        var pos = url.indexOf(\".html\");\n" +
                "        if (pos == -1 || pos != url.length - 5)\n" +
                "            return false;\n" +
                "        var allowNumber = false;\n" +
                "        var allowSep = false;\n" +
                "        var seenDot = false;\n" +
                "        for (var i = 0; i < url.length - 5; i++) {\n" +
                "            var ch = url.charAt(i);\n" +
                "            if ('a' <= ch && ch <= 'z' ||\n" +
                "                    'A' <= ch && ch <= 'Z' ||\n" +
                "                    ch == '$' ||\n" +
                "                    ch == '_' ||\n" +
                "                    ch.charCodeAt(0) > 127) {\n" +
                "                allowNumber = true;\n" +
                "                allowSep = true;\n" +
                "            } else if ('0' <= ch && ch <= '9'\n" +
                "                    || ch == '-') {\n" +
                "                if (!allowNumber)\n" +
                "                     return false;\n" +
                "            } else if (ch == '/' || ch == '.') {\n" +
                "                if (!allowSep)\n" +
                "                    return false;\n" +
                "                allowNumber = false;\n" +
                "                allowSep = false;\n" +
                "                if (ch == '.')\n" +
                "                     seenDot = true;\n" +
                "                if (ch == '/' && seenDot)\n" +
                "                     return false;\n" +
                "            } else {\n" +
                "                return false;\n" +
                "            }\n" +
                "        }\n" +
                "        return true;\n" +
                "    }\n" +
                "    function loadFrames() {\n" +
                "        if (targetPage != \"\" && targetPage != \"undefined\")\n" +
                "             top.classFrame.location = top.targetPage;\n" +
                "    }\n";
        RawHtml scriptContent = new RawHtml(scriptCode.replace("\n", DocletConstants.NL));
        scriptTree.addContent(scriptContent);
        return scriptTree;
    }

    /**
     * Returns an HtmlTree for the BODY tag.
     *
     * @param includeScript  set true if printing windowtitle script
     * @param title title for the window
     * @return an HtmlTree for the BODY tag
     */
    public HtmlTree getBody(boolean includeScript, String title) {
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        // Set window title string which is later printed
        this.winTitle = title;
        // Don't print windowtitle script for overview-frame, allclasses-frame
        // and package-frame
        if (includeScript) {
            this.script = getWinTitleScript();
            body.addContent(script);
            Content noScript = HtmlTree.NOSCRIPT(
                    HtmlTree.DIV(configuration.getContent("doclet.No_Script_Message")));
            body.addContent(noScript);
        }
        return body;
    }

    /**
     * Returns an HtmlTree for the TITLE tag.
     *
     * @return an HtmlTree for the TITLE tag
     */
    public HtmlTree getTitle() {
        HtmlTree title = HtmlTree.TITLE(new StringContent(winTitle));
        return title;
    }
}
