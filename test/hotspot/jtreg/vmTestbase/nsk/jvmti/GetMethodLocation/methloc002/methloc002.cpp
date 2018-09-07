/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = NULL;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_methloc002(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_methloc002(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_methloc002(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res;

    if (options != NULL && strcmp(options, "printdump") == 0) {
        printdump = JNI_TRUE;
    }

    res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == NULL) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_GetMethodLocation_methloc002_check(JNIEnv *env, jclass cls) {
    jvmtiError err;
    jmethodID mid;
    jlocation startLocation;
    jlocation endLocation;

    if (jvmti == NULL) {
        printf("JVMTI client was not properly loaded!\n");
        return STATUS_FAILED;
    }

    mid = env->GetMethodID(cls, "<init>", "()V");
    if (mid == NULL) {
        printf("Cannot get method ID for \"<init>\"!\n");
        return STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> invalid method check ...\n");
    }
    err = jvmti->GetMethodLocation(NULL, &startLocation, &endLocation);
    if (err != JVMTI_ERROR_INVALID_METHODID) {
        printf("Error expected: JVMTI_ERROR_INVALID_METHODID,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> (startLocationPtr) null pointer check ...\n");
    }
    err = jvmti->GetMethodLocation(mid, NULL, &endLocation);
    if (err != JVMTI_ERROR_NULL_POINTER) {
        printf("(startLocationPtr) error expected: JVMTI_ERROR_NULL_POINTER,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> (endLocationPtr) null pointer check ...\n");
    }
    err = jvmti->GetMethodLocation(mid, &startLocation, NULL);
    if (err != JVMTI_ERROR_NULL_POINTER) {
        printf("(endLocationPtr) error expected: JVMTI_ERROR_NULL_POINTER,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    mid = env->GetStaticMethodID(cls, "check", "()I");
    if (mid == NULL) {
        printf("Cannot get method ID for \"check\"!\n");
        return STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> native method check ...\n");
    }
    err = jvmti->GetMethodLocation(mid, &startLocation, &endLocation);
    if (err != JVMTI_ERROR_NATIVE_METHOD) {
        printf("Error expected: JVMTI_ERROR_NATIVE_METHOD,\n");
        printf("\tactual: %s (%d)\n", TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> ... done\n");
    }

    return result;
}

}
