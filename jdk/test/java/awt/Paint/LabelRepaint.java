/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.peer.LabelPeer;

/**
 * @test
 * @bug 7090424
 * @author Sergey Bylokhov
 */
public final class LabelRepaint extends Label {

    public static void main(final String[] args) {
        for (int i = 0; i < 10; ++i) {
            final Frame frame = new Frame();
            frame.setSize(300, 300);
            frame.setLocationRelativeTo(null);
            LabelRepaint label = new LabelRepaint();
            frame.add(label);
            frame.setVisible(true);
            sleep();
            label.test();
            frame.dispose();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void paint(final Graphics g) {
        super.paint(g);
        if (!EventQueue.isDispatchThread()) {
            throw new RuntimeException("Wrong thread");
        }
        test();
    }

    void test() {
        setAlignment(getAlignment());
        ((LabelPeer) getPeer()).setAlignment(getAlignment());

        setText("");
        setText(null);
        setText(getText());
        ((LabelPeer) getPeer()).setText("");
        ((LabelPeer) getPeer()).setText(null);
        ((LabelPeer) getPeer()).setText(getText());

        setFont(null);
        setFont(getFont());
        getPeer().setFont(getFont());

        setBackground(null);
        setBackground(getBackground());
        getPeer().setBackground(getBackground());

        setForeground(null);
        setForeground(getForeground());
        getPeer().setForeground(getForeground());

        setEnabled(isEnabled());
        getPeer().setEnabled(isEnabled());
    }
}
