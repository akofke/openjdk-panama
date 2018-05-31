/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package nsk.jvmti.scenarios.hotswap.HS203.hs203t003;

import java.util.concurrent.atomic.AtomicBoolean;

public class MyThread extends Thread {
        public static AtomicBoolean resume = new AtomicBoolean(false);
        public int threadState=0;
    // field watch is added on.

        public MyThread() {
                System.out.println(" MyThread :: MyThread().");
        }

        public void run() {
                resume.set(true);
                doThisFunction();
                doTask2();
        }

        public void doThisFunction() {
                System.out.println(" MyThread.doThisFunction()");
                for(int i=0; i < 100; i++) {
                        threadState++;
                }
                return;
        }

        public void doTask2() {
                for (int i=0; i < 1000; i++) {
                        threadState++;
                }
                return;
        }
}
