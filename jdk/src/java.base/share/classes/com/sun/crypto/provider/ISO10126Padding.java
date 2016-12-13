/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import javax.crypto.ShortBufferException;

/**
 * This class implements padding as specified in the W3 XML ENC standard.
 *
 * @author Valerie Peng
 *
 *
 * @see Padding
 */
final class ISO10126Padding implements Padding {

    private int blockSize;

    ISO10126Padding(int blockSize) {
        this.blockSize = blockSize;
    }

    /**
     * Adds the given number of padding bytes to the data input.
     * The value of the padding bytes is determined
     * by the specific padding mechanism that implements this
     * interface.
     *
     * @param in the input buffer with the data to pad
     * @param off the offset in <code>in</code> where the padding bytes
     * are appended
     * @param len the number of padding bytes to add
     *
     * @exception ShortBufferException if <code>in</code> is too small to hold
     * the padding bytes
     */
    public void padWithLen(byte[] in, int off, int len)
        throws ShortBufferException
    {
        if (in == null)
            return;

        if ((off + len) > in.length) {
            throw new ShortBufferException("Buffer too small to hold padding");
        }

        byte paddingOctet = (byte) (len & 0xff);
        byte[] padding = new byte[len - 1];
        SunJCE.getRandom().nextBytes(padding);
        System.arraycopy(padding, 0, in, off, len - 1);
        in[off + len - 1] = paddingOctet;
        return;
    }

    /**
     * Returns the index where the padding starts.
     *
     * <p>Given a buffer with padded data, this method returns the
     * index where the padding starts.
     *
     * @param in the buffer with the padded data
     * @param off the offset in <code>in</code> where the padded data starts
     * @param len the length of the padded data
     *
     * @return the index where the padding starts, or -1 if the input is
     * not properly padded
     */
    public int unpad(byte[] in, int off, int len) {
        if ((in == null) ||
            (len == 0)) { // this can happen if input is really a padded buffer
            return 0;
        }

        byte lastByte = in[off + len - 1];
        int padValue = (int)lastByte & 0x0ff;
        if ((padValue < 0x01)
            || (padValue > blockSize)) {
            return -1;
        }

        int start = off + len - padValue;
        if (start < off) {
            return -1;
        }

        return start;
    }

    /**
     * Determines how long the padding will be for a given input length.
     *
     * @param len the length of the data to pad
     *
     * @return the length of the padding
     */
    public int padLength(int len) {
        int paddingOctet = blockSize - (len % blockSize);
        return paddingOctet;
    }
}
