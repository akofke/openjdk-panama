/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6191390
 * @summary Verify that ActionEvent is received with correct modifiers set.
 * @run main/manual ActionEventTest
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ActionEventTest extends Frame {

    MenuBar menuBar;
    TextArea instructions;
    public static boolean isProgInterruption = false;
    static Thread mainThread = null;
    static int sleepTime = 300000;

    public ActionEventTest() {
        menuBar = new MenuBar();
        Menu menu = new Menu("Menu1");
        MenuItem menuItem = new MenuItem("MenuItem");

        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                System.out.println("actionPerformed");
                int md = ae.getModifiers();
                int expectedMask = ActionEvent.ALT_MASK | ActionEvent.CTRL_MASK
                        | ActionEvent.SHIFT_MASK;

                isProgInterruption = true;
                mainThread.interrupt();
                if ((md & expectedMask) != expectedMask) {
                    throw new RuntimeException("Action Event modifiers are not"
                        + " set correctly.");
                }
            }
        });
        menu.add(menuItem);
        menuBar.add(menu);
        setMenuBar(menuBar);

        instructions = new TextArea(10, 50);
        instructions.setText(
        " This is a manual test\n" +
        " Keep the Alt, Shift & Ctrl Keys pressed while doing next steps\n" +
        " Click 'Menu1' Menu from the Menu Bar\n" +
        " It will show 'MenuItem'\n" +
        " Left mouse Click the 'MenuItem'\n" +
        " Test exits automatically after mouse click.");
        add(instructions);

        setSize(400, 400);
        setVisible(true);
        validate();
    }


    public static void main(final String[] args) throws Exception {
        mainThread = Thread.currentThread();
        ActionEventTest test = new ActionEventTest();
        try {
            mainThread.sleep(sleepTime);
        } catch (InterruptedException e) {
            if (!isProgInterruption) {
                throw e;
            }
        }
        test.dispose();
        if (!isProgInterruption) {
            throw new RuntimeException("Timed out after " + sleepTime / 1000
                    + " seconds");
        }
    }
}
