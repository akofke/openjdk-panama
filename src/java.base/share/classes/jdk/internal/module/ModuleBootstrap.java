/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;

import java.io.File;
import java.io.PrintStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.perf.PerfCounter;

/**
 * Initializes/boots the module system.
 *
 * The {@link #boot() boot} method is called early in the startup to initialize
 * the module system. In summary, the boot method creates a Configuration by
 * resolving a set of module names specified via the launcher (or equivalent)
 * -m and --add-modules options. The modules are located on a module path that
 * is constructed from the upgrade module path, system modules, and application
 * module path. The Configuration is instantiated as the boot layer with each
 * module in the configuration defined to a class loader.
 */

public final class ModuleBootstrap {
    private ModuleBootstrap() { }

    private static final String JAVA_BASE = "java.base";

    // the token for "all default modules"
    private static final String ALL_DEFAULT = "ALL-DEFAULT";

    // the token for "all unnamed modules"
    private static final String ALL_UNNAMED = "ALL-UNNAMED";

    // the token for "all system modules"
    private static final String ALL_SYSTEM = "ALL-SYSTEM";

    // the token for "all modules on the module path"
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

    // access to java.lang/module
    private static final JavaLangModuleAccess JLMA
        = SharedSecrets.getJavaLangModuleAccess();

    // The ModulePatcher for the initial configuration
    private static final ModulePatcher patcher = initModulePatcher();

    /**
     * Returns the ModulePatcher for the initial configuration.
     */
    public static ModulePatcher patcher() {
        return patcher;
    }

    // ModuleFinders for the initial configuration
    private static volatile ModuleFinder unlimitedFinder;
    private static volatile ModuleFinder limitedFinder;

    /**
     * Returns the ModuleFinder for the initial configuration before
     * observability is limited by the --limit-modules command line option.
     *
     * @apiNote Used to support locating modules {@code java.instrument} and
     * {@code jdk.management.agent} modules when they are loaded dynamically.
     */
    public static ModuleFinder unlimitedFinder() {
        ModuleFinder finder = unlimitedFinder;
        if (finder == null) {
            return ModuleFinder.ofSystem();
        } else {
            return finder;
        }
    }

    /**
     * Returns the ModuleFinder for the initial configuration.
     *
     * @apiNote Used to support "{@code java --list-modules}".
     */
    public static ModuleFinder limitedFinder() {
        ModuleFinder finder = limitedFinder;
        if (finder == null) {
            return unlimitedFinder();
        } else {
            return finder;
        }
    }

