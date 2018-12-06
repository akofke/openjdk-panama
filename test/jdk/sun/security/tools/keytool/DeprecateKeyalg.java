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

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @bug 8212003 8214179
 * @summary Deprecating the default keytool -keyalg option
 * @library /test/lib
 */

public class DeprecateKeyalg {

    private static final String COMMON = "-keystore ks -storetype jceks "
            + "-storepass changeit -keypass changeit";

    public static void main(String[] args) throws Throwable {

        kt("-genkeypair -keyalg DSA -alias a -dname CN=A")
                .shouldContain("Generating")
                .shouldNotContain("-keyalg option must be specified");

        kt("-genkeypair -alias b -dname CN=B")
                .shouldContain("Generating")
                .shouldContain("default key algorithm (DSA)")
                .shouldContain("-keyalg option must be specified");

        kt("-genseckey -keyalg DES -alias c")
                .shouldContain("Generated")
                .shouldNotContain("-keyalg option must be specified");

        kt("-genseckey -alias d")
                .shouldContain("Generated")
                .shouldContain("default key algorithm (DES)")
                .shouldContain("-keyalg option must be specified");

        kt("-genkeypair -alias e -dname CN=e -keyalg EC -groupname brainpoolP256r1")
                .shouldContain("Generating 256 bit EC (brainpoolP256r1) key pair");

        kt("-genkeypair -alias f -dname CN=f -keyalg EC")
                .shouldContain("Generating 256 bit EC (secp256r1) key pair");

        kt("-genkeypair -alias g -dname CN=g -keyalg EC -keysize 384")
                .shouldContain("Generating 384 bit EC (secp384r1) key pair");
    }

    private static OutputAnalyzer kt(String cmd) throws Throwable {
        return SecurityTools.keytool(COMMON + " " + cmd)
                .shouldHaveExitValue(0);
    }
}
