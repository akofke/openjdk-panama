/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.util;

import java.io.*;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.util.*;
import sun.security.x509.X509CertImpl;

/**
 * A utility class to check if a certificate is untrusted. This is an internal
 * mechanism that explicitly marks a certificate as untrusted, normally in the
 * case that a certificate is known to be used for malicious reasons.
 *
 * <b>Attention</b>: This check is NOT meant to replace the standard PKI-defined
 * validation check, neither is it used as an alternative to CRL.
 */
public final class UntrustedCertificates {

    private static final Debug debug = Debug.getInstance("certpath");
    private static final String ALGORITHM_KEY = "Algorithm";

    private static final Properties props = new Properties();
    private static final String algorithm;

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                File f = new File(System.getProperty("java.home"),
                        "lib/security/blacklisted.certs");
                try (FileInputStream fin = new FileInputStream(f)) {
                    props.load(fin);
                    // It's said that the fingerprint could contain colons
                    for (Map.Entry<Object,Object> e: props.entrySet()) {
                        e.setValue(stripColons(e.getValue()));
                    }
                } catch (IOException fnfe) {
                    if (debug != null) {
                        debug.println("Error parsing blacklisted.certs");
                    }
                }
                return null;
            }
        });
        algorithm = props.getProperty(ALGORITHM_KEY);
    }

    private static String stripColons(Object input) {
        String s = (String)input;
        char[] letters = s.toCharArray();
        int pos = 0;
        for (int i = 0; i < letters.length; i++) {
            if (letters[i] != ':') {
                if (i != pos) {
                    letters[pos] = letters[i];
                }
                pos++;
            }
        }
        if (pos == letters.length) return s;
        else return new String(letters, 0, pos);
    }
    /**
     * Checks if a certificate is untrusted.
     *
     * @param cert the certificate to check
     * @return true if the certificate is untrusted.
     */
    public static boolean isUntrusted(X509Certificate cert) {
        if (algorithm == null) {
            return false;
        }
        String key;
        if (cert instanceof X509CertImpl) {
            key = ((X509CertImpl)cert).getFingerprint(algorithm);
        } else {
            try {
                key = new X509CertImpl(cert.getEncoded()).getFingerprint(algorithm);
            } catch (CertificateException cee) {
                return false;
            }
        }
        return props.containsKey(key);
    }

    private UntrustedCertificates() {}
}