    /**
     * Initialize the module system, returning the boot layer.
     *
     * @see java.lang.System#initPhase2(boolean, boolean)
     */
    public static ModuleLayer boot() throws Exception {

        // Step 0: Command line options

        long t0 = System.nanoTime();

        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        ModuleFinder appModulePath = finderFor("jdk.module.path");
        boolean isPatched = patcher.hasPatches();

        String mainModule = System.getProperty("jdk.module.main");
        Set<String> addModules = addModules();
        Set<String> limitModules = limitModules();

        PrintStream traceOutput = null;
        String trace = getAndRemoveProperty("jdk.module.showModuleResolution");
        if (trace != null && Boolean.parseBoolean(trace))
            traceOutput = System.out;


        // Step 1: The observable system modules, either all system modules
        // or the system modules pre-generated for the initial module (the
        // initial module may be the unnamed module). If the system modules
        // are pre-generated for the initial module then resolution can be
        // skipped.

        long t1 = System.nanoTime();

        SystemModules systemModules = null;
        ModuleFinder systemModuleFinder;

        boolean haveModulePath = (appModulePath != null || upgradeModulePath != null);
        boolean needResolution = true;

        // If the java heap was archived at CDS dump time and the environment
        // at dump time matches the current environment then use the archived
        // system modules and finder.
        ArchivedModuleGraph archivedModuleGraph = ArchivedModuleGraph.get(mainModule);
        if (archivedModuleGraph != null
                && !haveModulePath
                && addModules.isEmpty()
                && limitModules.isEmpty()
                && !isPatched) {
            systemModules = archivedModuleGraph.systemModules();
            systemModuleFinder = archivedModuleGraph.finder();
            needResolution = (traceOutput != null);
        } else {
            boolean canArchive = false;
            if (!haveModulePath && addModules.isEmpty() && limitModules.isEmpty()) {
                systemModules = SystemModuleFinders.systemModules(mainModule);
                if (systemModules != null && !isPatched) {
                    needResolution = (traceOutput != null);
                    canArchive = true;
                }
            }
            if (systemModules == null) {
                // all system modules are observable
                systemModules = SystemModuleFinders.allSystemModules();
            }
            if (systemModules != null) {
                // images build
                systemModuleFinder = SystemModuleFinders.of(systemModules);
            } else {
                // exploded build or testing
                systemModules = new ExplodedSystemModules();
                systemModuleFinder = SystemModuleFinders.ofSystem();
            }

            // Module graph can be archived at CDS dump time. Only allow the
            // unnamed module case for now.
            if (canArchive && (mainModule == null)) {
                ArchivedModuleGraph.archive(mainModule, systemModules, systemModuleFinder);
            }
        }

        Counters.add("jdk.module.boot.1.systemModulesTime", t1);


        // Step 2: Define and load java.base. This patches all classes loaded
        // to date so that they are members of java.base. Once java.base is
        // loaded then resources in java.base are available for error messages
        // needed from here on.

        long t2 = System.nanoTime();

        ModuleReference base = systemModuleFinder.find(JAVA_BASE).orElse(null);
        if (base == null)
            throw new InternalError(JAVA_BASE + " not found");
        URI baseUri = base.location().orElse(null);
        if (baseUri == null)
            throw new InternalError(JAVA_BASE + " does not have a location");
        BootLoader.loadModule(base);
        Modules.defineModule(null, base.descriptor(), baseUri);

        Counters.add("jdk.module.boot.2.defineBaseTime", t2);


        // Step 2a: Scan all modules when --validate-modules specified

        if (getAndRemoveProperty("jdk.module.validation") != null) {
            int errors = ModulePathValidator.scanAllModules(System.out);
            if (errors > 0) {
                fail("Validation of module path failed");
            }
        }


        // Step 3: If resolution is needed then create the module finder and
        // the set of root modules to resolve.

        long t3 = System.nanoTime();

        ModuleFinder savedModuleFinder = null;
        ModuleFinder finder;
        Set<String> roots;
        if (needResolution) {

            // upgraded modules override the modules in the run-time image
            if (upgradeModulePath != null)
                systemModuleFinder = ModuleFinder.compose(upgradeModulePath,
                                                          systemModuleFinder);

            // The module finder: [--upgrade-module-path] system [--module-path]
            if (appModulePath != null) {
                finder = ModuleFinder.compose(systemModuleFinder, appModulePath);
            } else {
                finder = systemModuleFinder;
            }

            // The root modules to resolve
            roots = new HashSet<>();

            // launcher -m option to specify the main/initial module
            if (mainModule != null)
                roots.add(mainModule);

            // additional module(s) specified by --add-modules
            boolean addAllDefaultModules = false;
            boolean addAllSystemModules = false;
            boolean addAllApplicationModules = false;
            for (String mod : addModules) {
                switch (mod) {
                    case ALL_DEFAULT:
                        addAllDefaultModules = true;
                        break;
                    case ALL_SYSTEM:
                        addAllSystemModules = true;
                        break;
                    case ALL_MODULE_PATH:
                        addAllApplicationModules = true;
                        break;
                    default:
                        roots.add(mod);
                }
            }

            // --limit-modules
            savedModuleFinder = finder;
            if (!limitModules.isEmpty()) {
                finder = limitFinder(finder, limitModules, roots);
            }

            // If there is no initial module specified then assume that the initial
            // module is the unnamed module of the application class loader. This
            // is implemented by resolving all observable modules that export an
            // API. Modules that have the DO_NOT_RESOLVE_BY_DEFAULT bit set in
            // their ModuleResolution attribute flags are excluded from the
            // default set of roots.
            if (mainModule == null || addAllDefaultModules) {
                roots.addAll(DefaultRoots.compute(systemModuleFinder, finder));
            }

            // If `--add-modules ALL-SYSTEM` is specified then all observable system
            // modules will be resolved.
            if (addAllSystemModules) {
                ModuleFinder f = finder;  // observable modules
                systemModuleFinder.findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(mn -> f.find(mn).isPresent())  // observable
                    .forEach(mn -> roots.add(mn));
            }

            // If `--add-modules ALL-MODULE-PATH` is specified then all observable
            // modules on the application module path will be resolved.
            if (appModulePath != null && addAllApplicationModules) {
                ModuleFinder f = finder;  // observable modules
                appModulePath.findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(mn -> f.find(mn).isPresent())  // observable
                    .forEach(mn -> roots.add(mn));
            }
        } else {
            // no resolution case
            finder = systemModuleFinder;
            roots = null;
        }

        Counters.add("jdk.module.boot.3.optionsAndRootsTime", t3);

        // Step 4: Resolve the root modules, with service binding, to create
        // the configuration for the boot layer. If resolution is not needed
        // then create the configuration for the boot layer from the
        // readability graph created at link time.

        long t4 = System.nanoTime();

        Configuration cf;
        if (needResolution) {
            cf = JLMA.resolveAndBind(finder, roots, traceOutput);
        } else {
            Map<String, Set<String>> map = systemModules.moduleReads();
            cf = JLMA.newConfiguration(systemModuleFinder, map);
        }

        // check that modules specified to --patch-module are resolved
        if (isPatched) {
            patcher.patchedModules()
                    .stream()
                    .filter(mn -> !cf.findModule(mn).isPresent())
                    .forEach(mn -> warnUnknownModule(PATCH_MODULE, mn));
        }

        Counters.add("jdk.module.boot.4.resolveTime", t4);


        // Step 5: Map the modules in the configuration to class loaders.
        // The static configuration provides the mapping of standard and JDK
        // modules to the boot and platform loaders. All other modules (JDK
        // tool modules, and both explicit and automatic modules on the
        // application module path) are defined to the application class
        // loader.

        long t5 = System.nanoTime();

        // mapping of modules to class loaders
        Function<String, ClassLoader> clf = ModuleLoaderMap.mappingFunction(cf);

        // check that all modules to be mapped to the boot loader will be
        // loaded from the runtime image
        if (haveModulePath) {
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                String name = mref.descriptor().name();
                ClassLoader cl = clf.apply(name);
                if (cl == null) {
                    if (upgradeModulePath != null
                            && upgradeModulePath.find(name).isPresent())
                        fail(name + ": cannot be loaded from upgrade module path");
                    if (!systemModuleFinder.find(name).isPresent())
                        fail(name + ": cannot be loaded from application module path");
                }
            }
        }

