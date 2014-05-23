/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Test framework for running javadoc and performing tests on the resulting output.
 *
 * <p>
 * Tests are typically written as subtypes of JavadocTester, with a main
 * method that creates an instance of the test class and calls the runTests()
 * method. The runTests() methods calls all the test methods declared in the class,
 * and then calls a method to print a summary, and throw an exception if
 * any of the test methods reported a failure.
 *
 * <p>
 * Test methods are identified with a @Test annotation. They have no parameters.
 * The name of the method is not important, but if you have more than one, it is
 * recommended that the names be meaningful and suggestive of the test case
 * contained therein.
 *
 * <p>
 * Typically, a test method will invoke javadoc, and then perform various
 * checks on the results. The standard checks are:
 *
 * <dl>
 * <dt>checkExitCode
 * <dd>Check the exit code returned from javadoc.
 * <dt>checkOutput
 * <dd>Perform a series of checks on the contents on a file or output stream
 *     generated by javadoc.
 *     The checks can be either that a series of strings are found or are not found.
 * <dt>checkFiles
 * <dd>Perform a series of checks on the files generated by javadoc.
 *     The checks can be that a series of files are found or are not found.
 * </dl>
 *
 * <pre><code>
 *  public class MyTester extends JavadocTester {
 *      public static void main(String... args) throws Exception {
 *          MyTester tester = new MyTester();
 *          tester.runTests();
 *      }
 *
 *      // test methods...
 *      @Test
 *      void test() {
 *          javadoc(<i>args</i>);
 *          checkExit(Exit.OK);
 *          checkOutput(<i>file</i>, true,
 *              <i>strings-to-find</i>);
 *          checkOutput(<i>file</i>, false,
 *              <i>strings-to-not-find</i>);
 *      }
 *  }
 * </code></pre>
 *
 * <p>
 * If javadoc is run more than once in a test method, you can compare the
 * results that are generated with the diff method. Since files written by
 * javadoc typically contain a timestamp, you may want to use the -notimestamp
 * option if you are going to compare the results from two runs of javadoc.
 *
 * <p>
 * If you have many calls of checkOutput that are very similar, you can write
 * your own check... method to reduce the amount of duplication. For example,
 * if you want to check that many files contain the same string, you could
 * write a method that takes a varargs list of files and calls checkOutput
 * on each file in turn with the string to be checked.
 *
 * <p>
 * You can also write you own custom check methods, which can use
 * readFile to get the contents of a file generated by javadoc,
 * and then use pass(...) or fail(...) to report whether the check
 * succeeded or not.
 *
 * <p>
 * You can have many separate test methods, each identified with a @Test
 * annotation. However, you should <b>not</b> assume they will be called
 * in the order declared in your source file.  If the order of a series
 * of javadoc invocations is important, do that within a single method.
 * If the invocations are independent, for better clarity, use separate
 * test methods, each with their own set of checks on the results.
 *
 * @author Doug Kramer
 * @author Jamie Ho
 * @author Jonathan Gibbons (rewrite)
 */
public abstract class JavadocTester {

    public static final String FS = System.getProperty("file.separator");
    public static final String PS = System.getProperty("path.separator");
    public static final String NL = System.getProperty("line.separator");

    public enum Output {
        /** The name for error output from javadoc. */
        ERROR,
        /** The name for the notice output from javadoc. */
        NOTICE,
        /** The name for the warning output  from javadoc. */
        WARNING,
        /** The name for any output written to System.out. */
        STDOUT,
        /** The name for any output written to System.err. */
        STDERR
    }

    /** The output directory used in the most recent call of javadoc. */
    protected File outputDir;

    /** The exit code of the most recent call of javadoc. */
    private int exitCode;

    /** The output generated by javadoc to the various writers and streams. */
    private final Map<Output, String> outputMap = new EnumMap<>(Output.class);

    /** A cache of file content, to avoid reading files unnecessarily. */
    private final Map<File,SoftReference<String>> fileContentCache = new HashMap<>();

    /** Stream used for logging messages. */
    private final PrintStream out = System.out;

    /** The directory containing the source code for the test. */
    public static final String testSrc = System.getProperty("test.src");

