/*
 * Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "jni_tools.h"
#include "jvmti_tools.h"

extern "C" {

/* ============================================================================= */

/* scaffold objects */
static JNIEnv* jni = NULL;
static jvmtiEnv *jvmti = NULL;
static jlong timeout = 0;

/* constant names */
#define EXPECTED_CLASS_NAME "nsk/jvmti/scenarios/hotswap/HS201/hs201t001a"
#define EXPECTED_CLASS_SIGN "Lnsk/jvmti/scenarios/hotswap/HS201/hs201t001a;"
#define METHOD_NAME "doInit"
#define METHOD_SIG "()V"
#define LOCAL_VARIABLE_NAME "localVariable"
#define PATH_TO_NEW_BYTECODE "pathToNewByteCode"

static jint testStep;
static int redefineNumber;
static jint newClassSize;
static unsigned char* newClassBytes;
static jthread testedThread;
static jclass testClass;
char chbuffer[255];

const char* getThreadName(JNIEnv* jni_env, jthread thread);
const char* getClassName(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jobject object);
int readNewBytecode(jvmtiEnv* jvmti, int testcase);
int getLocalVariableValue(jvmtiEnv *jvmti_env, jthread thread, jmethodID method);

/* ============================================================================= */

void setCurrentStep(JNIEnv* jni_env, int value) {

    jfieldID fld;

    if (!NSK_JNI_VERIFY(jni_env, (fld =
            NSK_CPP_STUB4(GetStaticFieldID, jni_env, testClass, "currentStep", "I")) != NULL)) {
        NSK_CPP_STUB2(FatalError, jni_env,
            "TEST FAILED: while getting currentStep fieldID\n");
    }

    if (!NSK_JNI_VERIFY_VOID(jni_env,
            NSK_CPP_STUB4(SetStaticIntField, jni_env, testClass, fld, value))) {
        NSK_CPP_STUB2(FatalError, jni_env,
            "TEST FAILED: while setting  value of currentStep fieldID\n");
    }

}

/* ============================================================================= */

void enableEvent(jvmtiEnv *jvmti_env, jvmtiEvent event, jthread thread) {

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(SetEventNotificationMode, jvmti_env, JVMTI_ENABLE,
                                            event, thread))) {
        NSK_COMPLAIN1("TEST FAILED: enabling %s\n", TranslateEvent(event));
        nsk_jvmti_setFailStatus();
    }
}

/* ============================================================================= */

void disableEvent(jvmtiEnv *jvmti_env, jvmtiEvent event, jthread thread) {

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(SetEventNotificationMode, jvmti_env, JVMTI_DISABLE,
                                            event, thread))) {
        NSK_COMPLAIN1("TEST FAILED: disabling %s\n", TranslateEvent(event));
        nsk_jvmti_setFailStatus();
    }
}

/* ============================================================================= */

void redefineClass(jvmtiEnv *jvmti_env, jclass klass) {

    jvmtiClassDefinition classDef;

    char *className;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(GetClassSignature, jvmti_env, klass,
                                &className, NULL))) {
        nsk_jvmti_setFailStatus();
        return;
    }

    if (!NSK_VERIFY(readNewBytecode(jvmti_env, redefineNumber++))) {
        NSK_COMPLAIN0("TEST FAILED: new bytecode could not be read\n");
        nsk_jvmti_setFailStatus();
        return;
    }

    classDef.klass = klass;
    classDef.class_byte_count = newClassSize;
    classDef.class_bytes = newClassBytes;

    NSK_DISPLAY1("\tredefining class %s\n", className);
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(RedefineClasses, jvmti_env, 1, &classDef))) {
        NSK_COMPLAIN1("TEST FAILED: while redefining class %s\n", className);
        nsk_jvmti_setFailStatus();
        return;
    }

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*)className))) {
        nsk_jvmti_setFailStatus();
    }

}

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* agentJNI, void* arg) {

    redefineNumber = 1;
    jni = agentJNI;

    NSK_DISPLAY0("Waiting for debuggee to become ready\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    testStep = 1;
    NSK_DISPLAY0("\n\n>>>> Debugge started, waiting for class loading \n");
    if (!nsk_jvmti_resumeSync())
        return;

    NSK_DISPLAY0("Waiting for debuggee's threads to finish\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    NSK_TRACE(NSK_CPP_STUB2(DeleteGlobalRef, jni, testClass));
    NSK_TRACE(NSK_CPP_STUB2(DeleteGlobalRef, jni, testedThread));

    NSK_DISPLAY0("Let debuggee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;
}

/* ============================================================================= */

void setBreakPoint(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jclass klass) {
    jmethodID mid;

    if (!NSK_JNI_VERIFY(jni_env, (mid = NSK_CPP_STUB4(GetMethodID,
            jni_env, klass, METHOD_NAME, METHOD_SIG)) != NULL))
        NSK_CPP_STUB2(FatalError, jni_env,
            "[agent] failed to get ID for the java method\n");

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetBreakpoint,
            jvmti_env, mid, 1)))
        NSK_CPP_STUB2(FatalError, jni_env,
            "[agent] failed to set breakpoint\n");
}

