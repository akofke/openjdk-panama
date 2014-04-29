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

import java.io.*;


/**
 * Runs javadoc and then runs regression tests on the resulting output.
 * This class currently contains three tests:
 * <ul>
 * <li> String search: Reads each file, complete with newlines,
 *      into a string.  Lets you search for strings that contain
 *      newlines.  String matching is case-sensitive.
 *      You can run javadoc multiple times with different arguments,
 *      generating output into different destination directories, and
 *      then perform a different array of tests on each one.
 *      To do this, the run method accepts a test array for testing
 *      that a string is found, and a negated test array for testing
 *      that a string is not found.
 * <li> Run diffs: Iterate through the list of given file pairs
 *      and diff the pairs.
 * <li> Check exit code: Check the exit code of Javadoc and
 *      record whether the test passed or failed.
 * </ul>
 *
 * @author Doug Kramer
 * @author Jamie Ho
 * @since 1.4.2
 */
public abstract class JavadocTester {

    protected static final String NL = System.getProperty("line.separator");
    protected static final String FS = System.getProperty("file.separator");

    protected static final String SRC_DIR = System.getProperty("test.src", ".");
    protected static final String JAVA_VERSION = System.getProperty("java.version");
    protected static final String OUTPUT_DIR = "out";
    protected static final String[][] NO_TEST = new String[][] {};
    protected static final String[] NO_FILE_TEST = new String[] {};

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the error output.
     */
    public static final String ERROR_OUTPUT = "ERROR_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the notice output.
     */
    public static final String NOTICE_OUTPUT = "NOTICE_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the warning output.
     */
    public static final String WARNING_OUTPUT = "WARNING_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in standard output.
     */
    public static final String STANDARD_OUTPUT = "STANDARD_OUTPUT";

    /**
     * The default doclet.
     */
    public static final String DEFAULT_DOCLET_CLASS = "com.sun.tools.doclets.formats.html.HtmlDoclet";
    public static final String DEFAULT_DOCLET_CLASS_OLD = "com.sun.tools.doclets.standard.Standard";

    /**
     * The writer to write error messages.
     */
    public StringWriter errors;

    /**
     * The writer to write notices.
     */
    public StringWriter notices;

    /**
     * The writer to write warnings.
     */
    public StringWriter warnings;

    /**
     * The buffer of warning output.
     */
    public StringBuffer standardOut;

    /**
     * The output directory.
     */
    private File outputDir;

    /**
     * The current subtest number.
     */
    private static int numTestsRun = 0;

    /**
     * The number of subtests passed.
     */
    private static int numTestsPassed = 0;

    /**
     * The current run of javadoc
     */
    private static int javadocRunNum = 0;

    /**
     * Construct a JavadocTester.
     */
    public JavadocTester() {
    }

    /**
     * Execute the tests.
     *
     * @param args             the arguments to pass to Javadoc
     * @param testArray        the array of tests
     * @param negatedTestArray the array of negated tests
     * @return                 the return code for the execution of Javadoc
     */
    public int run(String[] args,
            String[][] testArray, String[][] negatedTestArray) {
        int returnCode = runJavadoc(args);
        runTestsOnHTML(testArray, negatedTestArray);
        return returnCode;
    }

    /**
     * Execute the tests.
     *
     * @param args                 the arguments to pass to Javadoc
     * @param testArray            the array of tests
     * @param negatedTestArray     the array of negated tests
     * @param fileTestArray        the array of file tests
     * @param negatedFileTestArray the array of negated file tests
     * @return                     the return code for the execution of Javadoc
     */
    public int run(String[] args,
            String[][] testArray, String[][] negatedTestArray,
            String[] fileTestArray, String[] negatedFileTestArray) {
        int returnCode = runJavadoc(args);
        runTestsOnHTML(testArray, negatedTestArray);
        runTestsOnFile(fileTestArray, negatedFileTestArray);
        return returnCode;
    }