    /**
     * Get the path for a source file in the test source directory.
     * @param path the path of a file or directory in the source directory
     * @return the full path of the specified file
     */
    public static String testSrc(String path) {
        return new File(testSrc, path).getPath();
    }

    /**
     * Alternatives for checking the contents of a directory.
     */
    public enum DirectoryCheck {
        /**
         * Check that the directory is empty.
         */
        EMPTY((file, name) -> true),
        /**
         * Check that the directory does not contain any HTML files,
         * such as may have been generated by a prior run of javadoc
         * using this directory.
         * For now, the check is only performed on the top level directory.
         */
        NO_HTML_FILES((file, name) -> name.endsWith(".html")),
        /**
         * No check is performed on the directory contents.
         */
        NONE(null) { @Override void check(File dir) { } };

        /** The filter used to detect that files should <i>not</i> be present. */
        FilenameFilter filter;

        DirectoryCheck(FilenameFilter f) {
            filter = f;
        }

        void check(File dir) {
            if (dir.isDirectory()) {
                String[] contents = dir.list(filter);
                if (contents == null)
                    throw new Error("cannot list directory: " + dir);
                if (contents.length > 0)
                    throw new Error("directory has unexpected content: " + dir);
            }
        }
    }

    private DirectoryCheck outputDirectoryCheck = DirectoryCheck.EMPTY;

    /** The current subtest number. Incremented when checking(...) is called. */
    private int numTestsRun = 0;

    /** The number of subtests passed. Incremented when passed(...) is called. */
    private int numTestsPassed = 0;

    /** The current run of javadoc. Incremented when javadoc is called. */
    private int javadocRunNum = 0;

    /** The name of the standard doclet. */
    // This ought not to be necessary; there ought to be a javadoc entry point
    // that does not require this to be know externally.
    private static final String standardDocletClassName =
            "com.sun.tools.doclets.standard.Standard";

    /** Marker annotation for test methods to be invoked by runTests. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    /**
     * Run all methods annotated with @Test, followed by printSummary.
     * Typically called on a tester object in main()
     * @throws Exception if any errors occurred
     */
    public void runTests() throws Exception {
        for (Method m: getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                try {
                    out.println("Running test " + m.getName());
                    m.invoke(this, new Object[] { });
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                out.println();
            }
        }
        printSummary();
    }

    /**
     * Run javadoc.
     * The output directory used by this call and the final exit code
     * will be saved for later use.
     * To aid the reader, it is recommended that calls to this method
     * put each option and the arguments it takes on a separate line.
     *
     * Example:
     * <pre><code>
     *  javadoc("-d", "out",
     *          "-sourcepath", testSrc,
     *          "-notimestamp",
     *          "pkg1", "pkg2", "pkg3/C.java");
     * </code></pre>
     *
     * @param args the arguments to pass to javadoc
     */
    public void javadoc(String... args) {
        outputMap.clear();
        fileContentCache.clear();

        javadocRunNum++;
        if (javadocRunNum == 1) {
            out.println("Running javadoc...");
        } else {
            out.println("Running javadoc (run "
                                    + javadocRunNum + ")...");
        }
        outputDir = new File(".");
        for (int i = 0; i < args.length - 2; i++) {
            if (args[i].equals("-d")) {
                outputDir = new File(args[++i]);
                break;
            }
        }
//        log.setOutDir(outputDir);

        outputDirectoryCheck.check(outputDir);

        // These are the primary streams used by javadoc
        WriterOutput errOut = new WriterOutput();
        WriterOutput warnOut = new WriterOutput();
        WriterOutput noticeOut = new WriterOutput();
        // These are to catch output to System.out and System.err,
        // in case these are used instead of the primary streams
        StreamOutput sysOut = new StreamOutput(System.out, System::setOut);
        StreamOutput sysErr = new StreamOutput(System.err, System::setErr);

        try {
            exitCode = com.sun.tools.javadoc.Main.execute(
                    "javadoc",
                    errOut.pw, warnOut.pw, noticeOut.pw,
                    standardDocletClassName,
                    args);
        } finally {
            outputMap.put(Output.STDOUT, sysOut.close());
            outputMap.put(Output.STDERR, sysErr.close());
            outputMap.put(Output.ERROR, errOut.close());
            outputMap.put(Output.WARNING, warnOut.close());
            outputMap.put(Output.NOTICE, noticeOut.close());
        }

        outputMap.forEach((name, text) -> {
            if (!text.isEmpty()) {
                out.println("javadoc " + name + ":");
                out.println(text);
            }
        });
    }