/* ============================================================================= */

/**
 * CLASS_LOAD callback.
 *   -
 */
JNIEXPORT void JNICALL
callbackClassLoad(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                        jclass klass) {

    char *className;
    char *generic;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(GetClassSignature, jvmti_env, klass,
                                &className, &generic))) {
        nsk_jvmti_setFailStatus();
        return;
    }

    if (strcmp(className, EXPECTED_CLASS_SIGN)
                        == 0) {

        NSK_DISPLAY1("\n\n>>>> Class loaded: %s", className);
        NSK_DISPLAY0(", activating breakpoint\n");
        setBreakPoint(jvmti_env, jni_env, klass);
    }

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*)className))) {
        nsk_jvmti_setFailStatus();
    }

    if (generic != NULL)
        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                jvmti_env, (unsigned char*)generic))) {
            nsk_jvmti_setFailStatus();
        }
}

/* ============================================================================= */

/**
 * BREAKPOINT callback.
 *   -
 */
JNIEXPORT void JNICALL
callbackBreakpoint(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                         jmethodID method, jlocation location) {

    NSK_DISPLAY0("\n\n>>>>Breakpoint fired, enabling SINGLE_STEP\n");
    enableEvent(jvmti_env, JVMTI_EVENT_SINGLE_STEP, thread);
}

/* ============================================================================= */

/**
 * BREAKPOINT callback.
 *   -
 */
JNIEXPORT void JNICALL
callbackSingleStep(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                            jmethodID method, jlocation location) {

    char *methodName;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB5(GetMethodName,
            jvmti_env, method, &methodName, NULL, NULL))) {
        NSK_COMPLAIN0("TEST FAILED: unable to get method name during Breakpoint callback\n\n");
    }

    if (strcmp(methodName, METHOD_NAME) == 0) {
        char *declaringClassName;
        jclass declaringClass;

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(GetMethodDeclaringClass,
                jvmti_env, method, &declaringClass))) {
            NSK_COMPLAIN0("TEST FAILED: unable to get method name during Breakpoint callback\n\n");
        }

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetClassSignature,
                jvmti_env, declaringClass, &declaringClassName, NULL))) {
            NSK_COMPLAIN0("TEST FAILED: unable to get method name during Breakpoint callback\n\n");
        }

        if (strcmp(declaringClassName, EXPECTED_CLASS_SIGN) == 0) {
            int value;
            jboolean is_obsolete;

            /* getting local variable table*/
            value = getLocalVariableValue(jvmti_env, thread, method);

            switch (testStep) {
            case 1:

                if (value == 1) {
                    redefineClass(jvmti_env, declaringClass);
                    testStep++;
                }
                break;

            case 2:

                NSK_DISPLAY1("\n\n>>>> Checking if redefined method is obsolete\n", testStep);

                if (!NSK_JVMTI_VERIFY(
                            NSK_CPP_STUB3(IsMethodObsolete, jvmti, method, &is_obsolete))) {
                    NSK_COMPLAIN0("TEST FAILED: unable to check method to be obsolete\n");
                    nsk_jvmti_setFailStatus();
                    return;
                }

                if (!is_obsolete) {
                    NSK_COMPLAIN0("TEST FAILED: method must be obsolete\n");
                    nsk_jvmti_setFailStatus();
                }
                testStep++;
                break;

            case 3:

                NSK_DISPLAY1("\n\n>>>> Popping the currently executing frame\n", testStep);
                testStep++;
                setCurrentStep(jni_env, testStep);

                break;

            case 5:

                if (value > 10 && value < 1000) {
                    NSK_DISPLAY1("\n\n>>>> Disabling single step\n", testStep);
                    disableEvent(jvmti_env, JVMTI_EVENT_SINGLE_STEP, thread);
                    setCurrentStep(jni_env, testStep);
                }

            } /* case */

        }

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                jvmti_env, (unsigned char*) declaringClassName))) {
            NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
        }

    }

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*) methodName))) {
        NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
    }

}

