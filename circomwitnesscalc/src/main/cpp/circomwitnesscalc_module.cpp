
#include "circomwitnesscalc_module.h"

#define TAG "CircomWitnesscalcExampleNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_io_iden3_circomwitnesscalc_WitnesscalcJniBridge_calculateWitness(
        JNIEnv *env, jobject obj,
        jstring inputs,
        jbyteArray graphDataBuffer, jlong graphDataSize,
        jobjectArray witnessBuffer, jlongArray witnessSize,
        jbyteArray errorMsg, jlong errorMsgMaxSize
) {
    const char *nativeInputs = env->GetStringUTFChars(inputs, nullptr);

    // Convert jbyteArray to native types
    void *nativeGraphDataBuffer = env->GetByteArrayElements(graphDataBuffer, nullptr);

    void *nativeWitnessBuffer = nullptr;

    unsigned long nativeWitnessSize = 0;

    gw_status_t status;

    // Call the calculateWitness function
    int result = gw_calc_witness(
            nativeInputs,
            nativeGraphDataBuffer, graphDataSize,
            &nativeWitnessBuffer, &nativeWitnessSize,
            &status
    );

    // Handle witness
    // create new Java byte array
    jbyteArray newWitnessBuffer = env->NewByteArray((int) nativeWitnessSize);
    // get representation of its elements
    jbyte *newNativeWitnessBuffer = env->GetByteArrayElements(newWitnessBuffer, nullptr);
    // assign witness bytes there
    memcpy(newNativeWitnessBuffer, nativeWitnessBuffer, nativeWitnessSize);
    // copy representation to the actual Java array
    env->ReleaseByteArrayElements(newWitnessBuffer, newNativeWitnessBuffer, 0);
    // set this byte array to the array of byte arrays passed in the function
    env->SetObjectArrayElement(witnessBuffer, 0, newWitnessBuffer);

    // Handle witness size
    // create witness size arr
    unsigned long witnessSizeArr[] = {nativeWitnessSize};
    // copy it to the original JNI size arr
    env->SetLongArrayRegion(witnessSize, 0, 1, (jlong *) witnessSizeArr);

    // Handle status and error
    // if something happened - write error to the error message
    if (status.code != OK) {
        char *nativeErrorMsg = (char *) env->GetByteArrayElements(errorMsg, nullptr);
        long len = std::min((long) errorMsgMaxSize, (long) strlen(status.error_msg));
        env->SetByteArrayRegion(errorMsg, 0, (int) len, (jbyte *) status.error_msg);
        env->ReleaseByteArrayElements(errorMsg, (jbyte *) nativeErrorMsg, 0);
    }
    gw_free_status(&status);

    // Release the native buffers
    env->ReleaseStringUTFChars(inputs, nativeInputs);
    env->ReleaseByteArrayElements(graphDataBuffer, (jbyte *) nativeGraphDataBuffer, 0);

    return result;
}

#ifdef __cplusplus
}
#endif