        // check for split packages in the modules mapped to the built-in loaders
        if (systemModules.hasSplitPackages() || isPatched || haveModulePath) {
            checkSplitPackages(cf, clf);
        }

        // load/register the modules with the built-in class loaders
        loadModules(cf, clf);

        Counters.add("jdk.module.boot.5.loadModulesTime", t5);


        // Step 6: Define all modules to the VM

        long t6 = System.nanoTime();
        ModuleLayer bootLayer = ModuleLayer.empty().defineModules(cf, clf);
        Counters.add("jdk.module.boot.6.layerCreateTime", t6);


        // Step 7: Miscellaneous

        // check incubating status
        if (systemModules.hasIncubatorModules() || haveModulePath) {
            checkIncubatingStatus(cf);
        }

        // --add-reads, --add-exports/--add-opens, and --illegal-access
        long t7 = System.nanoTime();
        addExtraReads(bootLayer);
        boolean extraExportsOrOpens = addExtraExportsAndOpens(bootLayer);
        addIllegalAccess(upgradeModulePath, systemModules, bootLayer, extraExportsOrOpens);
        Counters.add("jdk.module.boot.7.adjustModulesTime", t7);

        // save module finders for later use
        if (savedModuleFinder != null) {
            unlimitedFinder = new SafeModuleFinder(savedModuleFinder);
            if (savedModuleFinder != finder)
                limitedFinder = new SafeModuleFinder(finder);
        }

        // total time to initialize
        Counters.add("jdk.module.boot.totalTime", t0);
        Counters.publish();