/* ============================================================================= */

/**
 * EXCEPTION callback.
 *   -
 */
JNIEXPORT void JNICALL
callbackException(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                        jmethodID method, jlocation location, jobject exception,
                        jmethodID catch_method, jlocation catch_location) {

    const char *className;

    className = getClassName(jvmti_env, jni_env, exception);

    if (strcmp(EXPECTED_CLASS_SIGN, className) == 0) {
        jclass klass;

        NSK_DISPLAY2("\n\n>>>> Exception %s in thread - %s\n",
                        className, getThreadName(jni_env, thread));

        testStep++;
        if (!NSK_JNI_VERIFY(jni_env, (klass =
                NSK_CPP_STUB2(GetObjectClass, jni_env, exception)) != NULL)) {
            nsk_jvmti_setFailStatus();
            return;
        }

        redefineClass(jvmti_env, klass);
    }
}

/* ============================================================================= */

/**
 * EXCEPTION_CATCH callback.
 *   -
 */
JNIEXPORT void JNICALL
callbackExceptionCatch(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                        jmethodID method, jlocation location,
                        jobject exception) {

    const char *className;

    className = getClassName(jvmti_env, jni_env, exception);

    if (strcmp(EXPECTED_CLASS_SIGN, className) == 0) {
        jclass klass;

        NSK_DISPLAY2("\n\n>>>> Caught exception %s in thread - %s\n",
                        className, getThreadName(jni_env, thread));

        testStep++;
        if (!NSK_JNI_VERIFY(jni_env, (klass =
                NSK_CPP_STUB2(GetObjectClass, jni_env, exception)) != NULL)) {
            nsk_jvmti_setFailStatus();
            return;
        }

        redefineClass(jvmti_env, klass);
    }
}

/* ============================================================================= */

int readNewBytecode(jvmtiEnv* jvmti, int testcase) {

    char filename[256];
    FILE *bytecode;
    const char *pathToByteCode = nsk_jvmti_findOptionValue(PATH_TO_NEW_BYTECODE);
    jint read_bytes;


    if (!pathToByteCode) {
        NSK_COMPLAIN0("TEST FAILED: error opening file\n");
        return NSK_FALSE;
    }

    sprintf(filename,"%s/%s%02d/%s.class",
                pathToByteCode, "newclass", testcase, EXPECTED_CLASS_NAME);

    NSK_DISPLAY1("\treading new bytecode for the tested class\n\tfile name: %s\n",
                        filename);

    bytecode = fopen(filename, "rb");
    if (bytecode == NULL) {
        NSK_COMPLAIN0("TEST FAILED: error opening file\n");
        return NSK_FALSE;
    }

    fseek(bytecode, 0, SEEK_END);
    newClassSize = ftell(bytecode);
    rewind(bytecode);

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(Allocate, jvmti,
                                newClassSize, &newClassBytes))) {
        NSK_COMPLAIN0("buffer couldn't be allocated\n");
        return NSK_FALSE;
    }
    read_bytes = (jint) fread(newClassBytes, 1, newClassSize, bytecode);
    fclose(bytecode);
    if (read_bytes != newClassSize) {
        NSK_COMPLAIN0("TEST FAILED: error reading file\n");
        return NSK_FALSE;
    }

    return NSK_TRUE;
}

