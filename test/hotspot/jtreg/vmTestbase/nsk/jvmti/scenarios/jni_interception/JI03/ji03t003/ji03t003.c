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
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

#include <jvmti.h>
#include "agent_common.h"

#include "JVMTITools.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_ARG
  #ifdef __cplusplus
    #define JNI_ENV_ARG(x, y) y
    #define JNI_ENV_PTR(x) x
  #else
    #define JNI_ENV_ARG(x, y) x, y
    #define JNI_ENV_PTR(x) (*x)
  #endif
#endif

#ifndef JNI_ENV_ARG1
  #ifdef __cplusplus
    #define JNI_ENV_ARG1(x)
  #else
    #define JNI_ENV_ARG1(x) x
  #endif
#endif

#define PASSED  0
#define STATUS_FAILED  2

static jvmtiEnv *jvmti = NULL;
static jint result = PASSED;
static int verbose = 0;

static const char *javaField = "exc";
static const char *excClassSig =
    "Lnsk/jvmti/scenarios/jni_interception/JI03/ji03t003Exc;";

/* the original JNI function table */
static jniNativeInterface *orig_jni_functions = NULL;

/* the redirected JNI function table */
static jniNativeInterface *redir_jni_functions = NULL;

/* number of the redirected JNI function calls */
int throw_calls = 0;
int thrownew_calls = 0;
int excoccur_calls = 0;

/** redirected JNI functions **/
jint JNICALL MyThrow(JNIEnv *env, jthrowable thrw) {
    jint res;

    throw_calls++;
    if (verbose)
        printf("\nMyThrow: the function called successfully: number of calls=%d\n",
            throw_calls);

    res = orig_jni_functions->Throw(
        JNI_ENV_ARG(env, thrw));

    if (verbose)
        printf("MyThrow: returning res=%d\n",res);
    return res;
}

jint JNICALL MyThrowNew(JNIEnv *env, jclass cls, const char *msg) {
    jint res;

    thrownew_calls++;
    if (verbose)
        printf("\nMyThrowNew: the function called successfully: number of calls=%d\n",
            thrownew_calls);

    res = orig_jni_functions->ThrowNew(
        JNI_ENV_ARG(env, cls), msg);

    if (verbose)
        printf("MyThrowNew: returning res=%d\n",res);
    return res;
}

jthrowable JNICALL MyExceptionOccurred(JNIEnv *env) {
    if (isThreadExpected(jvmti, NULL)) {
        excoccur_calls++;
        if (verbose)
            printf("\nMyExceptionOccurred: the function called successfully: number of calls=%d\n",
                   excoccur_calls);
    }

    return orig_jni_functions->ExceptionOccurred(
        JNI_ENV_ARG1(env));
}
/*****************************/

void doRedirect(JNIEnv *env) {
    jvmtiError err;

    if (verbose)
        printf("\ndoRedirect: obtaining the JNI function table ...\n");
    if ((err = (*jvmti)->GetJNIFunctionTable(jvmti, &orig_jni_functions)) !=
            JVMTI_ERROR_NONE) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to get original JNI function table: %s\n",
            __FILE__, __LINE__, TranslateError(err));
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to get original JNI function table"));
    }
    if ((err = (*jvmti)->GetJNIFunctionTable(jvmti, &redir_jni_functions)) !=
            JVMTI_ERROR_NONE) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to get redirected JNI function table: %s\n",
            __FILE__, __LINE__, TranslateError(err));
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to get redirected JNI function table"));
    }
    if (verbose)
        printf("doRedirect: the JNI function table obtained successfully\n");

    if (verbose)
        printf("\ndoRedirect: overwriting the functions Throw,ThrowNew,ExceptionOccured ...\n");
    redir_jni_functions->Throw = MyThrow;
    redir_jni_functions->ThrowNew = MyThrowNew;
    redir_jni_functions->ExceptionOccurred = MyExceptionOccurred;

    if ((err = (*jvmti)->SetJNIFunctionTable(jvmti, redir_jni_functions)) !=
            JVMTI_ERROR_NONE) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to set new JNI function table: %s\n",
            __FILE__, __LINE__, TranslateError(err));
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to set new JNI function table"));
    }

    if (verbose)
        printf("\ndoRedirect: the functions are overwritten successfully\n");
}

void doRestore(JNIEnv *env) {
    jvmtiError err;

    if (verbose)
        printf("\ndoRestore: restoring the original JNI function table ...\n");
    if ((err = (*jvmti)->SetJNIFunctionTable(jvmti, orig_jni_functions)) !=
            JVMTI_ERROR_NONE) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to restore original JNI function table: %s\n",
            __FILE__, __LINE__, TranslateError(err));
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to restore original JNI function table"));
    }
    if (verbose)
        printf("doRestore: the original JNI function table is restored successfully\n");
}

void doExc(JNIEnv *env, jthrowable thrw, jclass thrCls, const char *msg) {
    jint res;

    if ((res = JNI_ENV_PTR(env)->ThrowNew(JNI_ENV_ARG(env, thrCls), msg)) != 0) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to throw new exception\n",
            __FILE__, __LINE__);
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to failed to throw new exception"));
    }

    if (JNI_ENV_PTR(env)->ExceptionOccurred(JNI_ENV_ARG1(env))) {
        if (verbose)
            printf("\nCHECK PASSED: exception %s thrown by ThrowNew()\n\tis detected by ExceptionOccurred() successfully\n",
                msg);
        JNI_ENV_PTR(env)->ExceptionClear(JNI_ENV_ARG1(env));
    }
    else {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: exception %s thrown by ThrowNew()\n\tis not detected by ExceptionOccurred()\n",
            __FILE__, __LINE__, msg);
    }

    if ((res = JNI_ENV_PTR(env)->Throw(JNI_ENV_ARG(env, thrw))) != 0) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to throw exception\n",
            __FILE__, __LINE__);
        JNI_ENV_PTR(env)->FatalError(JNI_ENV_ARG(env,
            "failed to failed to throw new exception"));
    }

    if (JNI_ENV_PTR(env)->ExceptionOccurred(JNI_ENV_ARG1(env))) {
        if (verbose)
            printf("(%s,%d): CHECK PASSED: exception %s thrown by Throw()\n\tis detected by ExceptionOccurred() successfully\n",
                __FILE__, __LINE__, msg);
        JNI_ENV_PTR(env)->ExceptionClear(JNI_ENV_ARG1(env));
    }
    else {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: exception %s thrown by Throw()\n\tis not detected by ExceptionOccurred()\n",
            __FILE__, __LINE__, msg);
    }
}

