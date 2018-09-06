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

#include <stdio.h>
#include <string.h>
#include <jvmti.h>
#include "agent_common.h"

#include "nsk_tools.h"
#include "jni_tools.h"
#include "JVMTITools.h"
#include "jvmti_tools.h"

#ifdef __cplusplus
extern "C" {
#endif

#define OBJ_MAX_COUNT 100000

static JNIEnv *jni = NULL;
static jvmtiEnv *jvmti = NULL;
static jvmtiEventCallbacks callbacks;
static jvmtiCapabilities caps;

static jlong timeout = 0;

static const char* DEBUGEE_SIGNATURE = "Lnsk/jvmti/scenarios/allocation/AP04/ap04t003;";
static const char* ROOT_SIGNATURE    = "[Lnsk/jvmti/scenarios/allocation/AP04/ap04t003;";

static jclass debugeeClass = NULL;
static jfieldID rootFieldID;

static jrawMonitorID startLock = NULL;
static jrawMonitorID runLock = NULL;
static jrawMonitorID endLock = NULL;

static volatile int iterationCount = 0;
static volatile int objectCount = 0;

/***********************************************************************/

static jrawMonitorID counterMonitor_ptr = NULL;

static void increaseCounter(volatile int* counterPtr) {

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }

    (*counterPtr)++;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorExit, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }
}

static void setCounter(volatile int* counterPtr, int value) {

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }

    *counterPtr = value;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorExit, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }
}

static int getCounter(volatile int* counterPtr) {
    int result;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }

    result = *counterPtr;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorExit, jvmti, counterMonitor_ptr))) {
        nsk_jvmti_setFailStatus();
    }

    return result;
}

/***********************************************************************/

void notifyThread() {

    /* enter and notify runLock */
    {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorEnter, jvmti, runLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorNotify, jvmti, runLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorExit, jvmti, runLock))) {
            nsk_jvmti_setFailStatus();
        }
    }
}

jvmtiIterationControl JNICALL
heapObjectCallback( jlong  class_tag,
                    jlong  size,
                    jlong* tag_ptr,
                    void*  user_data) {

    if (getCounter(&iterationCount) == 0) {
        notifyThread();
    }
    increaseCounter(&iterationCount);

    if (*tag_ptr > 0) {
        increaseCounter(&objectCount);
    }

    return JVMTI_ITERATION_CONTINUE;
}

/* jvmtiHeapRootCallback */
jvmtiIterationControl JNICALL
heapRootCallback( jvmtiHeapRootKind root_kind,
                  jlong class_tag,
                  jlong size,
                  jlong* tag_ptr,
                  void* user_data) {

    if (getCounter(&iterationCount) == 0) {
        notifyThread();
    }
    increaseCounter(&iterationCount);

    if (*tag_ptr > 0) {
        increaseCounter(&objectCount);
    }

    return JVMTI_ITERATION_CONTINUE;
}

/* jvmtiStackReferenceCallback */
jvmtiIterationControl JNICALL
stackReferenceCallback( jvmtiHeapRootKind root_kind,
                        jlong     class_tag,
                        jlong     size,
                        jlong*    tag_ptr,
                        jlong     thread_tag,
                        jint      depth,
                        jmethodID method,
                        jint      slot,
                        void*     user_data) {

    if (getCounter(&iterationCount) == 0) {
        notifyThread();
    }
    increaseCounter(&iterationCount);

    if (*tag_ptr > 0) {
        increaseCounter(&objectCount);
    }

    return JVMTI_ITERATION_CONTINUE;
}


/* jvmtiObjectReferenceCallback */
jvmtiIterationControl JNICALL
objectReferenceCallback( jvmtiObjectReferenceKind reference_kind,
                         jlong  class_tag,
                         jlong  size,
                         jlong* tag_ptr,
                         jlong  referrer_tag,
                         jint   referrer_index,
                         void*  user_data) {

    if (getCounter(&iterationCount) == 0) {
        notifyThread();
    }
    increaseCounter(&iterationCount);

    if (*tag_ptr > 0) {
        increaseCounter(&objectCount);
    }

    return JVMTI_ITERATION_CONTINUE;
}

/********* Agent thread modifying tags of objects ************/

