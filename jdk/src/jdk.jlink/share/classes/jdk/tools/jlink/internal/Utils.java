/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.lang.reflect.Module;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Plugin.Category;

public class Utils {

    private Utils() {}

    // jrt-fs file system
    private static FileSystem JRT_FILE_SYSTEM;

    // current module
    private static final Module THIS_MODULE = Utils.class.getModule();

    public static List<String> parseList(String arguments) {
        return Arrays.stream(arguments.split(","))
                     .map((p) -> p.trim())
                     .filter((p) -> !p.isEmpty())
                     .collect(Collectors.toList());
    }

    public static boolean isPostProcessor(Category category) {
        return category.equals(Category.VERIFIER)
                || category.equals(Category.PROCESSOR)
                || category.equals(Category.PACKAGER);
    }

    public static boolean isPreProcessor(Category category) {
        return category.equals(Category.COMPRESSOR)
                || category.equals(Category.FILTER)
                || category.equals(Category.MODULEINFO_TRANSFORMER)
                || category.equals(Category.SORTER)
                || category.equals(Category.TRANSFORMER)
                || category.equals(Category.METAINFO_ADDER);
    }

    public static boolean isPostProcessor(Plugin provider) {
        Set<Category> types = provider.getType();
        Objects.requireNonNull(types);
        for (Category pt : types) {
            return isPostProcessor(pt);
        }
        return false;
    }

    public static boolean isPreProcessor(Plugin provider) {
        Set<Category> types = provider.getType();
        Objects.requireNonNull(types);
        for (Category pt : types) {
            return isPreProcessor(pt);
        }
        return false;
    }

    public static Category getCategory(Plugin provider) {
        Set<Category> types = provider.getType();
        Objects.requireNonNull(types);
        for (Category t : types) {
            return t;
        }
        return null;
    }

    public static List<Plugin> getPreProcessors(List<Plugin> plugins) {
        List<Plugin> res = new ArrayList<>();
        for (Plugin p : plugins) {
            if (isPreProcessor(p)) {
                res.add(p);
            }
        }
        return res;
    }

    public static List<Plugin> getPostProcessors(List<Plugin> plugins) {
        List<Plugin> res = new ArrayList<>();
        for (Plugin p : plugins) {
            if (isPostProcessor(p)) {
                res.add(p);
            }
        }
        return res;
    }

    public static List<Plugin> getSortedPostProcessors(List<Plugin> plugins) {
        List<Plugin> res = getPostProcessors(plugins);
        res.sort(new Comparator<Plugin>() {
            @Override
            public int compare(Plugin o1, Plugin o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return res;
    }

    public static List<Plugin> getSortedPreProcessors(List<Plugin> plugins) {
        List<Plugin> res = getPreProcessors(plugins);
        res.sort(new Comparator<Plugin>() {
            @Override
            public int compare(Plugin o1, Plugin o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return res;
    }

    public static boolean isFunctional(Plugin prov) {
        return prov.getState().contains(Plugin.State.FUNCTIONAL);
    }

    public static boolean isAutoEnabled(Plugin prov) {
        return prov.getState().contains(Plugin.State.AUTO_ENABLED);
    }

    public static boolean isDisabled(Plugin prov) {
        return prov.getState().contains(Plugin.State.DISABLED);
    }

    // is this a builtin (jdk.jlink) plugin?
    public static boolean isBuiltin(Plugin prov) {
        return THIS_MODULE.equals(prov.getClass().getModule());
    }

    public static FileSystem jrtFileSystem() {
        if (JRT_FILE_SYSTEM == null) {
            JRT_FILE_SYSTEM = FileSystems.getFileSystem(URI.create("jrt:/"));
        }

        return JRT_FILE_SYSTEM;
    }

    public static PathMatcher getPathMatcher(FileSystem fs, String pattern) {
        if (!pattern.startsWith("glob:") && !pattern.startsWith("regex:")) {
            pattern = "glob:" + pattern;
        }

        return fs.getPathMatcher(pattern);
    }

    public static PathMatcher getJRTFSPathMatcher(String pattern) {
        return getPathMatcher(jrtFileSystem(), pattern);
    }

    public static Path getJRTFSPath(String first, String... more) {
        return jrtFileSystem().getPath(first, more);
    }
}
