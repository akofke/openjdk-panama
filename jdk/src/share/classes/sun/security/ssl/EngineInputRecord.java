/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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


package sun.security.ssl;

import java.io.*;
import java.nio.*;
import javax.net.ssl.*;
import javax.crypto.BadPaddingException;
import sun.misc.HexDumpEncoder;


/**
 * Wrapper class around InputRecord.
 *
 * Application data is kept external to the InputRecord,
 * but handshake data (alert/change_cipher_spec/handshake) will
 * be kept internally in the ByteArrayInputStream.
 *
 * @author Brad Wetmore
 */
final class EngineInputRecord extends InputRecord {

    private SSLEngineImpl engine;

    /*
     * A dummy ByteBuffer we'll pass back even when the data
     * is stored internally.  It'll never actually be used.
     */
    static private ByteBuffer tmpBB = ByteBuffer.allocate(0);

    /*
     * Flag to tell whether the last read/parsed data resides
     * internal in the ByteArrayInputStream, or in the external
     * buffers.
     */
    private boolean internalData;

    EngineInputRecord(SSLEngineImpl engine) {
        super();
        this.engine = engine;
    }

    @Override
    byte contentType() {
        if (internalData) {
            return super.contentType();
        } else {
            return ct_application_data;
        }
    }

    /*
     * Check if there is enough inbound data in the ByteBuffer
     * to make a inbound packet.  Look for both SSLv2 and SSLv3.
     *
     * @return -1 if there are not enough bytes to tell (small header),
     */
    int bytesInCompletePacket(ByteBuffer buf) throws SSLException {

        /*
         * SSLv2 length field is in bytes 0/1
         * SSLv3/TLS length field is in bytes 3/4
         */
        if (buf.remaining() < 5) {
            return -1;
        }

        int pos = buf.position();
        byte byteZero = buf.get(pos);

        int len = 0;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last hueristic to
         * see if it's SSL/TLS.
         */
        if (formatVerified ||
                (byteZero == ct_handshake) ||
                (byteZero == ct_alert)) {
            /*
             * Last sanity check that it's not a wild record
             */
            ProtocolVersion recordVersion =
                ProtocolVersion.valueOf(buf.get(pos + 1), buf.get(pos + 2));

            // Check if too old (currently not possible)
            // or if the major version does not match.
            // The actual version negotiation is in the handshaker classes
            if ((recordVersion.v < ProtocolVersion.MIN.v)
                    || (recordVersion.major > ProtocolVersion.MAX.major)) {
                throw new SSLException(
                    "Unsupported record version " + recordVersion);
            }

            /*
             * Reasonably sure this is a V3, disable further checks.
             * We can't do the same in the v2 check below, because
             * read still needs to parse/handle the v2 clientHello.
             */
            formatVerified = true;

            /*
             * One of the SSLv3/TLS message types.
             */
            len = ((buf.get(pos + 3) & 0xff) << 8) +
                (buf.get(pos + 4) & 0xff) + headerSize;

        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byteZero & 0x80) != 0);

