/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Paths;
import jdk.test.lib.dcmd.*;
import jdk.test.lib.Platform;
import org.testng.annotations.Test;

public abstract class AttachFailedTestBase {

    public abstract void run(CommandExecutor executor);

    /**
     * Build path to shared object according to platform rules
     */
    public static String getSharedObjectPath(String name) {
        String libname;
        if (Platform.isWindows()) {
            libname = name + ".dll";
        } else if (Platform.isOSX()) {
            libname = "lib" + name + ".dylib";
        } else {
            libname = "lib" + name + ".so";
        }

        return Paths.get(System.getProperty("test.nativepath"), libname)
                    .toAbsolutePath()
                    .toString();
    }

    @Test
    public void jmx() throws Throwable {
        run(new JMXExecutor());
    }

    @Test
    public void cli() throws Throwable {
        run(new PidJcmdExecutor());
    }
}