void checkCall(int step, int exThrCalls, int exThrNewCalls, int exExcOccCalls) {
    if (throw_calls == exThrCalls) {
        if (verbose)
            printf("\nCHECK PASSED: the %s JNI function Throw() has been %s:\n\t%d intercepted call(s) as expected\n",
                (step==1)?"tested":"original",
                (step==1)?"redirected":"restored",
                throw_calls);
    }
    else {
        result = STATUS_FAILED;
        printf("\nTEST FAILED: the %s JNI function Throw() has not been %s:\n\t%d intercepted call(s) instead of %d as expected\n",
            (step==1)?"tested":"original",
            (step==1)?"redirected":"restored",
            throw_calls, exThrCalls);
    }
    throw_calls = 0; /* zeroing an interception counter */

    if (thrownew_calls == exThrNewCalls) {
        if (verbose)
            printf("\nCHECK PASSED: the %s JNI function ThrowNew() has been %s:\n\t%d intercepted call(s) as expected\n",
                (step==1)?"tested":"original",
                (step==1)?"redirected":"restored",
                thrownew_calls);
    }
    else {
        result = STATUS_FAILED;
        printf("\nTEST FAILED: the %s JNI function ThrowNew() has not been %s:\n\t%d intercepted call(s) instead of %d as expected\n",
            (step==1)?"tested":"original",
            (step==1)?"redirected":"restored",
            thrownew_calls, exThrNewCalls);
    }
    thrownew_calls = 0; /* zeroing an interception counter */

    if (excoccur_calls == exExcOccCalls) {
        if (verbose)
            printf("\nCHECK PASSED: the %s JNI function ExceptionOccurred() has been %s:\n\t%d intercepted call(s) as expected\n",
                (step==1)?"tested":"original",
                (step==1)?"redirected":"restored",
                excoccur_calls);
    }
    else {
        result = STATUS_FAILED;
        printf("\nTEST FAILED: the %s JNI function ExceptionOccurred() has not been %s:\n\t%d intercepted call(s) instead of %d as expected\n",
            (step==1)?"tested":"original",
            (step==1)?"redirected":"restored",
            excoccur_calls, exExcOccCalls);
    }
    excoccur_calls = 0; /* zeroing an interception counter */
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_scenarios_jni_1interception_JI03_ji03t003_check(JNIEnv *env, jobject obj) {
    jfieldID fid;
    jobject thrwObj;
    jclass thrw;
    jclass objCls;

    if (jvmti == NULL) {
        printf("(%s,%d): TEST FAILURE: JVMTI client was not properly loaded\n",
            __FILE__, __LINE__);
        return STATUS_FAILED;
    }

    objCls = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG(env, obj));

    if (verbose)
       printf("\ncheck: obtaining field ID for \"name=%s signature=%s\"...\n",
           javaField, excClassSig);
    if ((fid = JNI_ENV_PTR(env)->GetFieldID(
            JNI_ENV_ARG(env, objCls), javaField, excClassSig)) == 0) {
        result = STATUS_FAILED;
        printf("(%s,%d): TEST FAILED: failed to get ID for the field \"%s\"\n",
            __FILE__, __LINE__, javaField);
        return result;
    }

    if (verbose)
       printf("check: obtaining the value of the field \"%s\"...\n",
           javaField);
    thrwObj = JNI_ENV_PTR(env)->GetObjectField(
        JNI_ENV_ARG(env, obj), fid);

    if (verbose)
       printf("check: obtaining the class of the object for \"%s\"...\n",
           javaField);
    thrw = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG(env, thrwObj));

    /* 1: check the JNI function table interception */
    if (verbose)
        printf("\na) Checking the JNI function table interception ...\n");
    doRedirect(env);
    doExc(env, thrwObj, thrw, "redirected");
    checkCall(1, 1, 1, 2);

    /* 2: check the restored JNI function table */
    if (verbose)
        printf("\nb) Checking the restored JNI function table ...\n");
    doRestore(env);
    doExc(env, thrwObj, thrw, "restored");
    checkCall(2, 0, 0, 0);

    JNI_ENV_PTR(env)->DeleteLocalRef(JNI_ENV_ARG(env, thrw));
    JNI_ENV_PTR(env)->DeleteLocalRef(JNI_ENV_ARG(env, thrwObj));

    return result;
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_ji03t003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_ji03t003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_ji03t003(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res;

    if (options != NULL && strcmp(options, "-verbose") == 0)
        verbose = 1;

    if (verbose)
        printf("verbose mode on\n");

    res = JNI_ENV_PTR(jvm)->
        GetEnv(JNI_ENV_ARG(jvm, (void **) &jvmti), JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == NULL) {
        printf("(%s,%d): Failed to call GetEnv\n", __FILE__, __LINE__);
        return JNI_ERR;
    }

    return JNI_OK;
}

#ifdef __cplusplus
}
#endif