    /**
     * Set the kind of check for the initial contents of the output directory
     * before javadoc is run.
     * The filter should return true for files that should <b>not</b> appear.
     * @param c the kind of check to perform
     */
    public void setOutputDirectoryCheck(DirectoryCheck c) {
        outputDirectoryCheck = c;
    }

    public enum Exit {
        OK(0),
        FAILED(1);

        Exit(int code) {
            this.code = code;
        }

        final int code;
    }

    /**
     * Check the exit code of the most recent call of javadoc.
     *
     * @param expected the exit code that is required for the test
     * to pass.
     */
    public void checkExit(Exit expected) {
        checking("check exit code");
        if (exitCode == expected.code) {
            passed("return code " + exitCode);
        } else {
            failed("return code " + exitCode +"; expected " + expected.code + " (" + expected + ")");
        }
    }

    /**
     * Check for content in (or not in) the generated output.
     * Within the search strings, the newline character \n
     * will be translated to the platform newline character sequence.
     * @param path a path within the most recent output directory
     *  or the name of one of the output buffers, identifying
     *  where to look for the search strings.
     * @param expectedFound true if all of the search strings are expected
     *  to be found, or false if all of the strings are expected to be
     *  not found
     * @param strings the strings to be searched for
     */
    public void checkOutput(String path, boolean expectedFound, String... strings) {
        // Read contents of file
        String fileString;
        try {
            fileString = readFile(outputDir, path);
        } catch (Error e) {
            if (!expectedFound) {
                failed("Error reading file: " + e);
                return;
            }
            throw e;
        }
        checkOutput(path, fileString, expectedFound, strings);
    }

    /**
     * Check for content in (or not in) the one of the output streams written by
     * javadoc. Within the search strings, the newline character \n
     * will be translated to the platform newline character sequence.
     * @param output the output stream to check
     * @param expectedFound true if all of the search strings are expected
     *  to be found, or false if all of the strings are expected to be
     *  not found
     * @param strings the strings to be searched for
     */
    public void checkOutput(Output output, boolean expectedFound, String... strings) {
        checkOutput(output.toString(), outputMap.get(output), expectedFound, strings);
    }

    private void checkOutput(String path, String fileString, boolean expectedFound, String... strings) {
        for (String stringToFind : strings) {
//            log.logCheckOutput(path, expectedFound, stringToFind);
            checking("checkOutput");
            // Find string in file's contents
            boolean isFound = findString(fileString, stringToFind);
            if (isFound == expectedFound) {
                passed(path + ": " + (isFound ? "found:" : "not found:") + "\n"
                        + stringToFind + "\n");
            } else {
                failed(path + ": " + (isFound ? "found:" : "not found:") + "\n"
                        + stringToFind + "\n");
            }
        }
    }

    /**
     * Check for files in (or not in) the generated output.
     * @param expectedFound true if all of the files are expected
     *  to be found, or false if all of the files are expected to be
     *  not found
     * @param paths the files to check, within the most recent output directory.
     * */
    public void checkFiles(boolean expectedFound, String... paths) {
        for (String path: paths) {
//            log.logCheckFile(path, expectedFound);
            checking("checkFile");
            File file = new File(outputDir, path);
            boolean isFound = file.exists();
            if (isFound == expectedFound) {
                passed(path + ": " + (isFound ? "found:" : "not found:") + "\n");
            } else {
                failed(path + ": " + (isFound ? "found:" : "not found:") + "\n");
            }
        }
    }

    /**
     * Check that a series of strings are found in order in a file in
     * the generated output.
     * @param path the file to check
     * @param strings  the strings whose order to check
     */
    public void checkOrder(String path, String... strings) {
        String fileString = readOutputFile(path);
        int prevIndex = -1;
        for (String s : strings) {
            int currentIndex = fileString.indexOf(s);
            checking(s + " at index " + currentIndex);
            if (currentIndex >= prevIndex) {
                passed(s + "is in the correct order");
            } else {
                failed(s + " is in the wrong order.");
            }
            prevIndex = currentIndex;
        }
    }

