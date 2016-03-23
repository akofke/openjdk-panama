/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import com.apple.eawt.Application;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.Taskbar.Feature;
import java.awt.peer.TaskbarPeer;

final public class CTaskbarPeer implements TaskbarPeer {

    CTaskbarPeer() {}

    @Override
    public boolean isSupported(Feature feature) {
        switch(feature) {
            case ICON_BADGE_TEXT:
            case ICON_BADGE_NUMBER:
            case ICON_IMAGE:
            case MENU:
            case PROGRESS_VALUE:
            case USER_ATTENTION:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setProgressValue(int value) {
        Application.getApplication().setDockIconProgress(value);
    }

    @Override
    public void setIconBadge(String badge) {
        Application.getApplication().setDockIconBadge(badge);
    }

    @Override
    public Image getIconImage() {
        return Application.getApplication().getDockIconImage();
    }

    @Override
    public void setIconImage(Image image) {
        Application.getApplication().setDockIconImage(image);
    }

    @Override
    public PopupMenu getMenu() {
        return Application.getApplication().getDockMenu();
    }

    @Override
    public void setMenu(PopupMenu menu) {
        Application.getApplication().setDockMenu(menu);
    }

    @Override
    public void requestUserAttention(boolean enabled, boolean critical) {
        if (enabled) {
            Application.getApplication().requestUserAttention(critical);
        }
    }
}