/** Body of new agent thread: modify tags of tagged object. */
void JNICALL agent_start(jvmtiEnv* jvmti, JNIEnv* jni, void *p) {

    jint taggedObjectsCount = 0;
    jobject* taggedObjectsList = NULL;

    NSK_DISPLAY0("Agent thread: started.\n");

    /* obtain tagged objects list */
    {
        jlong tag = (jlong)1;

        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB6(GetObjectsWithTags, jvmti,
                                              1, &tag,
                                              &taggedObjectsCount,
                                              &taggedObjectsList,
                                              NULL))) {
            nsk_jvmti_setFailStatus();
            return;
        }
    }

    NSK_DISPLAY1("Agent thread: got tagged objects: %d\n", (int)taggedObjectsCount);

    if (!NSK_VERIFY(taggedObjectsCount == OBJ_MAX_COUNT)) {
        nsk_jvmti_setFailStatus();
        return;
    }

    /* enter runLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, runLock))) {
        nsk_jvmti_setFailStatus();
    }

    /* enter and notify startLock */
    {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorEnter, jvmti, startLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorNotify, jvmti, startLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorExit, jvmti, startLock))) {
            nsk_jvmti_setFailStatus();
        }
    }

    NSK_DISPLAY0("Agent thread: wait for run notification\n");

    /* wait on runLock */
    {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(RawMonitorWait, jvmti, runLock, timeout))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorExit, jvmti, runLock))) {
            nsk_jvmti_setFailStatus();
        }
    }

    NSK_DISPLAY0("Agent thread: modify tags of each even object.\n");

    /* modify tags of each even object */
    {
        int modified = 0;
        int i;
        for (i = 0; i < taggedObjectsCount; i+=2) {
            if (!NSK_JVMTI_VERIFY(
                    NSK_CPP_STUB3(SetTag, jvmti, taggedObjectsList[i], 0))) {
                nsk_jvmti_setFailStatus();
                continue;
            }
            modified++;
        }

        NSK_DISPLAY2("Agent thread: tags modified: %d of %d\n",
                                            modified, (int)taggedObjectsCount);
    }

    /* destroy objects list */
    {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(Deallocate, jvmti, (unsigned char*)taggedObjectsList))) {
            nsk_jvmti_setFailStatus();
        }
    }

    /* enter and notify endLock */
    {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorEnter, jvmti, endLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorNotify, jvmti, endLock))) {
            nsk_jvmti_setFailStatus();
        }
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(RawMonitorExit, jvmti, endLock))) {
            nsk_jvmti_setFailStatus();
        }
    }

    NSK_DISPLAY0("Agent thread: finished.\n");
}

/***********************************************************************/

static int startThread(JNIEnv* jni, jthread threadObj) {
    int success = NSK_TRUE;

    /* enter startLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, startLock))) {
        nsk_jvmti_setFailStatus();
    }

    /* start thread */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB5(RunAgentThread, jvmti,
                                          threadObj,
                                          agent_start,
                                          NULL,
                                          JVMTI_THREAD_NORM_PRIORITY))) {
        success = NSK_FALSE;
        nsk_jvmti_setFailStatus();
    } else {
        /* wait on startLock */
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(RawMonitorWait, jvmti, startLock, timeout))) {
            nsk_jvmti_setFailStatus();
        }
    }

    /* exit starLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorExit, jvmti, startLock))) {
        nsk_jvmti_setFailStatus();
    }

    return success;
}

/** Create thread object for new agent thread. */
static jthread newThreadObj(JNIEnv* jni) {
    jclass thrClass;
    jmethodID cid;
    jthread result = NULL;

    if (!NSK_JNI_VERIFY(jni, (thrClass =
            NSK_CPP_STUB2(FindClass, jni,
                                     "java/lang/Thread")) != NULL )) {
        nsk_jvmti_setFailStatus();
        return result;
    }

    if (!NSK_JNI_VERIFY(jni, (cid =
            NSK_CPP_STUB4(GetMethodID, jni,
                                       thrClass,
                                       "<init>",
                                       "()V")) != NULL )) {
        nsk_jvmti_setFailStatus();
        return result;
    }

    if (!NSK_JNI_VERIFY(jni, (result =
            NSK_CPP_STUB3(NewObject, jni,
                                     thrClass,
                                     cid )) != NULL )) {
        nsk_jvmti_setFailStatus();
        return result;
    }

    return result;
}

/***********************************************************************/