    /**
     * Execute Javadoc using the default doclet.
     *
     * @param args  the arguments to pass to Javadoc
     * @return      the return code from the execution of Javadoc
     */
    public int runJavadoc(String[] args) {
        float javaVersion = Float.parseFloat(JAVA_VERSION.substring(0,3));
        String docletClass = javaVersion < 1.5 ?
            DEFAULT_DOCLET_CLASS_OLD : DEFAULT_DOCLET_CLASS;
        return runJavadoc(docletClass, args);
    }


    /**
     * Execute Javadoc.
     *
     * @param docletClass the doclet being tested.
     * @param args  the arguments to pass to Javadoc
     * @return      the return code from the execution of Javadoc
     */
    public int runJavadoc(String docletClass, String[] args) {
        javadocRunNum++;
        if (javadocRunNum == 1) {
            System.out.println("\n" + "Running javadoc...");
        } else {
            System.out.println("\n" + "Running javadoc (run "
                                    + javadocRunNum + ")...");
        }
        initOutputBuffers();
        outputDir = new File(".");
        for (int i = 0; i < args.length - 2; i++) {
            if (args[i].equals("-d")) {
                outputDir = new File(args[++i]);
                break;
            }
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream prevOut = System.out;
        System.setOut(new PrintStream(stdout));

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream prevErr = System.err;
        System.setErr(new PrintStream(stderr));

        int returnCode = com.sun.tools.javadoc.Main.execute(
                "javadoc",
                new PrintWriter(errors, true),
                new PrintWriter(warnings, true),
                new PrintWriter(notices, true),
                docletClass,
                getClass().getClassLoader(),
                args);
        System.setOut(prevOut);
        standardOut = new StringBuffer(stdout.toString());
        System.setErr(prevErr);
        errors.write(NL + stderr.toString());

        printJavadocOutput();
        return returnCode;
    }

    /**
     * Create new string writer buffers
     */
    private void initOutputBuffers() {
        errors   = new StringWriter();
        notices  = new StringWriter();
        warnings = new StringWriter();
    }

    /**
     * Run array of tests on the resulting HTML.
     * This method accepts a testArray for testing that a string is found
     * and a negatedTestArray for testing that a string is not found.
     *
     * @param testArray         the array of tests
     * @param negatedTestArray  the array of negated tests
     */
    public void runTestsOnHTML(String[][] testArray, String[][] negatedTestArray) {
        runTestsOnHTML(testArray, false);
        runTestsOnHTML(negatedTestArray, true);
    }

    /**
     * Run array of tests on the generated files.
     * This method accepts a fileTestArray for testing if a file is generated
     * and a negatedFileTestArray for testing if a file is not found.
     * The files are relative to the most recent output directory specified
     * with -d.
     *
     * @param fileTestArray         the array of file tests
     * @param negatedFileTestArray  the array of negated file tests
     */
    public void runTestsOnFile(String[] fileTestArray, String[] negatedFileTestArray) {
        runTestsOnFile(outputDir, fileTestArray, false);
        runTestsOnFile(outputDir, negatedFileTestArray, true);
    }

    /**
     * Run the array of tests on the resulting HTML.
     * The files are relative to the most recent output directory specified
     * with -d.
     *
     * @param testArray the array of tests
     * @param isNegated true if test is negated; false otherwise
     */
    private void runTestsOnHTML(String[][] testArray , boolean isNegated) {
        for (String[] test : testArray) {
            numTestsRun++;
            System.out.print("Running subtest #" + numTestsRun + "... ");
            // Get string to find
            String stringToFind = test[1];
            // Read contents of file into a string
            String fileString;
            try {
                fileString = readFileToString(outputDir, test[0]);
            } catch (Error e) {
                if (isNegated) {
                    System.out.println( "FAILED, due to " + e + "\n");
                    continue;
                }
                throw e;
            }
            // Find string in file's contents
            boolean isFound = findString(fileString, stringToFind);
            if ((isNegated && !isFound) || (!isNegated && isFound)) {
                numTestsPassed += 1;
                System.out.println("Passed" + "\n"
                        + (isNegated ? "not found:" : "found:") + "\n"
                        + stringToFind + " in " + test[0] + "\n");
            } else {
                System.out.println("FAILED, when searching for:" + "\n"
                        + stringToFind
                        + " in " + test[0] + "\n");
            }
        }
    }

    /**
     * Run the array of file tests on the generated files.
     *
     * @param testArray the array of file tests
     * @param isNegated true if test is negated; false otherwise
     */
    private void runTestsOnFile(File baseDir, String[] testArray, boolean isNegated) {
        for (String fileName : testArray) {
            numTestsRun++;
            String failedString = "FAILED: file (" + fileName + ") found" + "\n";
            String passedString = "Passed" + "\n" +
                    "file (" + fileName + ") not found" + "\n";
            System.out.print("Running subtest #" + numTestsRun + "... ");
            try {
                File file = new File(baseDir, fileName);
                if ((file.exists() && !isNegated) || (!file.exists() && isNegated)) {
                    numTestsPassed += 1;
                    System.out.println(passedString);
                } else {
                    System.out.println(failedString);
                }
            } catch (Error e) {
                System.err.println(e);
            }
        }
    }

    /**
     * Iterate through the list of given file pairs and diff each file.
     *
     * @param baseDir1 the directory containing the first set of files
     * @param baseDir2 the directory containing the second set of files
     * @param files the set of files to be compared
     * @throws Error if any differences are found between
     * file pairs.
     */
    public void runDiffs(String baseDir1, String baseDir2, String[] files) throws Error {
        runDiffs(baseDir1, baseDir2, files, true);
    }

    /**
     * Iterate through the list of given file pairs and diff each file.
     *
     * @param baseDir1 the directory containing the first set of files
     * @param baseDir2 the directory containing the second set of files
     * @param files the set of files to be compared
     * @param throwErrorIfNoMatch flag to indicate whether or not to throw
     * an error if the files do not match.
     *
     * @throws Error if any differences are found between
     * file pairs and throwErrorIfNoMatch is true.
     */
    public void runDiffs(String baseDir1, String baseDir2, String[] files, boolean throwErrorIfNoMatch) throws Error {
        File bd1 = new File(baseDir1);
        File bd2 = new File(baseDir2);
        for (String file : files) {
            diff(bd1, bd2, file, throwErrorIfNoMatch);
        }
    }

    /**
     * Check the exit code of Javadoc and record whether the test passed
     * or failed.
     *
     * @param expectedExitCode The exit code that is required for the test
     * to pass.
     * @param actualExitCode The actual exit code from the previous run of
     * Javadoc.
     */
    public void checkExitCode(int expectedExitCode, int actualExitCode) {
        numTestsRun++;
        if (expectedExitCode == actualExitCode) {
            System.out.println( "Passed" + "\n" + " got return code " +
                actualExitCode);
            numTestsPassed++;
        } else {
            System.out.println( "FAILED: expected return code " +
                expectedExitCode + " but got " + actualExitCode);
        }
    }

    /**
     * Print a summary of the test results.
     */
    protected void printSummary() {
        if ( numTestsRun != 0 && numTestsPassed == numTestsRun ) {
            // Test passed
            System.out.println("\n" + "All " + numTestsPassed
                                             + " subtests passed");
        } else {
            // Test failed
            throw new Error("\n" + (numTestsRun - numTestsPassed)
                                    + " of " + (numTestsRun)
                                    + " subtests failed\n");
        }
    }

    /**
     * Print the output stored in the buffers.
     */
    protected void printJavadocOutput() {
        System.out.println(STANDARD_OUTPUT + " : \n" + getStandardOutput());
        System.err.println(ERROR_OUTPUT + " : \n" + getErrorOutput());
        System.err.println(WARNING_OUTPUT + " : \n" + getWarningOutput());
        System.out.println(NOTICE_OUTPUT + " : \n" + getNoticeOutput());
    }

    /**
     * Read the file and return it as a string.
     *
     * @param fileName  the name of the file to read
     * @return          the file in string format
     */
    public String readFileToString(String fileName) throws Error {
        return readFileToString(outputDir, fileName);
    }

    /**
     * Read the file and return it as a string.
     *
     * @param baseDir   the directory in which to locate the file
     * @param fileName  the name of the file to read
     * @return          the file in string format
     */
    private String readFileToString(File baseDir, String fileName) throws Error {
        switch (fileName) {
            case ERROR_OUTPUT:
                return getErrorOutput();
            case NOTICE_OUTPUT:
                return getNoticeOutput();
            case WARNING_OUTPUT:
                return getWarningOutput();
            case STANDARD_OUTPUT:
                return getStandardOutput();
        }
        try {
            File file = new File(baseDir, fileName);
            if ( !file.exists() ) {
                System.out.println("\n" + "FILE DOES NOT EXIST: " + fileName);
            }
            char[] allChars;
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                // Create an array of characters the size of the file
                allChars = new char[(int)file.length()];
                // Read the characters into the allChars array
                in.read(allChars, 0, (int)file.length());
            }

            // Convert to a string
            String allCharsString = new String(allChars);
            return allCharsString;
        } catch (FileNotFoundException e) {
            System.err.println(e);
            throw new Error("File not found: " + fileName);
        } catch (IOException e) {
            System.err.println(e);
            throw new Error("Error reading file: " + fileName);
        }
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
    private boolean diff(File baseDir1, File baseDir2, String file,
            boolean throwErrorIFNoMatch) throws Error {
        String file1Contents = readFileToString(baseDir1, file);
        String file2Contents = readFileToString(baseDir2, file);
        numTestsRun++;
        if (file1Contents.trim().compareTo(file2Contents.trim()) == 0) {
            System.out.println("Diff successful: " + new File(baseDir1, file) + ", " + new File(baseDir2, file));
            numTestsPassed++;
            return true;
        } else if (throwErrorIFNoMatch) {
            throw new Error("Diff failed: " + new File(baseDir1, file) + ", " + new File(baseDir2, file));
        } else {
            return false;
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
        return fileString.contains(stringToFind.replace("\n", NL));
    }


    /**
     * Return the standard output.
     * @return the standard output
     */
    public String getStandardOutput() {
        return standardOut.toString();
    }

    /**
     * Return the error output.
     * @return the error output
     */
    public String getErrorOutput() {
        return errors.getBuffer().toString();
    }

    /**
     * Return the notice output.
     * @return the notice output
     */
    public String getNoticeOutput() {
        return notices.getBuffer().toString();
    }

    /**
     * Return the warning output.
     * @return the warning output
     */
    public String getWarningOutput() {
        return warnings.getBuffer().toString();
    }

    /**
     * A utility to copy a directory from one place to another.
     * We may possibly want to move this to our doclet toolkit in
     * the near future and maintain it from there.
     *
     * @param targetDir the directory to copy.
     * @param destDir the destination to copy the directory to.
     */
    public static void copyDir(String targetDir, String destDir) {
        if (targetDir.endsWith("SCCS")) {
            return;
        }
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
                    System.out.println("Copying " + srcFile + " to " + destFile);
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
     * @throws SecurityException
     * @throws IOException
     */
    public static void copyFile(File destfile, File srcfile)
        throws IOException {
        byte[] bytearr = new byte[512];
        int len;
        FileInputStream input = new FileInputStream(srcfile);
        File destDir = destfile.getParentFile();
        destDir.mkdirs();
        FileOutputStream output = new FileOutputStream(destfile);
        try {
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        } catch (FileNotFoundException | SecurityException exc) {
        } finally {
            input.close();
            output.close();
        }
    }
}