    /**
     * Compare a set of files in each of two directories.
     *
     * @param baseDir1 the directory containing the first set of files
     * @param baseDir2 the directory containing the second set of files
     * @param files the set of files to be compared
     */
    public void diff(String baseDir1, String baseDir2, String... files) {
        File bd1 = new File(baseDir1);
        File bd2 = new File(baseDir2);
        for (String file : files) {
            diff(bd1, bd2, file);
        }
    }

    /**
     * A utility to copy a directory from one place to another.
     *
     * @param targetDir the directory to copy.
     * @param destDir the destination to copy the directory to.
     */
    // TODO: convert to using java.nio.Files.walkFileTree
    public void copyDir(String targetDir, String destDir) {
        try {
            File targetDirObj = new File(targetDir);
            File destDirParentObj = new File(destDir);
            File destDirObj = new File(destDirParentObj, targetDirObj.getName());
            if (! destDirParentObj.exists()) {
                destDirParentObj.mkdir();
            }
            if (! destDirObj.exists()) {
                destDirObj.mkdir();
            }
            String[] files = targetDirObj.list();
            for (String file : files) {
                File srcFile = new File(targetDirObj, file);
                File destFile = new File(destDirObj, file);
                if (srcFile.isFile()) {
                    out.println("Copying " + srcFile + " to " + destFile);
                    copyFile(destFile, srcFile);
                } else if(srcFile.isDirectory()) {
                    copyDir(srcFile.getAbsolutePath(), destDirObj.getAbsolutePath());
                }
            }
        } catch (IOException exc) {
            throw new Error("Could not copy " + targetDir + " to " + destDir);
        }
    }

    /**
     * Copy source file to destination file.
     *
     * @param destfile the destination file
     * @param srcfile the source file
     * @throws IOException
     */
    public void copyFile(File destfile, File srcfile) throws IOException {
        Files.copy(srcfile.toPath(), destfile.toPath());
    }

    /**
     * Read a file from the output directory.
     *
     * @param fileName  the name of the file to read
     * @return          the file in string format
     */
    public String readOutputFile(String fileName) throws Error {
        return readFile(outputDir, fileName);
    }

    protected String readFile(String fileName) throws Error {
        return readFile(outputDir, fileName);
    }

    protected String readFile(String baseDir, String fileName) throws Error {
        return readFile(new File(baseDir), fileName);
    }

    /**
     * Read the file and return it as a string.
     *
     * @param baseDir   the directory in which to locate the file
     * @param fileName  the name of the file to read
     * @return          the file in string format
     */
    private String readFile(File baseDir, String fileName) throws Error {
        try {
            File file = new File(baseDir, fileName);
            SoftReference<String> ref = fileContentCache.get(file);
            String content = (ref == null) ? null : ref.get();
            if (content != null)
                return content;

            content = new String(Files.readAllBytes(file.toPath()));
            fileContentCache.put(file, new SoftReference(content));
            return content;
        } catch (FileNotFoundException e) {
            System.err.println(e);
            throw new Error("File not found: " + fileName);
        } catch (IOException e) {
            System.err.println(e);
            throw new Error("Error reading file: " + fileName);
        }
    }

    protected void checking(String message) {
        numTestsRun++;
        print("Starting subtest " + numTestsRun, message);
    }

    protected void passed(String message) {
        numTestsPassed++;
        print("Passed", message);
    }

    protected void failed(String message) {
        print("FAILED", message);
    }

    private void print(String prefix, String message) {
        if (message.isEmpty())
            out.println(prefix);
        else {
            out.print(prefix);
            out.print(": ");
            out.println(message.replace("\n", NL));
        }
    }

    /**
     * Print a summary of the test results.
     */
    protected void printSummary() {
//        log.write();
        if (numTestsRun != 0 && numTestsPassed == numTestsRun) {
            // Test passed
            out.println();
            out.println("All " + numTestsPassed + " subtests passed");
        } else {
            // Test failed
            throw new Error((numTestsRun - numTestsPassed)
                    + " of " + (numTestsRun)
                    + " subtests failed");
        }
    }