/** Clean counters and start new agent thread with agent_start() body. */
static int prepareToIteration (JNIEnv* jni) {
    jthread threadObj = NULL;

    setCounter(&iterationCount, 0);
    setCounter(&objectCount, 0);

    if (!NSK_VERIFY((threadObj = newThreadObj(jni)) != NULL)) {
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    /* enter endLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorEnter, jvmti, endLock))) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("Starting new agent thread...\n");
    return startThread(jni, threadObj);
}

/** Wait for new agent thread to complete. */
static void afterIteration (JNIEnv* jni) {

    /* notify new agent thread (in case if not yet notified) */
    notifyThread();

    NSK_DISPLAY0("Wait for new agent thread to complete\n");

    /* wait on endLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(RawMonitorWait, jvmti, endLock, timeout))) {
        nsk_jvmti_setFailStatus();
    }

    /* exit endLock */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(RawMonitorExit, jvmti, endLock))) {
        nsk_jvmti_setFailStatus();
    }
}

/***********************************************************************/

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_allocation_AP04_ap04t003_setTag( JNIEnv* jni,
                                                          jclass  klass,
                                                          jobject target, /* object to be tagged */
                                                          jlong   tag ) {

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetTag, jvmti, target, tag))) {
        nsk_jvmti_setFailStatus();
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_allocation_AP04_ap04t003_runIterateOverHeap( JNIEnv* jni,
                                                                      jclass  klass ) {
    int modified = 0;
    int found = 0;

    if (!prepareToIteration(jni))
        return;

    NSK_DISPLAY0("Calling IterateOverHeap...\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(IterateOverHeap, jvmti,
                                           JVMTI_HEAP_OBJECT_TAGGED,
                                           heapObjectCallback,
                                           NULL /*user_data*/))) {
        nsk_jvmti_setFailStatus();
    }
    NSK_DISPLAY0("IterateOverHeap finished.\n");

    afterIteration(jni);

    found = getCounter(&objectCount);
    NSK_DISPLAY1("Found tagged objects: %d\n", found);

    modified = OBJ_MAX_COUNT - found;
    if (modified > 0) {
        NSK_COMPLAIN2("Tags were modified by other thread during heap iteration: %d of %d\n",
                                                        modified, OBJ_MAX_COUNT);
        nsk_jvmti_setFailStatus();
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_allocation_AP04_ap04t003_runIterateOverReachableObjects( JNIEnv* jni,
                                                                                  jclass  klass ) {
    int modified = 0;
    int found = 0;

    if (!prepareToIteration(jni))
        return;

    NSK_DISPLAY0("Calling IterateOverReachableObjects...\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB5(IterateOverReachableObjects, jvmti,
                                                       heapRootCallback,
                                                       stackReferenceCallback,
                                                       objectReferenceCallback,
                                                       NULL /*user_data*/))) {
        nsk_jvmti_setFailStatus();
    }
    NSK_DISPLAY0("IterateOverReachableObjects finished.\n");

    afterIteration(jni);

    found = getCounter(&objectCount);
    NSK_DISPLAY1("Found tagged objects: %d\n", found);

    modified = OBJ_MAX_COUNT - found;
    if (modified > 0) {
        NSK_COMPLAIN2("Tags were modified by other thread during heap iteration: %d of %d\n",
                                                        modified, OBJ_MAX_COUNT);
        nsk_jvmti_setFailStatus();
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_allocation_AP04_ap04t003_runIterateOverInstancesOfClass( JNIEnv* jni,
                                                                                  jclass  klass ) {
    int modified = 0;
    int found = 0;

    if (!prepareToIteration(jni))
        return;

    NSK_DISPLAY0("Calling IterateOverInstancesOfClass...\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB5(IterateOverInstancesOfClass, jvmti,
                                                       debugeeClass,
                                                       JVMTI_HEAP_OBJECT_TAGGED,
                                                       heapObjectCallback,
                                                       NULL /*user_data*/))) {
        nsk_jvmti_setFailStatus();
    }
    NSK_DISPLAY0("IterateOverInstancesOfClass finished.\n");

    afterIteration(jni);

    found = getCounter(&objectCount);
    NSK_DISPLAY1("Found tagged objects: %d\n", found);

    modified = OBJ_MAX_COUNT - found;
    if (modified > 0) {
        NSK_COMPLAIN2("Tags were modified by other thread during heap iteration: %d of %d\n",
                                                        modified, OBJ_MAX_COUNT);
        nsk_jvmti_setFailStatus();
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_scenarios_allocation_AP04_ap04t003_runIterateOverObjectsReachableFromObject( JNIEnv* jni,
                                                                                            jclass  klass ) {
    jobject root = NULL;
    int modified = 0;
    int found = 0;

    if (!NSK_JNI_VERIFY(jni, (root =
            NSK_CPP_STUB3(GetStaticObjectField, jni,
                                                debugeeClass,
                                                rootFieldID )) != NULL )) {
        NSK_COMPLAIN0("GetStaticObjectField returned NULL for 'root' field value\n\n");
        nsk_jvmti_setFailStatus();
        return;
    }

    if (!prepareToIteration(jni))
        return;

    NSK_DISPLAY0("Calling IterateOverObjectsReachableFromObject...\n");
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(IterateOverObjectsReachableFromObject, jvmti,
                                                                 root,
                                                                 objectReferenceCallback,
                                                                 NULL /*user_data*/))) {
        nsk_jvmti_setFailStatus();
    }
    NSK_DISPLAY0("IterateOverObjectsReachableFromObject finished.\n");

    afterIteration(jni);

    found = getCounter(&objectCount);
    NSK_DISPLAY1("Found tagged objects: %d\n", found);

    modified = OBJ_MAX_COUNT - found;
    if (modified > 0) {
        NSK_COMPLAIN2("Tags were modified by other thread during heap iteration: %d of %d\n",
                                                        modified, OBJ_MAX_COUNT);
        nsk_jvmti_setFailStatus();
    }
}

