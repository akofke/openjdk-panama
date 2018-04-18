/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @modules java.base/java.util.concurrent:open
 * @run testng WhiteBox
 * @summary White box tests of implementation details
 */

import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Test
public class WhiteBox {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final VarHandle TABLE, NEXTTABLE, SIZECTL;

    WhiteBox() throws ReflectiveOperationException {
        Class<?> mClass = ConcurrentHashMap.class;
        String nodeClassName = mClass.getName() + "$Node";
        Class<?> nodeClass = Class.forName(nodeClassName);
        Class<?> nodeArrayClass = Class.forName("[L" + nodeClassName + ";");
        MethodHandles.Lookup lookup
            = MethodHandles.privateLookupIn(mClass, MethodHandles.lookup());
        TABLE = lookup.findVarHandle(mClass, "table", nodeArrayClass);
        NEXTTABLE = lookup.findVarHandle(mClass, "nextTable", nodeArrayClass);
        SIZECTL = lookup.findVarHandle(mClass, "sizeCtl", int.class);
    }

    Object[] table(ConcurrentHashMap m) { return (Object[]) TABLE.getVolatile(m); }
    Object[] nextTable(ConcurrentHashMap m) { return (Object[]) NEXTTABLE.getVolatile(m); }
    int sizeCtl(ConcurrentHashMap m) { return (int) SIZECTL.getVolatile(m); }

    @Test
    public void defaultConstructor() {
        ConcurrentHashMap m = new ConcurrentHashMap();
        assertNull(table(m));
        assertEquals(sizeCtl(m), 0);
        assertResizeNotInProgress(m);
    }

    @Test
    public void shouldNotResizeWhenInitialCapacityProvided() {
        int initialCapacity = rnd.nextInt(1, 100);
        Object[] initialTable = null;
        ConcurrentHashMap m = new ConcurrentHashMap(initialCapacity);

        // table is lazily initialized
        assertNull(table(m));
        int expectedInitialTableLength = sizeCtl(m);

        assertInvariants(m);
        for (int i = 0; i < initialCapacity; i++) {
            m.put(i * 100 + rnd.nextInt(100), i);
            if (i == 0)
                initialTable = table(m);
            else
                assertSame(table(m), initialTable);
            assertInvariants(m);
        }
        assertEquals(initialTable.length, expectedInitialTableLength);
    }

    byte[] serialBytes(Object o) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            oos.close();
            return bos.toByteArray();
        } catch (Exception fail) {
            throw new AssertionError(fail);
        }
    }

    @SuppressWarnings("unchecked")
    <T> T serialClone(T o) {
        try {
            ObjectInputStream ois = new ObjectInputStream
                (new ByteArrayInputStream(serialBytes(o)));
            T clone = (T) ois.readObject();
            assertNotSame(o, clone);
            assertSame(o.getClass(), clone.getClass());
            return clone;
        } catch (Exception fail) {
            throw new AssertionError(fail);
        }
    }

    @Test
    public void testSerialization() {
        assertInvariants(serialClone(new ConcurrentHashMap()));

        ConcurrentHashMap m = new ConcurrentHashMap(rnd.nextInt(100));
        m.put(1, 1);
        ConcurrentHashMap clone = serialClone(m);
        assertInvariants(clone);
        assertNotSame(table(m), table(clone));
        assertEquals(m, clone);
        assertResizeNotInProgress(m);
        assertResizeNotInProgress(clone);
    }

    /** Checks conditions which should always be true. */
    void assertInvariants(ConcurrentHashMap m) {
        if (!m.isEmpty())
            assertNotNull(table(m));
    }

    void assertResizeNotInProgress(ConcurrentHashMap m) {
        assertTrue(sizeCtl(m) >= 0);
        assertNull(nextTable(m));
    }
}
