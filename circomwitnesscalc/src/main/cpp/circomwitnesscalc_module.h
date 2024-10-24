#ifndef ANDROID_CMAKE_LIBCIRCOMWITNESSCALC_MODULE_H
#define ANDROID_CMAKE_LIBCIRCOMWITNESSCALC_MODULE_H

#include <jni.h>
#include <sys/mman.h>
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include <__algorithm/min.h>
#include <string>
#include <cstring>
#include "libcircom_witnesscalc.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_io_iden3_circomwitnesscalc_WitnesscalcJniBridge_calculateWitness(
        JNIEnv *env, jobject obj,
        jstring inputs,
        jbyteArray graphDataBuffer, jlong graphDataSize,
        jobjectArray witnessBuffer, jlongArray witnessSize,
        jbyteArray errorMsg, jlong errorMsgMaxSize
);

#ifdef __cplusplus
}
#endif

#endif //ANDROID_CMAKE_LIBCIRCOMWITNESSCALC_MODULE_H