        return bootLayer;
    }

    /**
     * Load/register the modules to the built-in class loaders.
     */
    private static void loadModules(Configuration cf,
                                    Function<String, ClassLoader> clf) {
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();
            String name = resolvedModule.name();
            ClassLoader loader = clf.apply(name);
            if (loader == null) {
                // skip java.base as it is already loaded
                if (!name.equals(JAVA_BASE)) {
                    BootLoader.loadModule(mref);
                }
            } else if (loader instanceof BuiltinClassLoader) {
                ((BuiltinClassLoader) loader).loadModule(mref);
            }
        }
    }

    /**
     * Checks for split packages between modules defined to the built-in class
     * loaders.
     */
    private static void checkSplitPackages(Configuration cf,
                                           Function<String, ClassLoader> clf) {
        Map<String, String> packageToModule = new HashMap<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            String name = descriptor.name();
            ClassLoader loader = clf.apply(name);
            if (loader == null || loader instanceof BuiltinClassLoader) {
                for (String p : descriptor.packages()) {
                    String other = packageToModule.putIfAbsent(p, name);
                    if (other != null) {
                        String msg = "Package " + p + " in both module "
                                     + name + " and module " + other;
                        throw new LayerInstantiationException(msg);
                    }
                }
            }
        }
    }

    /**
     * Returns a ModuleFinder that limits observability to the given root
     * modules, their transitive dependences, plus a set of other modules.
     */
    private static ModuleFinder limitFinder(ModuleFinder finder,
                                            Set<String> roots,
                                            Set<String> otherMods)
    {
        // resolve all root modules
        Configuration cf = Configuration.empty().resolve(finder,
                                                         ModuleFinder.of(),
                                                         roots);

        // module name -> reference
        Map<String, ModuleReference> map = new HashMap<>();

        // root modules and their transitive dependences
        cf.modules().stream()
            .map(ResolvedModule::reference)
            .forEach(mref -> map.put(mref.descriptor().name(), mref));

        // additional modules
        otherMods.stream()
            .map(finder::find)
            .flatMap(Optional::stream)
            .forEach(mref -> map.putIfAbsent(mref.descriptor().name(), mref));

        // set of modules that are observable
        Set<ModuleReference> mrefs = new HashSet<>(map.values());

        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(map.get(name));
            }
            @Override
            public Set<ModuleReference> findAll() {
                return mrefs;
            }
        };
    }

    /**
     * Creates a finder from the module path that is the value of the given
     * system property and optionally patched by --patch-module
     */
    private static ModuleFinder finderFor(String prop) {
        String s = System.getProperty(prop);
        if (s == null) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir: dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModulePath.of(patcher, paths);
        }
    }

    /**
     * Initialize the module patcher for the initial configuration passed on the
     * value of the --patch-module options.
     */
    private static ModulePatcher initModulePatcher() {
        Map<String, List<String>> map = decode("jdk.module.patch.",
                                               File.pathSeparator,
                                               false);
        return new ModulePatcher(map);
    }

    /**
     * Returns the set of module names specified by --add-module options.
     */
    private static Set<String> addModules() {
        String prefix = "jdk.module.addmods.";
        int index = 0;
        // the system property is removed after decoding
        String value = getAndRemoveProperty(prefix + index);
        if (value == null) {
            return Collections.emptySet();
        } else {
            Set<String> modules = new HashSet<>();
            while (value != null) {
                for (String s : value.split(",")) {
                    if (s.length() > 0) modules.add(s);
                }
                index++;
                value = getAndRemoveProperty(prefix + index);
            }
            return modules;
        }
    }

    /**
     * Returns the set of module names specified by --limit-modules.
     */
    private static Set<String> limitModules() {
        String value = getAndRemoveProperty("jdk.module.limitmods");
        if (value == null) {
            return Collections.emptySet();
        } else {
            Set<String> names = new HashSet<>();
            for (String name : value.split(",")) {
                if (name.length() > 0) names.add(name);
            }
            return names;
        }
    }

    /**
     * Process the --add-reads options to add any additional read edges that
     * are specified on the command-line.
     */
    private static void addExtraReads(ModuleLayer bootLayer) {

        // decode the command line options
        Map<String, List<String>> map = decode("jdk.module.addreads.");
        if (map.isEmpty())
            return;

        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            // the key is $MODULE
            String mn = e.getKey();
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent()) {
                warnUnknownModule(ADD_READS, mn);
                continue;
            }
            Module m = om.get();

            // the value is the set of other modules (by name)
            for (String name : e.getValue()) {
                if (ALL_UNNAMED.equals(name)) {
                    Modules.addReadsAllUnnamed(m);
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        Modules.addReads(m, om.get());
                    } else {
                        warnUnknownModule(ADD_READS, name);
                    }
                }
            }
        }
    }

    /**
     * Process the --add-exports and --add-opens options to export/open
     * additional packages specified on the command-line.
     */
    private static boolean addExtraExportsAndOpens(ModuleLayer bootLayer) {
        boolean extraExportsOrOpens = false;

        // --add-exports
        String prefix = "jdk.module.addexports.";
        Map<String, List<String>> extraExports = decode(prefix);
        if (!extraExports.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraExports, false);
            extraExportsOrOpens = true;
        }


        // --add-opens
        prefix = "jdk.module.addopens.";
        Map<String, List<String>> extraOpens = decode(prefix);
        if (!extraOpens.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraOpens, true);
            extraExportsOrOpens = true;
        }

        return extraExportsOrOpens;
    }

    private static void addExtraExportsOrOpens(ModuleLayer bootLayer,
                                               Map<String, List<String>> map,
                                               boolean opens)
    {
        String option = opens ? ADD_OPENS : ADD_EXPORTS;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            // the key is $MODULE/$PACKAGE
            String key = e.getKey();
            String[] s = key.split("/");
            if (s.length != 2)
                fail(unableToParse(option, "<module>/<package>", key));

            String mn = s[0];
            String pn = s[1];
            if (mn.isEmpty() || pn.isEmpty())
                fail(unableToParse(option, "<module>/<package>", key));

            // The exporting module is in the boot layer
            Module m;
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent()) {
                warnUnknownModule(option, mn);
                continue;
            }

            m = om.get();

            if (!m.getDescriptor().packages().contains(pn)) {
                warn("package " + pn + " not in " + mn);
                continue;
            }

            // the value is the set of modules to export to (by name)
            for (String name : e.getValue()) {
                boolean allUnnamed = false;
                Module other = null;
                if (ALL_UNNAMED.equals(name)) {
                    allUnnamed = true;
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        other = om.get();
                    } else {
                        warnUnknownModule(option, name);
                        continue;
                    }
                }
                if (allUnnamed) {
                    if (opens) {
                        Modules.addOpensToAllUnnamed(m, pn);
                    } else {
                        Modules.addExportsToAllUnnamed(m, pn);
                    }
                } else {
                    if (opens) {
                        Modules.addOpens(m, pn, other);
                    } else {
                        Modules.addExports(m, pn, other);
                    }
                }

            }
        }
    }

    /**
     * Process the --illegal-access option (and its default) to open packages
     * of system modules in the boot layer to code in unnamed modules.
     */
    private static void addIllegalAccess(ModuleFinder upgradeModulePath,
                                         SystemModules systemModules,
                                         ModuleLayer bootLayer,
                                         boolean extraExportsOrOpens) {
        String value = getAndRemoveProperty("jdk.module.illegalAccess");
        IllegalAccessLogger.Mode mode = IllegalAccessLogger.Mode.ONESHOT;
        if (value != null) {
            switch (value) {
                case "deny":
                    return;
                case "permit":
                    break;
                case "warn":
                    mode = IllegalAccessLogger.Mode.WARN;
                    break;
                case "debug":
                    mode = IllegalAccessLogger.Mode.DEBUG;
                    break;
                default:
                    fail("Value specified to --illegal-access not recognized:"
                            + " '" + value + "'");
                    return;
            }
        }
        IllegalAccessLogger.Builder builder
            = new IllegalAccessLogger.Builder(mode, System.err);

        Map<String, Set<String>> map1 = systemModules.concealedPackagesToOpen();
        Map<String, Set<String>> map2 = systemModules.exportedPackagesToOpen();
        if (map1.isEmpty() && map2.isEmpty()) {
            // need to generate (exploded build)
            IllegalAccessMaps maps = IllegalAccessMaps.generate(limitedFinder());
            map1 = maps.concealedPackagesToOpen();
            map2 = maps.exportedPackagesToOpen();
        }

        // open specific packages in the system modules
        for (Module m : bootLayer.modules()) {
            ModuleDescriptor descriptor = m.getDescriptor();
            String name = m.getName();

            // skip open modules
            if (descriptor.isOpen()) {
                continue;
            }

            // skip modules loaded from the upgrade module path
            if (upgradeModulePath != null
                && upgradeModulePath.find(name).isPresent()) {
                continue;
            }

            Set<String> concealedPackages = map1.getOrDefault(name, Set.of());
            Set<String> exportedPackages = map2.getOrDefault(name, Set.of());

            // refresh the set of concealed and exported packages if needed
            if (extraExportsOrOpens) {
                concealedPackages = new HashSet<>(concealedPackages);
                exportedPackages = new HashSet<>(exportedPackages);
                Iterator<String> iterator = concealedPackages.iterator();
                while (iterator.hasNext()) {
                    String pn = iterator.next();
                    if (m.isExported(pn, BootLoader.getUnnamedModule())) {
                        // concealed package is exported to ALL-UNNAMED
                        iterator.remove();
                        exportedPackages.add(pn);
                    }
                }
                iterator = exportedPackages.iterator();
                while (iterator.hasNext()) {
                    String pn = iterator.next();
                    if (m.isOpen(pn, BootLoader.getUnnamedModule())) {
                        // exported package is opened to ALL-UNNAMED
                        iterator.remove();
                    }
                }
            }

            // log reflective access to all types in concealed packages
            builder.logAccessToConcealedPackages(m, concealedPackages);

            // log reflective access to non-public members/types in exported packages
            builder.logAccessToExportedPackages(m, exportedPackages);

            // open the packages to unnamed modules
            JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
            jla.addOpensToAllUnnamed(m, concat(concealedPackages.iterator(),
                                               exportedPackages.iterator()));
        }

        builder.complete();
    }

    /**
     * Decodes the values of --add-reads, -add-exports, --add-opens or
     * --patch-modules options that are encoded in system properties.
     *
     * @param prefix the system property prefix
     * @praam regex the regex for splitting the RHS of the option value
     */
    private static Map<String, List<String>> decode(String prefix,
                                                    String regex,
                                                    boolean allowDuplicates) {
        int index = 0;
        // the system property is removed after decoding
        String value = getAndRemoveProperty(prefix + index);
        if (value == null)
            return Collections.emptyMap();

        Map<String, List<String>> map = new HashMap<>();

        while (value != null) {

            int pos = value.indexOf('=');
            if (pos == -1)
                fail(unableToParse(option(prefix), "<module>=<value>", value));
            if (pos == 0)
                fail(unableToParse(option(prefix), "<module>=<value>", value));

            // key is <module> or <module>/<package>
            String key = value.substring(0, pos);

            String rhs = value.substring(pos+1);
            if (rhs.isEmpty())
                fail(unableToParse(option(prefix), "<module>=<value>", value));

            // value is <module>(,<module>)* or <file>(<pathsep><file>)*
            if (!allowDuplicates && map.containsKey(key))
                fail(key + " specified more than once to " + option(prefix));
            List<String> values = map.computeIfAbsent(key, k -> new ArrayList<>());
            int ntargets = 0;
            for (String s : rhs.split(regex)) {
                if (s.length() > 0) {
                    values.add(s);
                    ntargets++;
                }
            }
            if (ntargets == 0)
                fail("Target must be specified: " + option(prefix) + " " + value);

            index++;
            value = getAndRemoveProperty(prefix + index);
        }

        return map;
    }

    /**
     * Decodes the values of --add-reads, -add-exports or --add-opens
     * which use the "," to separate the RHS of the option value.
     */
    private static Map<String, List<String>> decode(String prefix) {
        return decode(prefix, ",", true);
    }

    /**
     * Gets and remove the named system property
     */
    private static String getAndRemoveProperty(String key) {
        return (String)System.getProperties().remove(key);
    }

    /**
     * Checks incubating status of modules in the configuration
     */
    private static void checkIncubatingStatus(Configuration cf) {
        String incubating = null;
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();

            // emit warning if the WARN_INCUBATING module resolution bit set
            if (ModuleResolution.hasIncubatingWarning(mref)) {
                String mn = mref.descriptor().name();
                if (incubating == null) {
                    incubating = mn;
                } else {
                    incubating += ", " + mn;
                }
            }
        }
        if (incubating != null)
            warn("Using incubator modules: " + incubating);
    }

    /**
     * Throws a RuntimeException with the given message
     */
    static void fail(String m) {
        throw new RuntimeException(m);
    }

    static void warn(String m) {
        System.err.println("WARNING: " + m);
    }

    static void warnUnknownModule(String option, String mn) {
        warn("Unknown module: " + mn + " specified to " + option);
    }

    static String unableToParse(String option, String text, String value) {
        return "Unable to parse " +  option + " " + text + ": " + value;
    }

    private static final String ADD_MODULES  = "--add-modules";
    private static final String ADD_EXPORTS  = "--add-exports";
    private static final String ADD_OPENS    = "--add-opens";
    private static final String ADD_READS    = "--add-reads";
    private static final String PATCH_MODULE = "--patch-module";


    /*
     * Returns the command-line option name corresponds to the specified
     * system property prefix.
     */
    static String option(String prefix) {
        switch (prefix) {
            case "jdk.module.addexports.":
                return ADD_EXPORTS;
            case "jdk.module.addopens.":
                return ADD_OPENS;
            case "jdk.module.addreads.":
                return ADD_READS;
            case "jdk.module.patch.":
                return PATCH_MODULE;
            case "jdk.module.addmods.":
                return ADD_MODULES;
            default:
                throw new IllegalArgumentException(prefix);
        }
    }

    /**
     * Returns an iterator that yields all elements of the first iterator
     * followed by all the elements of the second iterator.
     */
    static <T> Iterator<T> concat(Iterator<T> iterator1, Iterator<T> iterator2) {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return iterator1.hasNext() || iterator2.hasNext();
            }
            @Override
            public T next() {
                if (iterator1.hasNext()) return iterator1.next();
                if (iterator2.hasNext()) return iterator2.next();
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * Wraps a (potentially not thread safe) ModuleFinder created during startup
     * for use after startup.
     */
    static class SafeModuleFinder implements ModuleFinder {
        private final Set<ModuleReference> mrefs;
        private volatile Map<String, ModuleReference> nameToModule;

        SafeModuleFinder(ModuleFinder finder) {
            this.mrefs = Collections.unmodifiableSet(finder.findAll());
        }
        @Override
        public Optional<ModuleReference> find(String name) {
            Objects.requireNonNull(name);
            Map<String, ModuleReference> nameToModule = this.nameToModule;
            if (nameToModule == null) {
                this.nameToModule = nameToModule = mrefs.stream()
                        .collect(Collectors.toMap(m -> m.descriptor().name(),
                                                  Function.identity()));
            }
            return Optional.ofNullable(nameToModule.get(name));
        }
        @Override
        public Set<ModuleReference> findAll() {
            return mrefs;
        }
    }

    /**
     * Counters for startup performance analysis.
     */
    static class Counters {
        private static final boolean PUBLISH_COUNTERS;
        private static final boolean PRINT_COUNTERS;
        private static Map<String, Long> counters;
        static {
            String s = System.getProperty("jdk.module.boot.usePerfData");
            if (s == null) {
                PUBLISH_COUNTERS = false;
                PRINT_COUNTERS = false;
            } else {
                PUBLISH_COUNTERS = true;
                PRINT_COUNTERS = s.equals("debug");
                counters = new LinkedHashMap<>();  // preserve insert order
            }
        }

        /**
         * Add a counter
         */
        static void add(String name, long start) {
            if (PUBLISH_COUNTERS || PRINT_COUNTERS) {
                counters.put(name, (System.nanoTime() - start));
            }
        }

        /**
         * Publish the counters to the instrumentation buffer or stdout.
         */
        static void publish() {
            if (PUBLISH_COUNTERS || PRINT_COUNTERS) {
                for (Map.Entry<String, Long> e : counters.entrySet()) {
                    String name = e.getKey();
                    long value = e.getValue();
                    if (PUBLISH_COUNTERS)
                        PerfCounter.newPerfCounter(name).set(value);
                    if (PRINT_COUNTERS)
                        System.out.println(name + " = " + value);
                }
            }
        }
    }
}
