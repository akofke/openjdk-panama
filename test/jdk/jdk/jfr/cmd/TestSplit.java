/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.cmd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @summary Test jfr split
 * @key jfr
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.cmd.TestSplit
 */
public class TestSplit {

    public static void main(String[] args) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String dateText = formatter.format(new Date());

        Path recordingFileA = Paths.get("many-chunks-A-" + dateText + ".jfr");
        Path recordingFileB = Paths.get("many-chunks-B-" + dateText + ".jfr");
        makeRecordingWithChunks(6, recordingFileA);
        Files.copy(recordingFileA, recordingFileB);

        String fileAText = recordingFileA.toAbsolutePath().toString();
        String fileBText = recordingFileB.toAbsolutePath().toString();

        OutputAnalyzer output = ExecuteHelper.run("split");
        output.shouldContain("Missing file");

        output = ExecuteHelper.run("split", "--wrongOption1", "..wrongOption2", "..wrongOption3", fileAText);
        output.shouldContain("Too many arguments");

        output = ExecuteHelper.run("split", "--wrongOption", fileAText);
        output.shouldContain("Unknown option");

        output = ExecuteHelper.run("split", "--wrongOption", "1", fileAText);
        output.shouldContain("Unknown option");

        output = ExecuteHelper.run("split", "--maxchunks", "-3", fileAText);
        output.shouldContain("Must be at least one chunk per file");

        output = ExecuteHelper.run("split", "--maxchunks", "1000", fileAText);
        output.shouldContain("Number of chunks in recording");
        output.shouldContain("doesn't exceed max chunks");
        output = ExecuteHelper.run("split", fileAText); // maxchunks is 5 by
                                                        // default
        System.out.println(output.getOutput());
        System.out.println(fileAText);
        verifyRecording(fileAText.substring(0, fileAText.length() - 4) + "_1.jfr");
        verifyRecording(fileAText.substring(0, fileAText.length() - 4) + "_2.jfr");

        output = ExecuteHelper.run("split", "--maxchunks", "2", fileBText);

        verifyRecording(fileBText.substring(0, fileBText.length() - 4) + "_1.jfr");
        verifyRecording(fileBText.substring(0, fileBText.length() - 4) + "_2.jfr");
        verifyRecording(fileBText.substring(0, fileBText.length() - 4) + "_3.jfr");

        output = ExecuteHelper.run("split", "--maxchunks", "2", fileBText);
        output.shouldContain("file with that name already exist");
    }

    private static void verifyRecording(String name) throws IOException {
        System.out.println("split name " + name);
        try (RecordingFile rf = new RecordingFile(Paths.get(name))) {
            rf.readEvent();
        }
    }

    // Will create at least 2 * count + 1 chunks.
    private static void makeRecordingWithChunks(int count, Path file) throws IOException, ParseException {
        Recording main = new Recording(Configuration.getConfiguration("default"));
        main.setToDisk(true);
        main.start();
        for (int i = 0; i < count; i++) {
            Recording r = new Recording();
            r.setToDisk(true);
            r.start();
            r.stop();
            r.close();
        }
        main.stop();
        main.dump(file);
        main.close();
    }
}