/* ============================================================================= */

const char* getThreadName(JNIEnv* jni_env, jthread thread) {
    jmethodID methodID;
    jclass klass;
    jstring jthreadName;
    const char *threadName;

    strcpy(chbuffer, "");

    if (!NSK_JNI_VERIFY(jni_env, (klass =
            NSK_CPP_STUB2(GetObjectClass, jni_env, thread)) != NULL)) {
        nsk_jvmti_setFailStatus();
        return chbuffer;
    }

    if (!NSK_JNI_VERIFY(jni_env, (methodID =
            NSK_CPP_STUB4(GetMethodID, jni_env, klass,
                        "getName", "()Ljava/lang/String;")) != NULL)) {
        nsk_jvmti_setFailStatus();
        return chbuffer;
    }

    jthreadName = (jstring) NSK_CPP_STUB3(CallObjectMethod, jni_env, thread,
                        methodID);

    threadName = NSK_CPP_STUB3(GetStringUTFChars, jni_env, jthreadName, 0);

    strcpy(chbuffer, threadName);

    NSK_CPP_STUB3(ReleaseStringUTFChars, jni_env, jthreadName, threadName);

    return chbuffer;
}

/* ============================================================================= */

const char* getClassName(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jobject object) {

    char *className;
    char *generic;
    jclass klass;

    strcpy(chbuffer, "");

    if (!NSK_JNI_VERIFY(jni_env, (klass =
            NSK_CPP_STUB2(GetObjectClass, jni_env, object)) != NULL)) {
        nsk_jvmti_setFailStatus();
        return chbuffer;
    }

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(GetClassSignature, jvmti_env, klass,
                                &className, &generic))) {
        nsk_jvmti_setFailStatus();
        return chbuffer;
    }

    strcpy(chbuffer, className);

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*)className))) {
        nsk_jvmti_setFailStatus();
    }

    if (generic != NULL)
        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                jvmti_env, (unsigned char*)generic))) {
            nsk_jvmti_setFailStatus();
        }

    return chbuffer;
}

/* ============================================================================= */

int getLocalVariableValue(jvmtiEnv *jvmti_env, jthread thread,
                                jmethodID method) {

    jvmtiLocalVariableEntry *table = NULL;
    jint entryCount = 0;
    int i;
    jint value = -1;

    /* getting local variable table*/
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetLocalVariableTable,
            jvmti_env, method, &entryCount, &table))) {
        NSK_COMPLAIN0("TEST FAILED: unable to get local variable table\n\n");
    }

    if (table != NULL) {
        jvmtiError error;

        for (i = 0; i < entryCount; i++) {
            if (strcmp(table[i].name, LOCAL_VARIABLE_NAME) == 0) {
                error = NSK_CPP_STUB5(GetLocalInt, jvmti_env, thread, 0,
                                        table[i].slot, &value);
                if (!NSK_VERIFY(error == JVMTI_ERROR_NONE
                                || error == JVMTI_ERROR_INVALID_SLOT))
                    NSK_COMPLAIN0("TEST FAILED: unable to get local variable table\n\n");
            }
        }

        for (i = 0; i < entryCount; i++) {

            if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                    jvmti_env, (unsigned char*)table[i].name))) {
                NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
            }

            if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                    jvmti_env, (unsigned char*)table[i].signature))) {
                NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method signature\n\n");
            }

        }

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
                jvmti_env, (unsigned char*)table))) {
            NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to local variable table\n\n");
        }

    }

    return value;
}

