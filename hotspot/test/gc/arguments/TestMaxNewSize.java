/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestMaxNewSize
 * @key gc
 * @bug 7057939
 * @summary Make sure that MaxNewSize always has a useful value after argument
 * processing.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main TestMaxNewSize -XX:+UseSerialGC
 * @run main TestMaxNewSize -XX:+UseParallelGC
 * @run main TestMaxNewSize -XX:+UseConcMarkSweepGC
 * @run main TestMaxNewSize -XX:+UseG1GC
 * @author thomas.schatzl@oracle.com, jesper.wilhelmsson@oracle.com
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestMaxNewSize {

  private static void checkMaxNewSize(String[] flags, int heapsize) throws Exception {
    BigInteger actual = new BigInteger(getMaxNewSize(flags));
    System.out.println(actual);
    if (actual.compareTo(new BigInteger((new Long(heapsize)).toString())) == 1) {
      throw new RuntimeException("MaxNewSize value set to \"" + actual +
        "\", expected otherwise when running with the following flags: " + Arrays.asList(flags).toString());
    }
  }

  private static void checkIncompatibleNewSize(String[] flags) throws Exception {
    ArrayList<String> finalargs = new ArrayList<String>();
    finalargs.addAll(Arrays.asList(flags));
    finalargs.add("-version");

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(finalargs.toArray(new String[0]));
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldContain("Initial young gen size set larger than the maximum young gen size");
  }

  private static boolean isRunningG1(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].contains("+UseG1GC")) {
        return true;
      }
    }
    return false;
  }

  private static String getMaxNewSize(String[] flags) throws Exception {
    ArrayList<String> finalargs = new ArrayList<String>();
    finalargs.addAll(Arrays.asList(flags));
    if (isRunningG1(flags)) {
      finalargs.add("-XX:G1HeapRegionSize=1M");
    }
    finalargs.add("-XX:+PrintFlagsFinal");
    finalargs.add("-version");

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(finalargs.toArray(new String[0]));
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();
    //System.out.println(stdout);
    return getFlagValue("MaxNewSize", stdout);
  }

  private static String getFlagValue(String flag, String where) {
    Matcher m = Pattern.compile(flag + "\\s+:?=\\s+\\d+").matcher(where);
    if (!m.find()) {
      throw new RuntimeException("Could not find value for flag " + flag + " in output string");
    }
    String match = m.group();
    return match.substring(match.lastIndexOf(" ") + 1, match.length());
  }

  public static void main(String args[]) throws Exception {
    String gcName = args[0];
    final int M32 = 32 * 1024 * 1024;
    final int M64 = 64 * 1024 * 1024;
    final int M96 = 96 * 1024 * 1024;
    final int M128 = 128 * 1024 * 1024;
    checkMaxNewSize(new String[] { gcName, "-Xmx128M" }, M128);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:NewRatio=5" }, M128);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:NewSize=32M" }, M128);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:OldSize=96M" }, M128);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:MaxNewSize=32M" }, M32);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:NewSize=32M", "-XX:MaxNewSize=32M" }, M32);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-XX:NewRatio=6", "-XX:MaxNewSize=32M" }, M32);
    checkMaxNewSize(new String[] { gcName, "-Xmx128M", "-Xms96M" }, M128);
    checkMaxNewSize(new String[] { gcName, "-Xmx96M", "-Xms96M" }, M96);
    checkMaxNewSize(new String[] { gcName, "-XX:NewSize=128M", "-XX:MaxNewSize=50M"}, M128);
  }
}