    /**
     * Search for the string in the given file and return true
     * if the string was found.
     *
     * @param fileString    the contents of the file to search through
     * @param stringToFind  the string to search for
     * @return              true if the string was found
     */
    private boolean findString(String fileString, String stringToFind) {
        // javadoc (should) always use the platform newline sequence,
        // but in the strings to find it is more convenient to use the Java
        // newline character. So we translate \n to NL before we search.
        stringToFind = stringToFind.replace("\n", NL);
        return fileString.contains(stringToFind);
    }

    /**
     * Compare the two given files.
     *
     * @param baseDir1 the directory in which to locate the first file
     * @param baseDir2 the directory in which to locate the second file
     * @param file the file to compare in the two base directories
     * @param throwErrorIFNoMatch flag to indicate whether or not to throw
     * an error if the files do not match.
     * @return true if the files are the same and false otherwise.
     */
    private void diff(File baseDir1, File baseDir2, String file) {
        String file1Contents = readFile(baseDir1, file);
        String file2Contents = readFile(baseDir2, file);
        checking("diff " + new File(baseDir1, file) + ", " + new File(baseDir2, file));
        if (file1Contents.trim().compareTo(file2Contents.trim()) == 0) {
            passed("files are equal");
        } else {
            failed("files differ");
        }
    }

    /**
     * Utility class to simplify the handling of temporarily setting a
     * new stream for System.out or System.err.
     */
    private static class StreamOutput {
        // functional interface to set a stream.
        private interface Initializer {
            void set(PrintStream s);
        }

        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final PrintStream ps = new PrintStream(baos);
        private final PrintStream prev;
        private final Initializer init;

        StreamOutput(PrintStream s, Initializer init) {
            prev = s;
            init.set(ps);
            this.init = init;
        }

        String close() {
            init.set(prev);
            ps.close();
            return baos.toString();
        }
    }

    /**
     * Utility class to simplify the handling of creating an in-memory PrintWriter.
     */
    private static class WriterOutput {
        private final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        String close() {
            pw.close();
            return sw.toString();
        }
    }


//    private final Logger log = new Logger();

    //--------- Logging --------------------------------------------------------
    //
    // This class writes out the details of calls to checkOutput and checkFile
    // in a canonical way, so that the resulting file can be checked against
    // similar files from other versions of JavadocTester using the same logging
    // facilities.

    static class Logger {
        private static final int PREFIX = 40;
        private static final int SUFFIX = 20;
        private static final int MAX = PREFIX + SUFFIX;
        List<String> tests = new ArrayList<>();
        String outDir;
        String rootDir = rootDir();

        static String rootDir() {
            File f = new File(".").getAbsoluteFile();
            while (!new File(f, ".hg").exists())
                f = f.getParentFile();
            return f.getPath();
        }

        void setOutDir(File outDir) {
            this.outDir = outDir.getPath();
        }

        void logCheckFile(String file, boolean positive) {
            // Strip the outdir because that will typically not be the same
            if (file.startsWith(outDir + "/"))
                file = file.substring(outDir.length() + 1);
            tests.add(file + " " + positive);
        }

        void logCheckOutput(String file, boolean positive, String text) {
            // Compress the string to be displayed in the log file
            String simpleText = text.replaceAll("\\s+", " ").replace(rootDir, "[ROOT]");
            if (simpleText.length() > MAX)
                simpleText = simpleText.substring(0, PREFIX)
                        + "..." + simpleText.substring(simpleText.length() - SUFFIX);
            // Strip the outdir because that will typically not be the same
            if (file.startsWith(outDir + "/"))
                file = file.substring(outDir.length() + 1);
            // The use of text.hashCode ensure that all of "text" is taken into account
            tests.add(file + " " + positive + " " + text.hashCode() + " " + simpleText);
        }

        void write() {
            // sort the log entries because the subtests may not be executed in the same order
            tests.sort((a, b) -> a.compareTo(b));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("tester.log"))) {
                for (String t: tests) {
                    bw.write(t);
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new Error("problem writing log: " + e);
            }
        }
    }
}
