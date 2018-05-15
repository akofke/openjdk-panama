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

package jdk.management.jfr;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

final class StreamManager {

    public static final long TIME_OUT = TimeUnit.MINUTES.toMillis(2);
    public static final int DEFAULT_BLOCK_SIZE = 50000;

    private static long idCounter = 0;

    private final Map<Long, Stream> streams = new HashMap<>();
    private Timer timer;

    public synchronized Stream getStream(long streamIdentifer) {
        Stream stream = streams.get(streamIdentifer);
        if (stream == null) {
            throw new IllegalArgumentException("Unknown stream identifier " + streamIdentifer);
        }
        return stream;
    }

    public synchronized Stream create(InputStream is, int blockSize) {
        idCounter++;
        Stream stream = new Stream(is, idCounter, blockSize);
        streams.put(stream.getId(), stream);

        scheduleAbort(stream, System.currentTimeMillis() + TIME_OUT);
        return stream;
    }

    public synchronized void destroy(Stream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            // OK
        }
        streams.remove(stream.getId());
        if (streams.isEmpty()) {
            timer.cancel();
            timer = null;
        }
    }

    public synchronized void scheduleAbort(Stream s, long when) {
        if (timer == null) {
            timer = new Timer(true);
        }
        timer.schedule(new StreamCleanupTask(this, s), new Date(when + TIME_OUT));
    }
}