/* ============================================================================= */

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_hotswap_HS201_hs201t001_setThread(JNIEnv *env,
                        jclass cls, jthread thread) {

    if (!NSK_JNI_VERIFY(env, (testClass = (jclass)
            NSK_CPP_STUB2(NewGlobalRef, env, cls)) != NULL))
        nsk_jvmti_setFailStatus();

    if (!NSK_JNI_VERIFY(env, (testedThread =
            NSK_CPP_STUB2(NewGlobalRef, env, thread)) != NULL))
        nsk_jvmti_setFailStatus();

}

/* ============================================================================= */

JNIEXPORT jboolean JNICALL
Java_nsk_jvmti_scenarios_hotswap_HS201_hs201t001_resumeThread(JNIEnv *env,
                        jclass cls, jthread thread) {

    NSK_DISPLAY0("\tresuming thread...\n");
    disableEvent(jvmti, JVMTI_EVENT_SINGLE_STEP, thread);

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(ResumeThread, jvmti, thread))) {
        NSK_COMPLAIN0("TEST FAILED: unable to resume the thread\n");
        nsk_jvmti_setFailStatus();
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/* ============================================================================= */

JNIEXPORT jboolean JNICALL
Java_nsk_jvmti_scenarios_hotswap_HS201_hs201t001_suspendThread(JNIEnv *env,
                        jclass cls, jthread thread) {

    NSK_DISPLAY0("\tsuspending thread...\n");
    disableEvent(jvmti, JVMTI_EVENT_SINGLE_STEP, thread);

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(SuspendThread, jvmti, thread))) {
        NSK_COMPLAIN0("TEST FAILED: unable to suspend the thread\n");
        nsk_jvmti_setFailStatus();
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/* ============================================================================= */

JNIEXPORT jboolean JNICALL
Java_nsk_jvmti_scenarios_hotswap_HS201_hs201t001_popFrame(JNIEnv *env,
                        jclass cls, jthread thread) {

    NSK_DISPLAY0("\tpopping frame...\n");
    if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(PopFrame, jvmti, thread))) {
        NSK_COMPLAIN0("TEST FAILED: unable to pop the currently executed frame\n");
        nsk_jvmti_setFailStatus();
        return JNI_FALSE;
    }

    NSK_DISPLAY0("\tresuming thread...\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(ResumeThread, jvmti, thread))) {
        NSK_COMPLAIN0("TEST FAILED: unable to resume the thread\n");
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    testStep++;
    enableEvent(jvmti, JVMTI_EVENT_SINGLE_STEP, thread);

    return JNI_TRUE;
}

/* ============================================================================= */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_hs201t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_hs201t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_hs201t001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    {
        jvmtiCapabilities caps;
        memset(&caps, 0, sizeof(caps));

        caps.can_generate_exception_events = 1;
        caps.can_generate_breakpoint_events = 1;
        caps.can_generate_single_step_events = 1;
        caps.can_access_local_variables = 1;
        caps.can_redefine_classes = 1;
        caps.can_pop_frame = 1;
        caps.can_suspend = 1;

        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(AddCapabilities, jvmti, &caps)))
            return JNI_ERR;
    }

    {
        jvmtiEventCallbacks eventCallbacks;
        memset(&eventCallbacks, 0, sizeof(eventCallbacks));
        eventCallbacks.ClassLoad = callbackClassLoad;
        eventCallbacks.Exception = callbackException;
        eventCallbacks.ExceptionCatch = callbackExceptionCatch;
        eventCallbacks.Breakpoint = callbackBreakpoint;
        eventCallbacks.SingleStep = callbackSingleStep;
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(SetEventCallbacks, jvmti,
                                    &eventCallbacks, sizeof(eventCallbacks))))
            return JNI_ERR;
    }

    NSK_DISPLAY0("Enable events\n");

    enableEvent(jvmti, JVMTI_EVENT_CLASS_LOAD, testedThread);
    enableEvent(jvmti, JVMTI_EVENT_BREAKPOINT, testedThread);
    enableEvent(jvmti, JVMTI_EVENT_EXCEPTION, testedThread);
    enableEvent(jvmti, JVMTI_EVENT_EXCEPTION_CATCH, testedThread);

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;

    return JNI_OK;
}

/* ============================================================================= */

}