            if (isShort &&
                    ((buf.get(pos + 2) == 1) || buf.get(pos + 2) == 4)) {

                ProtocolVersion recordVersion =
                    ProtocolVersion.valueOf(buf.get(pos + 3), buf.get(pos + 4));

                // Check if too old (currently not possible)
                // or if the major version does not match.
                // The actual version negotiation is in the handshaker classes
                if ((recordVersion.v < ProtocolVersion.MIN.v)
                        || (recordVersion.major > ProtocolVersion.MAX.major)) {

                    // if it's not SSLv2, we're out of here.
                    if (recordVersion.v != ProtocolVersion.SSL20Hello.v) {
                        throw new SSLException(
                            "Unsupported record version " + recordVersion);
                    }
                }

                /*
                 * Client or Server Hello
                 */
                int mask = (isShort ? 0x7f : 0x3f);
                len = ((byteZero & mask) << 8) + (buf.get(pos + 1) & 0xff) +
                    (isShort ? 2 : 3);

            } else {
                // Gobblygook!
                throw new SSLException(
                    "Unrecognized SSL message, plaintext connection?");
            }
        }

        return len;
    }

    /*
     * Pass the data down if it's internally cached, otherwise
     * do it here.
     *
     * If internal data, data is decrypted internally.
     *
     * If external data(app), return a new ByteBuffer with data to
     * process.
     */
    ByteBuffer decrypt(Authenticator authenticator,
            CipherBox box, ByteBuffer bb) throws BadPaddingException {

        if (internalData) {
            decrypt(authenticator, box);    // MAC is checked during decryption
            return tmpBB;
        }

        BadPaddingException bpe = null;
        if (!box.isNullCipher()) {
            try {
                // apply explicit nonce for AEAD/CBC cipher suites if needed
                int nonceSize =
                    box.applyExplicitNonce(authenticator, contentType(), bb);

                // decrypt the content
                if (box.isAEADMode()) {
                    // DON'T encrypt the nonce_explicit for AEAD mode
                    bb.position(bb.position() + nonceSize);
                }   // The explicit IV for CBC mode can be decrypted.

                box.decrypt(bb);
                bb.position(nonceSize); // We don't actually remove the nonce.
            } catch (BadPaddingException e) {
                // RFC 2246 states that decryption_failed should be used
                // for this purpose. However, that allows certain attacks,
                // so we just send bad record MAC. We also need to make
                // sure to always check the MAC to avoid a timing attack
                // for the same issue. See paper by Vaudenay et al and the
                // update in RFC 4346/5246.
                //
                // Failover to message authentication code checking.
                bpe = new BadPaddingException("invalid padding");
            }
        }

        // Requires message authentication code for null, stream and block
        // cipher suites.
        if (authenticator instanceof MAC) {
            MAC signer = (MAC)authenticator;
            int macLen = signer.MAClen();
            if (macLen != 0) {
                if (bb.remaining() < macLen) {
                    // negative data length, something is wrong
                    throw new BadPaddingException("bad record");
                }

                int position = bb.position();
                int limit = bb.limit();
                int macOffset = limit - macLen;

                bb.limit(macOffset);
                byte[] hash = signer.compute(contentType(), bb);
                if (hash == null || macLen != hash.length) {
                    // something is wrong with MAC implementation
                    throw new RuntimeException("Internal MAC error");
                }

                bb.position(macOffset);
                bb.limit(limit);

                try {
                    for (byte b : hash) {       // No BB.equals(byte []); !
                        if (bb.get() != b) {
                            throw new BadPaddingException("bad record MAC");
                        }
                    }
                } finally {
                    // reset to the data
                    bb.position(position);
                    bb.limit(macOffset);
                }
            }
        }

        // Is it a failover?
        if (bpe != null) {
            throw bpe;
        }

        return bb.slice();
    }

    /*
     * Override the actual write below.  We do things this way to be
     * consistent with InputRecord.  InputRecord may try to write out
     * data to the peer, and *then* throw an Exception.  This forces
     * data to be generated/output before the exception is ever
     * generated.
     */
    @Override
    void writeBuffer(OutputStream s, byte [] buf, int off, int len)
            throws IOException {
        /*
         * Copy data out of buffer, it's ready to go.
         */
        ByteBuffer netBB = (ByteBuffer)
            (ByteBuffer.allocate(len).put(buf, 0, len).flip());
        engine.writer.putOutboundDataSync(netBB);
    }

    /*
     * Delineate or read a complete packet from src.
     *
     * If internal data (hs, alert, ccs), the data is read and
     * stored internally.
     *
     * If external data (app), return a new ByteBuffer which points
     * to the data to process.
     */
    ByteBuffer read(ByteBuffer srcBB) throws IOException {
        /*
         * Could have a src == null/dst == null check here,
         * but that was already checked by SSLEngine.unwrap before
         * ever attempting to read.
         */

        /*
         * If we have anything besides application data,
         * or if we haven't even done the initial v2 verification,
         * we send this down to be processed by the underlying
         * internal cache.
         */
        if (!formatVerified ||
                (srcBB.get(srcBB.position()) != ct_application_data)) {
            internalData = true;
            read(new ByteBufferInputStream(srcBB), (OutputStream) null);
            return tmpBB;
        }

        internalData = false;

        int srcPos = srcBB.position();
        int srcLim = srcBB.limit();

        ProtocolVersion recordVersion = ProtocolVersion.valueOf(
                srcBB.get(srcPos + 1), srcBB.get(srcPos + 2));
        // Check if too old (currently not possible)
        // or if the major version does not match.
        // The actual version negotiation is in the handshaker classes
        if ((recordVersion.v < ProtocolVersion.MIN.v)
                || (recordVersion.major > ProtocolVersion.MAX.major)) {
            throw new SSLException(
                "Unsupported record version " + recordVersion);
        }

        /*
         * It's really application data.  How much to consume?
         * Jump over the header.
         */
        int len = bytesInCompletePacket(srcBB);
        assert(len > 0);

        if (debug != null && Debug.isOn("packet")) {
            try {
                HexDumpEncoder hd = new HexDumpEncoder();
                ByteBuffer bb = srcBB.duplicate();  // Use copy of BB
                bb.limit(srcPos + len);

                System.out.println("[Raw read (bb)]: length = " + len);
                hd.encodeBuffer(bb, System.out);
            } catch (IOException e) { }
        }

        // Demarcate past header to end of packet.
        srcBB.position(srcPos + headerSize);
        srcBB.limit(srcPos + len);

        // Protect remainder of buffer, create slice to actually
        // operate on.
        ByteBuffer bb = srcBB.slice();

        srcBB.position(srcBB.limit());
        srcBB.limit(srcLim);

        return bb;
    }
}
