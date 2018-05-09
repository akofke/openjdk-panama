/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.Method.arguments;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * Method.                                                      <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.Method.arguments()</code>                  <BR>
 * complies with its spec if a debugged program is compiled with<BR>
 * "-g" option, hence no exception AbsentInformationException   <BR>
 * is expected.                                                 <BR>
 * <BR>
 * Cases for testing are as follows:                            <BR>
 *   - non-native method with arguments                         <BR>
 *   - non-native method without arguments                      <BR>
 *   - native method                                            <BR>
 */

public class arguments001 {

    //----------------------------------------------------- templete section
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    //----------------------------------------------------- templete parameters
    static final String
    sHeader1 = "\n==> nsk/jdi/Method/arguments/arguments001",
    sHeader2 = "--> arguments001: ",
    sHeader3 = "##> arguments001: ";

    //----------------------------------------------------- main method

    public static void main (String argv[]) {
        int result = run(argv, System.out);
        System.exit(result + PASS_BASE);
    }

    public static int run (String argv[], PrintStream out) {
        return new arguments001().runThis(argv, out);
    }

     //--------------------------------------------------   log procedures

    //private static boolean verbMode = false;

    private static Log  logHandler;

    private static void log1(String message) {
        logHandler.display(sHeader1 + message);
    }
    private static void log2(String message) {
        logHandler.display(sHeader2 + message);
    }
    private static void log3(String message) {
        logHandler.complain(sHeader3 + message);
    }

    //  ************************************************    test parameters

    private String debuggeeName =
        "nsk.jdi.Method.arguments.arguments001a";

    String mName = "nsk.jdi.Method.arguments";

    //====================================================== test program

    static ArgumentHandler      argsHandler;
    static int                  testExitCode = PASSED;

    //------------------------------------------------------ common section

    private int runThis (String argv[], PrintStream out) {

        Debugee debuggee;

        argsHandler     = new ArgumentHandler(argv);
        logHandler      = new Log(out, argsHandler);
        Binder binder   = new Binder(argsHandler, logHandler);

        if (argsHandler.verbose()) {
            debuggee = binder.bindToDebugee(debuggeeName + " -vbs");  // *** tp
        } else {
            debuggee = binder.bindToDebugee(debuggeeName);            // *** tp
        }

        IOPipe pipe     = new IOPipe(debuggee);

        debuggee.redirectStderr(out);
        log2("arguments001a debuggee launched");
        debuggee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        VirtualMachine vm = debuggee.VM();

    //------------------------------------------------------  testing section
        log1("      TESTING BEGINS");

        for (int i = 0; ; i++) {
        pipe.println("newcheck");
            line = pipe.readln();

            if (line.equals("checkend")) {
                log2("     : returned string is 'checkend'");
                break ;
            } else if (!line.equals("checkready")) {
                log3("ERROR: returned string is not 'checkready'");
                testExitCode = FAILED;
                break ;
            }

            log1("new check: #" + i);

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ variable part

            List listOfDebuggeeClasses = vm.classesByName(mName +
                                                              ".arguments001aTestClass");
            if (listOfDebuggeeClasses.size() != 1) {
                testExitCode = FAILED;
                log3("ERROR: listOfDebuggeeClasses.size() != 1");
                break ;
            }

            List   methods   = null;
            Method m         = null;
            List   argsList  = null;


            //  method with arguments

            methods = ((ReferenceType) listOfDebuggeeClasses.get(0)).
                           methodsByName("primitiveargsmethod");
            m = (Method) methods.get(0);

     check: {
            try {
                argsList = m.arguments();
                if (argsList.size() != 8) {
                    log3("ERROR: for method with arguments: argsList# != 8    :"
                             + argsList.size() );
                    testExitCode = FAILED;
                }
                try {
                    for (int i2 = 0; i2 < argsList.size(); i2++) {
                        LocalVariable lv = (LocalVariable) argsList.get(i2);
                        int n = 0;
                        String names[] = { "bl", "bt", "ch", "db",
                                           "fl", "in", "ln", "sh" };
                        String name = lv.name();

                        for (int i3 = 0; i3 < 8; i3++) {
                            if (names[i3].equals(name)) {
                                if (n == 1) {
                                    log3("ERROR: method with args: multiple arg names  "
                                          + name);
                                    testExitCode = FAILED;
                                }
                                n++;
                            }
                        }
                        if (n == 0) {
                            log3("ERROR: method with args: no name in list:  "
                                  + name);
                            testExitCode = FAILED;
                        }
                    }
                } catch ( ClassCastException e ) {
                    log3("ERROR: ClassCastException for lv = (LocalVariable) arglist.get(0);");
                    testExitCode = FAILED;
                }
            } catch ( AbsentInformationException e ) {
                    log3("ERROR: AbsentInformationException for method with arguments");
                    testExitCode = FAILED;
            }
            } //check:


            // method without arguments

            methods = ((ReferenceType) listOfDebuggeeClasses.get(0)).
                           methodsByName("vd");
            m = (Method) methods.get(0);

            try {
                argsList = m.arguments();
                if (argsList.size() != 0) {
                    log3("ERROR: for method without arguments: argsList# != 0    :"
                            + argsList.size() );
                    testExitCode = FAILED;
                }
            } catch ( AbsentInformationException e ) {
                log3("ERROR: AbsentInformationException for method without arguments");
                 testExitCode = FAILED;
            }


            // native method

            methods = ((ReferenceType) listOfDebuggeeClasses.get(0)).
                           methodsByName("nvd");
            m = (Method) methods.get(0);

            try {
                argsList = m.arguments();
                log3("ERROR: no AbsentInformationException for native method");
                testExitCode = FAILED;
            } catch ( AbsentInformationException e ) {
                log2("       AbsentInformationException for native method");
            }

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
        log1("      TESTING ENDS");

    //--------------------------------------------------   test summary section
    //-------------------------------------------------    standard end section

        pipe.println("quit");
        log2("waiting for the debuggee to finish ...");
        debuggee.waitFor();

        int status = debuggee.getStatus();
        if (status != PASSED + PASS_BASE) {
            log3("debuggee returned UNEXPECTED exit status: " +
                    status + " != PASS_BASE");
            testExitCode = FAILED;
        } else {
            log2("debuggee returned expected exit status: " +
                    status + " == PASS_BASE");
        }

        if (testExitCode != PASSED) {
            logHandler.complain("TEST FAILED");
        }
        return testExitCode;
    }
}