static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

    NSK_DISPLAY0("Wait for debugee start\n\n");
    if (!NSK_VERIFY(nsk_jvmti_waitForSync(timeout)))
        return;

    NSK_DISPLAY1("Find debugee class: %s\n", DEBUGEE_SIGNATURE);
    debugeeClass = nsk_jvmti_classBySignature(DEBUGEE_SIGNATURE);
    if (debugeeClass == NULL) {
        nsk_jvmti_setFailStatus();
        return;
    }

    if (!NSK_JNI_VERIFY(jni, (debugeeClass = (jclass)
            NSK_CPP_STUB2(NewGlobalRef, jni, debugeeClass)) != NULL))
        return;

    NSK_DISPLAY1("Find ID of 'root' field: %s\n", ROOT_SIGNATURE);
    if (!NSK_JNI_VERIFY(jni, (rootFieldID =
            NSK_CPP_STUB4(GetStaticFieldID, jni,
                                            debugeeClass,
                                            "root",
                                            ROOT_SIGNATURE)) != NULL )) {
        nsk_jvmti_setFailStatus();
        return;
    }

    NSK_DISPLAY0("Let debugee to run test cases\n");
    if (!NSK_VERIFY(nsk_jvmti_resumeSync()))
        return;

    NSK_DISPLAY0("Wait for completion of test cases\n\n");
    if (!NSK_VERIFY(nsk_jvmti_waitForSync(timeout)))
        return;

    NSK_TRACE(NSK_CPP_STUB2(DeleteGlobalRef, jni, debugeeClass));
    NSK_TRACE(NSK_CPP_STUB2(DestroyRawMonitor, jvmti, counterMonitor_ptr));
    NSK_TRACE(NSK_CPP_STUB2(DestroyRawMonitor, jvmti, startLock));
    NSK_TRACE(NSK_CPP_STUB2(DestroyRawMonitor, jvmti, runLock));
    NSK_TRACE(NSK_CPP_STUB2(DestroyRawMonitor, jvmti, endLock));

    NSK_DISPLAY0("Let debugee to finish\n");
    if (!NSK_VERIFY(nsk_jvmti_resumeSync()))
        return;
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_ap04t003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_ap04t003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_ap04t003(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    /* init framework and parse options */
    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    /* create JVMTI environment */
    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "counterMonitor", &counterMonitor_ptr))) {
        return JNI_ERR;
    }

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "startLock", &startLock))) {
        return JNI_ERR;
    }
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "runLock", &runLock))) {
        return JNI_ERR;
    }
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "endLock", &endLock))) {
        return JNI_ERR;
    }

    memset(&caps, 0, sizeof(jvmtiCapabilities));
    caps.can_tag_objects = 1;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(AddCapabilities,
            jvmti, &caps)))
        return JNI_ERR;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(GetCapabilities,
            jvmti, &caps)))
        return JNI_ERR;

    if (!caps.can_tag_objects)
        NSK_DISPLAY0("Warning: tagging objects is not available\n");

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;
    NSK_DISPLAY0("agentProc has been set\n\n");

    return JNI_OK;
}

#ifdef __cplusplus
}
#endif
