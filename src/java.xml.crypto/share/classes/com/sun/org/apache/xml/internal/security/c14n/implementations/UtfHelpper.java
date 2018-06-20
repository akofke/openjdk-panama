/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.c14n.implementations;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

public final class UtfHelpper {

    /**
     * Revert to the old behavior (version 2 or before), i.e. surrogate pairs characters becomes
     * '??' in output. Set system property com.sun.org.apache.xml.internal.security.c14n.oldUtf8=true if you want
     * to verify signatures generated by version 2 or before that contains 32 bit chars in the
     * XML document.
     */
    private static final boolean OLD_UTF8 =
        AccessController.doPrivileged((PrivilegedAction<Boolean>)
            () -> Boolean.getBoolean("com.sun.org.apache.xml.internal.security.c14n.oldUtf8"));

    private UtfHelpper() {
        // complete
    }

    public static void writeByte(
        final String str,
        final OutputStream out,
        Map<String, byte[]> cache
    ) throws IOException {
        byte[] result = cache.get(str);
        if (result == null) {
            result = getStringInUtf8(str);
            cache.put(str, result);
        }

        out.write(result);
    }

    public static void writeCodePointToUtf8(final int c, final OutputStream out) throws IOException {
        if (!Character.isValidCodePoint(c) || c >= 0xD800 && c <= 0xDBFF || c >= 0xDC00 && c <= 0xDFFF) {
            // valid code point: c >= 0x0000 && c <= 0x10FFFF
            out.write(0x3f);
            return;
        }
        if (OLD_UTF8 && c >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // version 2 or before output 2 question mark characters for 32 bit chars
            out.write(0x3f);
            out.write(0x3f);
            return;
        }

        if (c < 0x80) {
            // 0x00000000 - 0x0000007F
            // 0xxxxxxx
            out.write(c);
            return;
        }
        byte extraByte = 0;
        if (c < 0x800) {
            // 0x00000080 - 0x000007FF
            // 110xxxxx 10xxxxxx
            extraByte = 1;
        } else if (c < 0x10000) {
            // 0x00000800 - 0x0000FFFF
            // 1110xxxx 10xxxxxx 10xxxxxx
            extraByte = 2;
        } else if (c < 0x200000) {
            // 0x00010000 - 0x001FFFFF
            // 11110xxx 10xxxxx 10xxxxxx 10xxxxxx
            extraByte = 3;
        } else if (c < 0x4000000) {
            // 0x00200000 - 0x03FFFFFF
            // 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
            // already outside valid Character range, just for completeness
            extraByte = 4;
        } else if (c <= 0x7FFFFFFF) {
            // 0x04000000 - 0x7FFFFFFF
            // 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
            // already outside valid Character range, just for completeness
            extraByte = 5;
        } else {
            // 0x80000000 - 0xFFFFFFFF
            // case not possible as java has no unsigned int
            out.write(0x3f);
            return;
        }

        byte write;
        int shift = 6 * extraByte;
        write = (byte)((0xFE << (6 - extraByte)) | (c >>> shift));
        out.write(write);
        for (int i = extraByte - 1; i >= 0; i--) {
            shift -= 6;
            write = (byte)(0x80 | ((c >>> shift) & 0x3F));
            out.write(write);
        }
    }

    @Deprecated
    public static void writeCharToUtf8(final char c, final OutputStream out) throws IOException {
        if (c < 0x80) {
            out.write(c);
            return;
        }
        if (c >= 0xD800 && c <= 0xDBFF || c >= 0xDC00 && c <= 0xDFFF) {
            //No Surrogates in sun java
            out.write(0x3f);
            return;
        }
        int bias;
        int write;
        char ch;
        if (c > 0x07FF) {
            ch = (char)(c>>>12);
            write = 0xE0;
            if (ch > 0) {
                write |= ch & 0x0F;
            }
            out.write(write);
            write = 0x80;
            bias = 0x3F;
        } else {
            write = 0xC0;
            bias = 0x1F;
        }
        ch = (char)(c>>>6);
        if (ch > 0) {
            write |= ch & bias;
        }
        out.write(write);
        out.write(0x80 | ((c) & 0x3F));

    }

