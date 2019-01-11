/*
 * Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <psapi.h>
#include <locale.h>

#include "jni.h"
#include "jni_util.h"

static void getParent(const TCHAR *path, TCHAR *dest) {
    char* lastSlash = max(strrchr(path, '\\'), strrchr(path, '/'));
    if (lastSlash == NULL) {
        *dest = 0;
        return;
    }
    if (path != dest)
        strcpy(dest, path);
    *lastSlash = 0;
}

void* getProcessHandle() {
    return (void*)GetModuleHandle(NULL);
}

/*
 * Windows doesn't have an RTLD_DEFAULT equivalent, so in stead we have to
 * iterate over all the modules loaded by the process to implement the 
 * default library behaviour.
 */
void* findEntryInProcess(const char* name) {
    HANDLE hProcess = GetCurrentProcess();

    HMODULE hMods[1024];
    DWORD cbNeeded; // array size in bytes

    // first come, first served
    if(EnumProcessModules(hProcess, hMods, sizeof(hMods), &cbNeeded)) {
        for (int i = 0; i < (cbNeeded / sizeof(HMODULE)); i++) {
            HMODULE mod = hMods[i];
            FARPROC proc = GetProcAddress(mod, name);
            if(proc != NULL) {
                return proc;
            }
        }
    } 

    return NULL;
}

/*
 * Windows symbols can be simple like JNI_OnLoad or __stdcall format
 * like _JNI_OnLoad@8. We need to handle both.
 */
void buildJniFunctionName(const char *sym, const char *cname,
                          char *jniEntryName) {
    if (cname != NULL) {
        char *p = strrchr(sym, '@');
        if (p != NULL && p != sym) {
            // sym == _JNI_OnLoad@8
            strncpy(jniEntryName, sym, (p - sym));
            jniEntryName[(p-sym)] = '\0';
            // jniEntryName == _JNI_OnLoad
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
            strcat(jniEntryName, p);
            //jniEntryName == _JNI_OnLoad_cname@8
        } else {
            strcpy(jniEntryName, sym);
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
        }
    } else {
        strcpy(jniEntryName, sym);
    }
    return;
}

JNIEXPORT size_t JNICALL
getLastErrorString(char *buf, size_t len) {

    DWORD errval;

    if ((errval = GetLastError()) != 0) {
        // DOS error
        size_t n = (size_t)FormatMessage(
                FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL,
                errval,
                0,
                buf,
                (DWORD)len,
                NULL);
        if (n > 3) {
            // Drop final '.', CR, LF
            if (buf[n - 1] == '\n') n--;
            if (buf[n - 1] == '\r') n--;
            if (buf[n - 1] == '.') n--;
            buf[n] = '\0';
        }
        return n;
    }

    // C runtime error that has no corresponding DOS error code
    if (errno == 0 || len < 1) return 0;
    return strerror_s(buf, len, errno);
}

JNIEXPORT int JNICALL
getErrorString(int err, char *buf, size_t len)
{
    int ret = 0;
    if (err == 0 || len < 1) return 0;
    ret = strerror_s(buf, len, err);
    return ret;
}
