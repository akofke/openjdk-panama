/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "jni_tools.h"
#include "jvmti_tools.h"

extern "C" {

/* ============================================================================= */

static jlong timeout = 0;

#define STATUS_FAIL     97

#define EVENTS_COUNT    2

static jvmtiEvent events[EVENTS_COUNT] = {
    JVMTI_EVENT_VM_INIT,
    JVMTI_EVENT_VM_DEATH
};

static jvmtiCapabilities initCaps;

/* ============================================================================= */

/**
 * Get and check current capabilities.
 * @returns NSK_FALSE if any error occured.
 */
static int checkCapabilities(jvmtiEnv* jvmti, jvmtiCapabilities* initCaps, const char where[]) {
    int success = NSK_TRUE;
    jvmtiCapabilities caps;

    memset(&caps, 0, sizeof(jvmtiCapabilities));

    NSK_DISPLAY0("GetCapabilities() for current JVMTI env\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(GetCapabilities, jvmti, &caps))) {
        return NSK_FALSE;
    }

    return success;
}

/**
 * Add given capabilities list.
 * @returns NSK_FALSE if any error occured.
 */
static int addCapabilities(jvmtiEnv* jvmti, jvmtiCapabilities* caps, const char where[]) {
    NSK_DISPLAY0("AddCapabilities() for current JVMTI env\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(AddCapabilities, jvmti, caps))) {
        return NSK_FALSE;
    }
    NSK_DISPLAY0("  ... set\n");

    return NSK_TRUE;
}

/**
 * Get potential capabilities to the given list.
 * @returns NSK_FALSE if any error occured.
 */
static int getPotentialCapabilities(jvmtiEnv* jvmti, jvmtiCapabilities* caps) {
    NSK_DISPLAY0("GetPotentialCapabilities() for current JVMTI env\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(GetPotentialCapabilities, jvmti, caps))) {
        return NSK_FALSE;
    }

    return NSK_TRUE;
}

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {
    NSK_DISPLAY0("Wait for debugee to become ready\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    NSK_DISPLAY0(">>> Testcase #3: Check capabilities in agent thread\n");
    if (!checkCapabilities(jvmti, &initCaps, "agent thread")) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("Let debugee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;
}

/* ============================================================================= */

/**
 * Callback for VM_INIT event.
 */
JNIEXPORT void JNICALL
callbackVMInit(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {

    NSK_DISPLAY0(">>> Testcase #2: Check capabilities in VM_INIT callback\n");
    if (!checkCapabilities(jvmti, &initCaps, "VM_INIT callback")) {
        nsk_jvmti_setFailStatus();
    }

}

/**
 * Callback for VM_DEATH event.
 */
JNIEXPORT void JNICALL
callbackVMDeath(jvmtiEnv* jvmti, JNIEnv* jni) {
    int success = NSK_TRUE;

    NSK_DISPLAY0(">>> Testcase #4: Check capabilities in VM_DEATH callback\n");
    success = checkCapabilities(jvmti, &initCaps, "VM_DEATH callback");

    NSK_DISPLAY1("Disable events: %d events\n", EVENTS_COUNT);
    if (!nsk_jvmti_enableEvents(JVMTI_DISABLE, EVENTS_COUNT, events, NULL)) {
        success = NSK_FALSE;
    } else {
        NSK_DISPLAY0("  ... disabled\n");
    }

    if (!success) {
        NSK_DISPLAY1("Exit with FAIL exit status: %d\n", STATUS_FAIL);
        NSK_BEFORE_TRACE(exit(STATUS_FAIL));
    }
}

/* ============================================================================= */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_addcaps003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_addcaps003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_addcaps003(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = NULL;

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    {
        jvmtiEventCallbacks eventCallbacks;

        memset(&eventCallbacks, 0, sizeof(eventCallbacks));
        eventCallbacks.VMInit = callbackVMInit;
        eventCallbacks.VMDeath = callbackVMDeath;
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(SetEventCallbacks, jvmti,
                                    &eventCallbacks, sizeof(eventCallbacks)))) {
            return JNI_ERR;
        }

    }

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;

    memset(&initCaps, 0, sizeof(jvmtiCapabilities));
    if (!getPotentialCapabilities(jvmti, &initCaps)) {
        return JNI_ERR;
    }

    NSK_DISPLAY0(">>> Testcase #0: Add all potential capabilities in Agent_OnLoad()\n");
    if (!addCapabilities(jvmti, &initCaps, "Agent_OnLoad()")) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0(">>> Testcase #1: Check capabilities in Agent_OnLoad()\n");
    if (!checkCapabilities(jvmti, &initCaps, "Agent_OnLoad()")) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY1("Enable events: %d events\n", EVENTS_COUNT);
    if (nsk_jvmti_enableEvents(JVMTI_ENABLE, EVENTS_COUNT, events, NULL)) {
        NSK_DISPLAY0("  ... enabled\n");
    }

    return JNI_OK;
}

/* ============================================================================= */

}
