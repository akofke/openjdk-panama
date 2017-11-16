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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.DocletEnvironment.ModuleMode;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.ModuleSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Class to generate file for each module contents in the right-hand frame. This will list all the
 * required modules, packages and service types for the module. A click on any of the links will update
 * the frame with the clicked element page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleWriterImpl extends HtmlDocletWriter implements ModuleSummaryWriter {

    /**
     * The prev module name in the alpha-order list.
     */
    protected ModuleElement prevModule;

    /**
     * The next module name in the alpha-order list.
     */
    protected ModuleElement nextModule;

    /**
     * The module being documented.
     */
    protected ModuleElement mdle;

    /**
     * The module mode for this javadoc run. It can be set to "api" or "all".
     */
    private final ModuleMode moduleMode;

    /**
     * Map of module elements and modifiers required by this module.
     */
    private final Map<ModuleElement, Content> requires
            = new TreeMap<>(utils.makeModuleComparator());

    /**
     * Map of indirect modules and modifiers, transitive closure, required by this module.
     */
    private final Map<ModuleElement, Content> indirectModules
            = new TreeMap<>(utils.makeModuleComparator());

    /**
     * Map of packages exported by this module and the modules it has been exported to.
     */
    private final Map<PackageElement, SortedSet<ModuleElement>> exportedPackages
            = new TreeMap<>(utils.makePackageComparator());

    /**
     * Map of opened packages by this module and the modules it has been opened to.
     */
    private final Map<PackageElement, SortedSet<ModuleElement>> openedPackages
            = new TreeMap<>(utils.makePackageComparator());

    /**
     * Set of concealed packages of this module.
     */
    private final SortedSet<PackageElement> concealedPackages = new TreeSet<>(utils.makePackageComparator());

    /**
     * Map of indirect modules (transitive closure) and their exported packages.
     */
    private final Map<ModuleElement, SortedSet<PackageElement>> indirectPackages
            = new TreeMap<>(utils.makeModuleComparator());

    /**
     * Map of indirect modules (transitive closure) and their open packages.
     */
    private final Map<ModuleElement, SortedSet<PackageElement>> indirectOpenPackages
            = new TreeMap<>(utils.makeModuleComparator());

    /**
     * Set of services used by the module.
     */
    private final SortedSet<TypeElement> uses
            = new TreeSet<>(utils.makeAllClassesComparator());

    /**
     * Map of services used by the module and specified using @uses javadoc tag, and description.
     */
    private final Map<TypeElement, Content> usesTrees
            = new TreeMap<>(utils.makeAllClassesComparator());

    /**
     * Map of services provided by this module, and set of its implementations.
     */
    private final Map<TypeElement, SortedSet<TypeElement>> provides
            = new TreeMap<>(utils.makeAllClassesComparator());

    /**
     * Map of services provided by the module and specified using @provides javadoc tag, and
     * description.
     */
    private final Map<TypeElement, Content> providesTrees
            = new TreeMap<>(utils.makeAllClassesComparator());

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * The HTML tree for section tag.
     */
    protected HtmlTree sectionTree = HtmlTree.SECTION();

    /**
     * Constructor to construct ModuleWriter object and to generate "moduleName-summary.html" file.
     *
     * @param configuration the configuration of the doclet.
     * @param mdle        Module under consideration.
     * @param prevModule   Previous module in the sorted array.
     * @param nextModule   Next module in the sorted array.
     */
    public ModuleWriterImpl(HtmlConfiguration configuration,
            ModuleElement mdle, ModuleElement prevModule, ModuleElement nextModule) {
        super(configuration, DocPaths.moduleSummary(mdle));
        this.prevModule = prevModule;
        this.nextModule = nextModule;
        this.mdle = mdle;
        this.moduleMode = configuration.docEnv.getModuleMode();
        computeModulesData();
    }

    /**
     * Get the module header.
     *
     * @param heading the heading for the section
     */
    @Override
    public Content getModuleHeader(String heading) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(mdle.getQualifiedName().toString()));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.header);
        Content annotationContent = new HtmlTree(HtmlTag.P);
        addAnnotationInfo(mdle, annotationContent);
        div.addContent(annotationContent);
        Content label = mdle.isOpen() && (configuration.docEnv.getModuleMode() == ModuleMode.ALL)
                ? contents.openModuleLabel : contents.moduleLabel;
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, label);
        tHeading.addContent(Contents.SPACE);
        Content moduleHead = new RawHtml(heading);
        tHeading.addContent(moduleHead);
        div.addContent(tHeading);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            bodyTree.addContent(div);
        }
        return bodyTree;
    }

    /**
     * Get the content header.
     */
    @Override
    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.contentContainer);
        return div;
    }

    /**
     * Get the summary section header.
     */
    @Override
    public Content getSummaryHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.setStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Get the summary tree.
     *
     * @param summaryContentTree the content tree to be added to the summary tree.
     */
    @Override
    public Content getSummaryTree(Content summaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, summaryContentTree);
        return ul;
    }

    /**
     * Compute the modules data that will be displayed in various tables on the module summary page.
     */
    public void computeModulesData() {
        CommentHelper ch = utils.getCommentHelper(mdle);
        // Get module dependencies using the module's transitive closure.
        Map<ModuleElement, String> dependentModules = utils.getDependentModules(mdle);
        // Add all dependent modules to indirect modules set. We will remove the modules,
        // listed using the requires directive, from this set to come up with the table of indirect
        // required modules.
        dependentModules.forEach((module, mod) -> {
            if (shouldDocument(module)) {
                indirectModules.put(module, new StringContent(mod));
            }
        });
        (ElementFilter.requiresIn(mdle.getDirectives())).forEach((directive) -> {
            ModuleElement m = directive.getDependency();
            if (shouldDocument(m)) {
                if (moduleMode == ModuleMode.ALL || directive.isTransitive()) {
                    requires.put(m, new StringContent(utils.getModifiers(directive)));
            } else {
                    // For api mode, just keep the public requires in dependentModules for display of
                    // indirect packages in the "Packages" section.
                    dependentModules.remove(m);
                }
                indirectModules.remove(m);
        }
        });

        // Get all packages for the module and put it in the concealed packages set.
        utils.getModulePackageMap().getOrDefault(mdle, Collections.emptySet()).forEach((pkg) -> {
            if (shouldDocument(pkg) && moduleMode == ModuleMode.ALL) {
                concealedPackages.add(pkg);
            }
        });

        // Get all exported packages for the module using the exports directive for the module.
        (ElementFilter.exportsIn(mdle.getDirectives())).forEach((directive) -> {
            PackageElement p = directive.getPackage();
            if (shouldDocument(p)) {
                SortedSet<ModuleElement> mdleList = new TreeSet<>(utils.makeModuleComparator());
                List<? extends ModuleElement> targetMdles = directive.getTargetModules();
                if (targetMdles != null) {
                    mdleList.addAll(targetMdles);
                }
                // Qualified exports should not be displayed in the api mode. So if mdleList is empty,
                // its exported to all modules and hence can be added.
                if (moduleMode == ModuleMode.ALL || mdleList.isEmpty()) {
                    exportedPackages.put(p, mdleList);
                }
                if (moduleMode == ModuleMode.ALL) {
                    concealedPackages.remove(p);
                }
            }
        });
        // Get all opened packages for the module using the opens directive for the module.
        (ElementFilter.opensIn(mdle.getDirectives())).forEach((directive) -> {
            PackageElement p = directive.getPackage();
            if (shouldDocument(p)) {
                SortedSet<ModuleElement> mdleList = new TreeSet<>(utils.makeModuleComparator());
                List<? extends ModuleElement> targetMdles = directive.getTargetModules();
                if (targetMdles != null) {
                    mdleList.addAll(targetMdles);
                }
                // Qualified opens should not be displayed in the api mode. So if mdleList is empty,
                // it is opened to all modules and hence can be added.
                if (moduleMode == ModuleMode.ALL || mdleList.isEmpty()) {
                    openedPackages.put(p, mdleList);
                }
                if (moduleMode == ModuleMode.ALL) {
                    concealedPackages.remove(p);
                }
            }
        });
        // Get all the exported and opened packages, for the transitive closure of the module, to be displayed in
        // the indirect packages tables.
        dependentModules.forEach((module, mod) -> {
            SortedSet<PackageElement> exportPkgList = new TreeSet<>(utils.makePackageComparator());
            (ElementFilter.exportsIn(module.getDirectives())).forEach((directive) -> {
                PackageElement pkg = directive.getPackage();
                if (shouldDocument(pkg)) {
                    // Qualified exports are not displayed in API mode
                    if (moduleMode == ModuleMode.ALL || directive.getTargetModules() == null) {
                        exportPkgList.add(pkg);
                    }
                }
            });
            // If none of the indirect modules have exported packages to be displayed, we should not be
            // displaying the table and so it should not be added to the map.
            if (!exportPkgList.isEmpty()) {
                indirectPackages.put(module, exportPkgList);
            }
            SortedSet<PackageElement> openPkgList = new TreeSet<>(utils.makePackageComparator());
            (ElementFilter.opensIn(module.getDirectives())).forEach((directive) -> {
                PackageElement pkg = directive.getPackage();
                if (shouldDocument(pkg)) {
                    // Qualified opens are not displayed in API mode
                    if (moduleMode == ModuleMode.ALL || directive.getTargetModules() == null) {
                        openPkgList.add(pkg);
                    }
                }
            });
            // If none of the indirect modules have opened packages to be displayed, we should not be
            // displaying the table and so it should not be added to the map.
            if (!openPkgList.isEmpty()) {
                indirectOpenPackages.put(module, openPkgList);
            }
        });
        // Get all the services listed as uses directive.
        (ElementFilter.usesIn(mdle.getDirectives())).forEach((directive) -> {
            TypeElement u = directive.getService();
            if (shouldDocument(u)) {
                uses.add(u);
            }
        });
        // Get all the services and implementations listed as provides directive.
        (ElementFilter.providesIn(mdle.getDirectives())).forEach((directive) -> {
            TypeElement u = directive.getService();
            if (shouldDocument(u)) {
                List<? extends TypeElement> implList = directive.getImplementations();
                SortedSet<TypeElement> implSet = new TreeSet<>(utils.makeAllClassesComparator());
                implSet.addAll(implList);
                provides.put(u, implSet);
            }
        });
        // Generate the map of all services listed using @provides, and the description.
        (utils.getBlockTags(mdle, DocTree.Kind.PROVIDES)).forEach((tree) -> {
            TypeElement t = ch.getServiceType(configuration, tree);
            if (t != null) {
                providesTrees.put(t, commentTagsToContent(tree, mdle, ch.getDescription(configuration, tree), false));
            }
        });
        // Generate the map of all services listed using @uses, and the description.
        (utils.getBlockTags(mdle, DocTree.Kind.USES)).forEach((tree) -> {
            TypeElement t = ch.getServiceType(configuration, tree);
            if (t != null) {
                usesTrees.put(t, commentTagsToContent(tree, mdle, ch.getDescription(configuration, tree), false));
            }
        });
    }

    /**
     * Returns true if the element should be documented on the module summary page.
     *
     * @param element the element to be checked
     * @return true if the element should be documented
     */
    public boolean shouldDocument(Element element) {
        return (moduleMode == ModuleMode.ALL || utils.isIncluded(element));
    }

    /**
     * Returns true if there are elements to be displayed.
     *
     * @param section set of elements
     * @return true if there are elements to be displayed
     */
    public boolean display(Set<? extends Element> section) {
        return section != null && !section.isEmpty();
    }

    /**
     * Returns true if there are elements to be displayed.
     *
     * @param section map of elements.
     * @return true if there are elements to be displayed
     */
    public boolean display(Map<? extends Element, ?> section) {
        return section != null && !section.isEmpty();
    }

    /*
     * Returns true, in API mode, if at least one type element in
     * the typeElements set is referenced by a javadoc tag in tagsMap.
     */
    private boolean displayServices(Set<TypeElement> typeElements,
                                    Map<TypeElement, Content> tagsMap) {
        return typeElements != null &&
                typeElements.stream().anyMatch((v) -> displayServiceDirective(v, tagsMap));
    }

    /*
     * Returns true, in API mode, if the type element is referenced
     * from a javadoc tag in tagsMap.
     */
    private boolean displayServiceDirective(TypeElement typeElement,
                                            Map<TypeElement, Content> tagsMap) {
        return moduleMode == ModuleMode.ALL || tagsMap.containsKey(typeElement);
    }

    /**
     * Add the summary header.
     *
     * @param startMarker the marker comment
     * @param markerAnchor the marker anchor for the section
     * @param heading the heading for the section
     * @param htmltree the content tree to which the information is added
     */
    public void addSummaryHeader(Content startMarker, SectionName markerAnchor, Content heading,
            Content htmltree) {
        htmltree.addContent(startMarker);
        htmltree.addContent(getMarkerAnchor(markerAnchor));
        htmltree.addContent(HtmlTree.HEADING(HtmlTag.H3, heading));
    }

    /**
     * Get a table, with two columns.
     *
     * @param caption the table caption
     * @param tableSummary the summary for the table
     * @param tableStyle the table style
     * @param tableHeader the table header
     * @return a content object
     */
    private Table getTable2(Content caption, String tableSummary, HtmlStyle tableStyle,
            TableHeader tableHeader) {
        return new Table(configuration.htmlVersion, tableStyle)
                .setSummary(tableSummary)
                .setCaption(caption)
                .setHeader(tableHeader)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
    }

    /**
     * Get a table, with three columns, with the second column being the defining column.
     *
     * @param caption the table caption
     * @param tableSummary the summary for the table
     * @param tableStyle the table style
     * @param tableHeader the table header
     * @return a content object
     */
    private Table getTable3(Content caption, String tableSummary, HtmlStyle tableStyle,
            TableHeader tableHeader) {
        return new Table(configuration.htmlVersion, tableStyle)
                .setSummary(tableSummary)
                .setCaption(caption)
                .setHeader(tableHeader)
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModulesSummary(Content summaryContentTree) {
        if (display(requires) || display(indirectModules)) {
            TableHeader requiresTableHeader =
                    new TableHeader(contents.modifierLabel, contents.moduleLabel,
                            contents.descriptionLabel);
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.setStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_MODULES_SUMMARY, SectionName.MODULES,
                    contents.navModules, li);
            if (display(requires)) {
                String text = resources.getText("doclet.Requires_Summary");
                String tableSummary = resources.getText("doclet.Member_Table_Summary",
                        text,
                        resources.getText("doclet.modules"));
                Content caption = getTableCaption(new StringContent(text));
                Table table = getTable3(caption, tableSummary, HtmlStyle.requiresSummary,
                            requiresTableHeader);
                addModulesList(requires, table);
                li.addContent(table.toContent());
            }
            // Display indirect modules table in both "api" and "all" mode.
            if (display(indirectModules)) {
                String amrText = resources.getText("doclet.Indirect_Requires_Summary");
                String amrTableSummary = resources.getText("doclet.Member_Table_Summary",
                        amrText,
                        configuration.getText("doclet.modules"));
                Content amrCaption = getTableCaption(new StringContent(amrText));
                Table amrTable = getTable3(amrCaption, amrTableSummary, HtmlStyle.requiresSummary,
                            requiresTableHeader);
                addModulesList(indirectModules, amrTable);
                li.addContent(amrTable.toContent());
            }
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the list of modules.
     *
     * @param mdleMap map of modules and modifiers
     * @param tbody the content tree to which the list will be added
     */
    private void addModulesList(Map<ModuleElement, Content> mdleMap, Table table) {
        for (ModuleElement m : mdleMap.keySet()) {
            Content modifiers = mdleMap.get(m);
            Content moduleLink = getModuleLink(m, new StringContent(m.getQualifiedName()));
            Content moduleSummary = new ContentBuilder();
            addSummaryComment(m, moduleSummary);
            table.addRow(modifiers, moduleLink, moduleSummary);
        }
    }

    @Override
    public void addPackagesSummary(Content summaryContentTree) {
        if (display(exportedPackages) || display(openedPackages) || display(concealedPackages)
                || display(indirectPackages) || display(indirectOpenPackages)) {
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.setStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_PACKAGES_SUMMARY, SectionName.PACKAGES,
                    contents.navPackages, li);
            if (display(exportedPackages) || display(openedPackages) || display(concealedPackages)) {
                String tableSummary = resources.getText("doclet.Member_Table_Summary",
                        resources.getText("doclet.Packages_Summary"),
                        resources.getText("doclet.packages"));
                addPackageSummary(tableSummary, li);
            }
            TableHeader indirectPackagesHeader =
                    new TableHeader(contents.fromLabel, contents.packagesLabel);
            if (display(indirectPackages)) {
                String aepText = resources.getText("doclet.Indirect_Exports_Summary");
                String aepTableSummary = resources.getText("doclet.Indirect_Packages_Table_Summary",
                        aepText,
                        resources.getText("doclet.modules"),
                        resources.getText("doclet.packages"));
                Table aepTable = getTable2(new StringContent(aepText), aepTableSummary,
                        HtmlStyle.packagesSummary, indirectPackagesHeader);
                addIndirectPackages(aepTable, indirectPackages);
                li.addContent(aepTable.toContent());
            }
            if (display(indirectOpenPackages)) {
                String aopText = resources.getText("doclet.Indirect_Opens_Summary");
                String aopTableSummary = resources.getText("doclet.Indirect_Packages_Table_Summary",
                        aopText,
                        resources.getText("doclet.modules"),
                        resources.getText("doclet.packages"));
                Table aopTable = getTable2(new StringContent(aopText), aopTableSummary,
                        HtmlStyle.packagesSummary, indirectPackagesHeader);
                addIndirectPackages(aopTable, indirectOpenPackages);
                li.addContent(aopTable.toContent());
            }
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the package summary for the module.
     *
     * @param tableSummary
     * @param li
     */
    public void addPackageSummary(String tableSummary, HtmlTree li) {
        Table table = new Table(configuration.htmlVersion, HtmlStyle.packagesSummary)
                .setSummary(tableSummary)
                .setDefaultTab(resources.getText("doclet.All_Packages"))
                .addTab(resources.getText("doclet.Exported_Packages_Summary"),
                        e -> exportedPackages.containsKey((PackageElement) e))
                .addTab(resources.getText("doclet.Opened_Packages_Summary"),
                        e -> openedPackages.containsKey((PackageElement) e))
                .addTab(resources.getText("doclet.Concealed_Packages_Summary"),
                        e -> concealedPackages.contains((PackageElement) e))
                .setTabScript(i -> String.format("showPkgs(%d);", i))
                .setTabScriptVariable("packages");

        if (configuration.docEnv.getModuleMode() == ModuleMode.API) {
            table.setHeader(new TableHeader(contents.packageLabel, contents.descriptionLabel))
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        } else {
            table.setHeader(new TableHeader(contents.packageLabel, contents.moduleLabel, contents.descriptionLabel))
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);
        }

        addPackageTableRows(table);
        li.addContent(table.toContent());
        if (table.needsScript()) {
            script.addContent(new RawHtml(table.getScript()));
        }
    }

    /**
     * Get the package table rows.
     *
     * @return a content object
     */
    private void addPackageTableRows(Table table) {
        addPackageTableRows(table, exportedPackages);
        addPackageTableRows(table, openedPackages);
        // Show concealed packages only in "all" mode.
        if (moduleMode == ModuleMode.ALL) {
            for (PackageElement pkg : concealedPackages) {
                Content pkgLinkContent = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
                Content noModules = new StringContent(resources.getText("doclet.None"));
                Content summary = new ContentBuilder();
                addSummaryComment(pkg, summary);
                table.addRow(pkg, pkgLinkContent, noModules, summary);
            }
        }
    }

    private void addPackageTableRows(Table table, Map<PackageElement,SortedSet<ModuleElement>> ap) {
        for (Map.Entry<PackageElement, SortedSet<ModuleElement>> entry : ap.entrySet()) {
            List<Content> row = new ArrayList<>();
            PackageElement pkg = entry.getKey();
            SortedSet<ModuleElement> mdleList = entry.getValue();
            Content pkgLinkContent = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
            row.add(pkgLinkContent);

            if (moduleMode == ModuleMode.ALL) {
                Content modules = new ContentBuilder();
                if (!mdleList.isEmpty()) {
                    for (ModuleElement m : mdleList) {
                        if (!modules.isEmpty()) {
                            modules.addContent(new HtmlTree(HtmlTag.BR));
                        }
                        modules.addContent(getModuleLink(m, new StringContent(m.getQualifiedName())));
                    }
                } else {
                    Content allModules = new StringContent(resources.getText("doclet.All_Modules"));
                    modules.addContent(allModules);
                }
                row.add(modules);
            }
            Content summary = new ContentBuilder();
            addSummaryComment(pkg, summary);
            row.add(summary);
            table.addRow(pkg, row);
        }
    }

    /**
     * Add the indirect packages for the module being documented.
     *
     * @param table the table to which the content rows will be added
     * @param ip indirect packages to be added
     */
    public void addIndirectPackages(Table table, Map<ModuleElement, SortedSet<PackageElement>> ip) {
        for (Map.Entry<ModuleElement, SortedSet<PackageElement>> entry : ip.entrySet()) {
            ModuleElement m = entry.getKey();
            SortedSet<PackageElement> pkgList = entry.getValue();
            Content moduleLinkContent = getModuleLink(m, new StringContent(m.getQualifiedName()));
            Content packages = new ContentBuilder();
            String sep = "";
            for (PackageElement pkg : pkgList) {
                packages.addContent(sep);
                packages.addContent(getPackageLink(pkg, new StringContent(utils.getPackageName(pkg))));
                sep = " ";
            }
            table.addRow(moduleLinkContent, packages);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addServicesSummary(Content summaryContentTree) {

        boolean haveUses = displayServices(uses, usesTrees);
        boolean haveProvides = displayServices(provides.keySet(), providesTrees);

        if (haveProvides || haveUses) {
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.setStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_SERVICES_SUMMARY, SectionName.SERVICES,
                    contents.navServices, li);
            TableHeader usesProvidesTableHeader =
                    new TableHeader(contents.typeLabel, contents.descriptionLabel);
            if (haveProvides) {
                String label = resources.getText("doclet.Provides_Summary");
                String tableSummary = resources.getText("doclet.Member_Table_Summary",
                        label,
                        resources.getText("doclet.types"));
                Table table = getTable2(new StringContent(label), tableSummary, HtmlStyle.providesSummary,
                        usesProvidesTableHeader);
                addProvidesList(table);
                if (!table.isEmpty()) {
                    li.addContent(table.toContent());
                }
            }
            if (haveUses){
                String label = resources.getText("doclet.Uses_Summary");
                String tableSummary = resources.getText("doclet.Member_Table_Summary",
                        label,
                        resources.getText("doclet.types"));
                Table table = getTable2(new StringContent(label), tableSummary, HtmlStyle.usesSummary,
                        usesProvidesTableHeader);
                addUsesList(table);
                if (!table.isEmpty()) {
                    li.addContent(table.toContent());
                }
            }
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the uses list for the module.
     *
     * @param table the table to which the services used will be added
     */
    public void addUsesList(Table table) {
        Content typeLinkContent;
        Content description;
        for (TypeElement t : uses) {
            if (!displayServiceDirective(t, usesTrees)) {
                continue;
            }
            typeLinkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, t));
            Content summary = new ContentBuilder();
            if (display(usesTrees)) {
                description = usesTrees.get(t);
                if (description != null) {
                    summary.addContent(description);
                }
            }
            addSummaryComment(t, summary);
            table.addRow(typeLinkContent, summary);
        }
    }

    /**
     * Add the provides list for the module.
     *
     * @param table the table to which the services provided will be added
     */
    public void addProvidesList(Table table) {
        SortedSet<TypeElement> implSet;
        Content description;
        for (Map.Entry<TypeElement, SortedSet<TypeElement>> entry : provides.entrySet()) {
            TypeElement srv = entry.getKey();
            if (!displayServiceDirective(srv, providesTrees)) {
                continue;
            }
            implSet = entry.getValue();
            Content srvLinkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, srv));
            Content desc = new ContentBuilder();
            if (display(providesTrees)) {
                description = providesTrees.get(srv);
                if (description != null) {
                    desc.addContent(description);
                }
            }
            addSummaryComment(srv, desc);
            // Only display the implementation details in the "all" mode.
            if (moduleMode == ModuleMode.ALL && !implSet.isEmpty()) {
                desc.addContent(new HtmlTree(HtmlTag.BR));
                desc.addContent("(");
                HtmlTree implSpan = HtmlTree.SPAN(HtmlStyle.implementationLabel, contents.implementation);
                desc.addContent(implSpan);
                desc.addContent(Contents.SPACE);
                String sep = "";
                for (TypeElement impl : implSet) {
                    desc.addContent(sep);
                    desc.addContent(getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, impl)));
                    sep = ", ";
                }
                desc.addContent(")");
            }
            table.addRow(srvLinkContent, desc);
        }
    }

    /**
     * Add the module deprecation information to the documentation tree.
     *
     * @param div the content tree to which the deprecation information will be added
     */
    public void addDeprecationInfo(Content div) {
        List<? extends DocTree> deprs = utils.getBlockTags(mdle, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(mdle)) {
            CommentHelper ch = utils.getCommentHelper(mdle);
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.setStyle(HtmlStyle.deprecationBlock);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(mdle));
            deprDiv.addContent(deprPhrase);
            if (!deprs.isEmpty()) {
                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(mdle, deprs.get(0), deprDiv);
                }
            }
            div.addContent(deprDiv);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModuleDescription(Content moduleContentTree) {
        if (!utils.getFullBody(mdle).isEmpty()) {
            Content tree = configuration.allowTag(HtmlTag.SECTION) ? HtmlTree.SECTION() : moduleContentTree;
            addDeprecationInfo(tree);
            tree.addContent(HtmlConstants.START_OF_MODULE_DESCRIPTION);
            tree.addContent(getMarkerAnchor(SectionName.MODULE_DESCRIPTION));
            addInlineComment(mdle, tree);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                moduleContentTree.addContent(tree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModuleTags(Content moduleContentTree) {
        Content tree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION()
                : moduleContentTree;
        addTagsInfo(mdle, tree);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            moduleContentTree.addContent(tree);
        }
    }

    /**
     * Add summary details to the navigation bar.
     *
     * @param subDiv the content tree to which the summary detail links will be added
     */
    @Override
    protected void addSummaryDetailLinks(Content subDiv) {
        Content div = HtmlTree.DIV(getNavSummaryLinks());
        subDiv.addContent(div);
    }

    /**
     * Get summary links for navigation bar.
     *
     * @return the content tree for the navigation summary links
     */
    protected Content getNavSummaryLinks() {
        Content li = HtmlTree.LI(contents.moduleSubNavLabel);
        li.addContent(Contents.SPACE);
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        Content liNav = new HtmlTree(HtmlTag.LI);
        liNav.addContent(!utils.getFullBody(mdle).isEmpty() && !configuration.nocomment
                ? getHyperLink(SectionName.MODULE_DESCRIPTION, contents.navModuleDescription)
                : contents.navModuleDescription);
        addNavGap(liNav);
        liNav.addContent((display(requires) || display(indirectModules))
                ? getHyperLink(SectionName.MODULES, contents.navModules)
                : contents.navModules);
        addNavGap(liNav);
        liNav.addContent((display(exportedPackages) || display(openedPackages) || display(concealedPackages)
                || display(indirectPackages) || display(indirectOpenPackages))
                ? getHyperLink(SectionName.PACKAGES, contents.navPackages)
                : contents.navPackages);
        addNavGap(liNav);
        liNav.addContent((displayServices(uses, usesTrees) || displayServices(provides.keySet(), providesTrees))
                ? getHyperLink(SectionName.SERVICES, contents.navServices)
                : contents.navServices);
        ulNav.addContent(liNav);
        return ulNav;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModuleContent(Content contentTree, Content moduleContentTree) {
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(moduleContentTree);
            contentTree.addContent(mainTree);
        } else {
            contentTree.addContent(moduleContentTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addModuleFooter(Content contentTree) {
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : contentTree;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            contentTree.addContent(htmlTree);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException
     */
    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywordsForModule(mdle),
                true, contentTree);
    }

    /**
     * Add the module package deprecation information to the documentation tree.
     *
     * @param li the content tree to which the deprecation information will be added
     * @param pkg the PackageDoc that is added
     */
    public void addPackageDeprecationInfo(Content li, PackageElement pkg) {
        List<? extends DocTree> deprs;
        if (utils.isDeprecated(pkg)) {
            deprs = utils.getDeprecatedTrees(pkg);
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.setStyle(HtmlStyle.deprecationBlock);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(pkg));
            deprDiv.addContent(deprPhrase);
            if (!deprs.isEmpty()) {
                CommentHelper ch = utils.getCommentHelper(pkg);
                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(pkg, deprs.get(0), deprDiv);
                }
            }
            li.addContent(deprDiv);
        }
    }

    /**
     * Get this module link.
     *
     * @return a content tree for the module link
     */
    @Override
    protected Content getNavLinkModule() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, contents.moduleLabel);
        return li;
    }

    /**
     * Get "PREV MODULE" link in the navigation bar.
     *
     * @return a content tree for the previous link
     */
    @Override
    public Content getNavLinkPrevious() {
        Content li;
        if (prevModule == null) {
            li = HtmlTree.LI(contents.prevModuleLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.moduleSummary(
                    prevModule)), contents.prevModuleLabel, "", ""));
        }
        return li;
    }

    /**
     * Get "NEXT MODULE" link in the navigation bar.
     *
     * @return a content tree for the next link
     */
    @Override
    public Content getNavLinkNext() {
        Content li;
        if (nextModule == null) {
            li = HtmlTree.LI(contents.nextModuleLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.moduleSummary(
                    nextModule)), contents.nextModuleLabel, "", ""));
        }
        return li;
    }
}
