/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.*;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Generate the module index page "overview-summary.html" for the right-hand
 * frame.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleIndexWriter extends AbstractModuleIndexWriter {

    /**
     * HTML tree for main tag.
     */
    private final HtmlTree htmlTree = HtmlTree.MAIN();

    /**
     * Construct the ModuleIndexWriter.
     * @param configuration the configuration object
     * @param filename the name of the generated file
     */
    public ModuleIndexWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
    }

    /**
     * Generate the module index page for the right-hand frame.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocFileIOException if there is a problem generating the module index page
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        DocPath filename = DocPaths.overviewSummary(configuration.frames);
        ModuleIndexWriter mdlgen = new ModuleIndexWriter(configuration, filename);
        mdlgen.buildModuleIndexFile("doclet.Window_Overview_Summary", true);
    }

    /**
     * Add the module index.
     *
     * @param body the documentation tree to which the index will be added
     */
    @Override
    protected void addIndex(Content body) {
        addIndexContents(body);
    }

    /**
     * Adds module index contents.
     *
     * @param body the document tree to which the index contents will be added
     */
    protected void addIndexContents(Content body) {
        HtmlTree htmltree = (configuration.allowTag(HtmlTag.NAV))
                ? HtmlTree.NAV()
                : new HtmlTree(HtmlTag.DIV);
        htmltree.setStyle(HtmlStyle.indexNav);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        addAllClassesLink(ul);
        if (configuration.showModules) {
            addAllModulesLink(ul);
        }
        htmltree.addContent(ul);
        body.addContent(htmltree);
        addModulesList(body);
    }

    /**
     * Add the list of modules.
     *
     * @param body the content tree to which the module list will be added
     */
    @Override
    protected void addModulesList(Content body) {
        Map<String, SortedSet<ModuleElement>> groupModuleMap
                = configuration.group.groupModules(configuration.modules);

        if (!groupModuleMap.keySet().isEmpty()) {
            String tableSummary = configuration.getText("doclet.Member_Table_Summary",
                    configuration.getText("doclet.Module_Summary"), configuration.getText("doclet.modules"));
            TableHeader header = new TableHeader(contents.moduleLabel, contents.descriptionLabel);
            Table table =  new Table(configuration.htmlVersion, HtmlStyle.overviewSummary)
                    .setSummary(tableSummary)
                    .setHeader(header)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast)
                    .setDefaultTab(resources.getText("doclet.All_Modules"))
                    .setTabScriptVariable("groups")
                    .setTabScript(i -> "showGroups(" + i + ");")
                    .setTabId(i -> (i == 0) ? "t0" : ("t" + (1 << (i - 1))));

            // add the tabs in command-line order
            for (String groupName : configuration.group.getGroupList()) {
                Set<ModuleElement> groupModules = groupModuleMap.get(groupName);
                if (groupModules != null) {
                    table.addTab(groupName, groupModules::contains);
                }
            }

            for (ModuleElement mdle : configuration.modules) {
                if (!mdle.isUnnamed()) {
                    if (!(configuration.nodeprecated && utils.isDeprecated(mdle))) {
                        Content moduleLinkContent = getModuleLink(mdle, new StringContent(mdle.getQualifiedName().toString()));
                        Content summaryContent = new ContentBuilder();
                        addSummaryComment(mdle, summaryContent);
                        table.addRow(mdle, moduleLinkContent, summaryContent);
                    }
                }
            }

            Content div = HtmlTree.DIV(HtmlStyle.contentContainer, table.toContent());
            if (configuration.allowTag(HtmlTag.MAIN)) {
                htmlTree.addContent(div);
            } else {
                body.addContent(div);
            }

            if (table.needsScript()) {
                getScript().addContent(table.getScript());
            }
        }
    }

    /**
     * Adds the overview summary comment for this documentation. Add one line
     * summary at the top of the page and generate a link to the description,
     * which is added at the end of this page.
     *
     * @param body the documentation tree to which the overview header will be added
     */
    @Override
    protected void addOverviewHeader(Content body) {
        addConfigurationTitle(body);
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.setStyle(HtmlStyle.contentContainer);
            addOverviewComment(div);
            if (configuration.allowTag(HtmlTag.MAIN)) {
                htmlTree.addContent(div);
            } else {
                body.addContent(div);
            }
        }
    }

    /**
     * Adds the overview comment as provided in the file specified by the
     * "-overview" option on the command line.
     *
     * @param htmltree the documentation tree to which the overview comment will
     *                 be added
     */
    protected void addOverviewComment(Content htmltree) {
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            addInlineComment(configuration.overviewElement, htmltree);
        }
    }

    /**
     * For HTML 5, add the htmlTree to the body. For HTML 4, do nothing.
     *
     * @param body the documentation tree to which the overview will be added
     */
    @Override
    protected void addOverview(Content body) {
        if (configuration.allowTag(HtmlTag.MAIN)) {
            body.addContent(htmlTree);
        }
    }

    /**
     * Adds the top text (from the -top option), the upper
     * navigation bar, and then the title (from the"-title"
     * option), at the top of page.
     *
     * @param body the documentation tree to which the navigation bar header will be added
     */
    @Override
    protected void addNavigationBarHeader(Content body) {
        Content tree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : body;
        addTop(tree);
        addNavLinks(true, tree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            body.addContent(tree);
        }
    }

    /**
     * Adds the lower navigation bar and the bottom text
     * (from the -bottom option) at the bottom of page.
     *
     * @param body the documentation tree to which the navigation bar footer will be added
     */
    @Override
    protected void addNavigationBarFooter(Content body) {
        Content htmltree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : body;
        addNavLinks(false, htmltree);
        addBottom(htmltree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(htmltree);
        }
    }

    @Override
    protected void addModulePackagesList(Map<ModuleElement, Set<PackageElement>> modules, String text,
            String tableSummary, Content body, ModuleElement mdle) {
    }
}
