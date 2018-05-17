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

package jdk.jfr.internal;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class RequestEngine {

    private final static JVM jvm = JVM.getJVM();

    final static class RequestHook {
        private final Runnable hook;
        private final PlatformEventType type;
        private final AccessControlContext accessControllerContext;
        private long delta;

        // Java events
        private RequestHook(AccessControlContext acc, PlatformEventType eventType, Runnable hook) {
            this.hook = hook;
            this.type = eventType;
            this.accessControllerContext = acc;
        }

        // native events
        RequestHook(PlatformEventType eventType) {
            this(null, eventType, null);
        }

        private void execute() {
            try {
                if (accessControllerContext == null) { // native
                    jvm.emitEvent(type.getId(), JVM.counterTime(), 0);
                    Logger.log(LogTag.JFR_SYSTEM_EVENT, LogLevel.DEBUG, ()-> "Executed periodic hook for " + type.getLogName());
                } else {
                    executeSecure();
                }
            } catch (Throwable e) {
                // Prevent malicious user to propagate exception callback in the wrong context
                Logger.log(LogTag.JFR_SYSTEM_EVENT, LogLevel.WARN, "Exception occured during execution of period hook for " + type.getLogName());
            }
        }

        private void executeSecure() {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        hook.run();
                        Logger.log(LogTag.JFR_EVENT, LogLevel.DEBUG, ()-> "Executed periodic hook for " + type.getLogName());
                    } catch (Throwable t) {
                        // Prevent malicious user to propagate exception callback in the wrong context
                        Logger.log(LogTag.JFR_EVENT, LogLevel.WARN, "Exception occured during execution of period hook for " + type.getLogName());
                    }
                    return null;
                }
            }, accessControllerContext);
        }
    }

    private final static List<RequestHook> entries = new CopyOnWriteArrayList<>();
    private static long lastTimeMillis;

    // Insertion takes O(2*n), could be O(1) with HashMap, but
    // thinking is that CopyOnWriteArrayList is faster
    // to iterate over, which will happen more over time.
    public static void addHook(AccessControlContext acc, PlatformEventType type, Runnable hook) {
        Objects.requireNonNull(acc);
        RequestHook he = new RequestHook(acc, type, hook);
        for (RequestHook e : entries) {
            if (e.hook == hook) {
                throw new IllegalArgumentException("Hook has already been added");
            }
        }
        he.type.setEventHook(true);
        entries.add(he);
        logHook("Added", type);
    }


    private static void logHook(String action, PlatformEventType type) {
        if (type.isJDK() || type.isJVM()) {
            Logger.log(LogTag.JFR_SYSTEM_EVENT, LogLevel.INFO, action + " periodic hook for " + type.getLogName());
        } else {
            Logger.log(LogTag.JFR_EVENT, LogLevel.INFO, action + " periodic hook for " + type.getLogName());
        }
    }

    // Takes O(2*n), see addHook.
    public static boolean removeHook(Runnable hook) {
        for (RequestHook rh : entries) {
            if (rh.hook == hook) {
                entries.remove(rh);
                rh.type.setEventHook(false);
                logHook("Removed", rh.type);
                return true;
            }
        }
        return false;
    }

    // Only to be used for JVM events. No access control contest
    // or check if hook already exists
    static void addHooks(List<RequestHook> newEntries) {
        List<RequestHook> addEntries = new ArrayList<>();
        for (RequestHook rh : newEntries) {
            rh.type.setEventHook(true);
            addEntries.add(rh);
            logHook("Added", rh.type);
        }
        entries.addAll(newEntries);
    }

    static void doChunkEnd() {
        doChunk(x -> x.isEndChunk());
    }

    static void doChunkBegin() {
        doChunk(x -> x.isBeginChunk());
    }

    private static void doChunk(Predicate<PlatformEventType> predicate) {
        for (RequestHook requestHook : entries) {
            PlatformEventType s = requestHook.type;
            if (s.isEnabled() && predicate.test(s)) {
                requestHook.execute();
            }
        }
    }

    static long doPeriodic() {
        return run_requests(entries);
    }

    // code copied from native impl.
    private static long run_requests(Collection<RequestHook> entries) {
        long last = lastTimeMillis;
        // Bug 9000556 - current time millis has rather lame resolution
        // The use of os::elapsed_counter() is deliberate here, we don't
        // want it exchanged for os::ft_elapsed_counter().
        // Keeping direct call os::elapsed_counter() here for reliable
        // real time values in order to decide when registered requestable
        // events are due.
        long now = System.currentTimeMillis();
        long min = 0;
        long delta = 0;

        if (last == 0) {
            last = now;
        }

        // time from then to now
        delta = now - last;

        if (delta < 0) {
            // to handle time adjustments
            // for example Daylight Savings
            lastTimeMillis = now;
            return 0;
        }
        for (RequestHook he : entries) {
            long left = 0;
            PlatformEventType es = he.type;
            // Not enabled, skip.
            if (!es.isEnabled() || es.isEveryChunk()) {
                continue;
            }
            long r_period = es.getPeriod();
            long r_delta = he.delta;

            // add time elapsed.
            r_delta += delta;

            // above threshold?
            if (r_delta >= r_period) {
                // Bug 9000556 - don't try to compensate
                // for wait > period
                r_delta = 0;
                he.execute();
                ;
            }

            // calculate time left
            left = (r_period - r_delta);

            /**
             * nothing outside checks that a period is >= 0, so left can end up
             * negative here. ex. (r_period =(-1)) - (r_delta = 0) if it is,
             * handle it.
             */
            if (left < 0) {
                left = 0;
            }

            // assign delta back
            he.delta = r_delta;

            if (min == 0 || left < min) {
                min = left;
            }
        }
        lastTimeMillis = now;
        return min;
    }
}