    public static void writeStringToUtf8(
        final String str, final OutputStream out
    ) throws IOException {
        final int length = str.length();
        int i = 0;
        int c;
        while (i < length) {
            c = str.codePointAt(i);
            i += Character.charCount(c);
            if (!Character.isValidCodePoint(c) || c >= 0xD800 && c <= 0xDBFF || c >= 0xDC00 && c <= 0xDFFF) {
                // valid code point: c >= 0x0000 && c <= 0x10FFFF
                out.write(0x3f);
                continue;
            }
            if (OLD_UTF8 && c >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                // version 2 or before output 2 question mark characters for 32 bit chars
                out.write(0x3f);
                out.write(0x3f);
                continue;
            }
            if (c < 0x80)  {
                out.write(c);
                continue;
            }
            byte extraByte = 0;
            if (c < 0x800) {
                // 0x00000080 - 0x000007FF
                // 110xxxxx 10xxxxxx
                extraByte = 1;
            } else if (c < 0x10000) {
                // 0x00000800 - 0x0000FFFF
                // 1110xxxx 10xxxxxx 10xxxxxx
                extraByte = 2;
            } else if (c < 0x200000) {
                // 0x00010000 - 0x001FFFFF
                // 11110xxx 10xxxxx 10xxxxxx 10xxxxxx
                extraByte = 3;
            } else if (c < 0x4000000) {
                // 0x00200000 - 0x03FFFFFF
                // 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                // already outside valid Character range, just for completeness
                extraByte = 4;
            } else if (c <= 0x7FFFFFFF) {
                // 0x04000000 - 0x7FFFFFFF
                // 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                // already outside valid Character range, just for completeness
                extraByte = 5;
            } else {
                // 0x80000000 - 0xFFFFFFFF
                // case not possible as java has no unsigned int
                out.write(0x3f);
                continue;
            }
            byte write;
            int shift = 6 * extraByte;
            write = (byte)((0xFE << (6 - extraByte)) | (c >>> shift));
            out.write(write);
            for (int j = extraByte - 1; j >= 0; j--) {
                shift -= 6;
                write = (byte)(0x80 | ((c >>> shift) & 0x3F));
                out.write(write);
            }

        }

    }

    public static byte[] getStringInUtf8(final String str) {
        final int length = str.length();
        boolean expanded = false;
        byte[] result = new byte[length];
        int i = 0;
        int out = 0;
        int c;
        while (i < length) {
            c = str.codePointAt(i);
            i += Character.charCount(c);
            if (!Character.isValidCodePoint(c) || c >= 0xD800 && c <= 0xDBFF || c >= 0xDC00 && c <= 0xDFFF) {
                // valid code point: c >= 0x0000 && c <= 0x10FFFF
                result[out++] = (byte)0x3f;
                continue;
            }
            if (OLD_UTF8 && c >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                // version 2 or before output 2 question mark characters for 32 bit chars
                result[out++] = (byte)0x3f;
                result[out++] = (byte)0x3f;
                continue;
            }
            if (c < 0x80) {
                result[out++] = (byte)c;
                continue;
            }
            if (!expanded) {
                byte newResult[] = new byte[6*length];
                System.arraycopy(result, 0, newResult, 0, out);
                result = newResult;
                expanded = true;
            }
            byte extraByte = 0;
            if (c < 0x800) {
                // 0x00000080 - 0x000007FF
                // 110xxxxx 10xxxxxx
                extraByte = 1;
            } else if (c < 0x10000) {
                // 0x00000800 - 0x0000FFFF
                // 1110xxxx 10xxxxxx 10xxxxxx
                extraByte = 2;
            } else if (c < 0x200000) {
                // 0x00010000 - 0x001FFFFF
                // 11110xxx 10xxxxx 10xxxxxx 10xxxxxx
                extraByte = 3;
            } else if (c < 0x4000000) {
                // 0x00200000 - 0x03FFFFFF
                // 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                // already outside valid Character range, just for completeness
                extraByte = 4;
            } else if (c <= 0x7FFFFFFF) {
                // 0x04000000 - 0x7FFFFFFF
                // 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                // already outside valid Character range, just for completeness
                extraByte = 5;
            } else {
                // 0x80000000 - 0xFFFFFFFF
                // case not possible as java has no unsigned int
                result[out++] = 0x3f;
                continue;
            }
            byte write;
            int shift = 6 * extraByte;
            write = (byte)((0xFE << (6 - extraByte)) | (c >>> shift));
            result[out++] = write;
            for (int j = extraByte - 1; j >= 0; j--) {
                shift -= 6;
                write = (byte)(0x80 | ((c >>> shift) & 0x3F));
                result[out++] = write;
            }
        }
        if (expanded) {
            byte newResult[] = new byte[out];
            System.arraycopy(result, 0, newResult, 0, out);
            result = newResult;
        }
        return result;
    }
}
