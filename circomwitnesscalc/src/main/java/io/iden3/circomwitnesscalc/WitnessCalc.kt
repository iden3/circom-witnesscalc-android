package io.iden3.circomwitnesscalc

import android.util.Log
import java.nio.charset.StandardCharsets

const val DEFAULT_ERROR_BUFFER_SIZE = 256

// Witnesscalc status codes
private const val WITNESSCALC_OK = 0x0
private const val WITNESSCALC_ERROR = 0x1

private val witnesscalcJNI = WitnesscalcJniBridge()


@Throws(WitnesscalcError::class)
fun calculateWitness(
    inputs: String,
    graphData: ByteArray,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): ByteArray {
    val witnessArray = arrayOfNulls<ByteArray>(1)
    val witnessSizeArray = LongArray(1)

    val errorBuffer = ByteArray(errorBufferSize)

    val statusCode = witnesscalcJNI.calculateWitness(
        inputs,
        graphData, graphData.size.toLong(),
        witnessArray, witnessSizeArray,
        errorBuffer, errorBuffer.size.toLong(),
    )

    val error = String(errorBuffer, StandardCharsets.UTF_8).trim(' ')

    val witness = witnessArray.first()
    if (statusCode == WITNESSCALC_OK && witness != null) {
        return witness
    } else if (statusCode == WITNESSCALC_ERROR) {
        throw WitnesscalcError(error, code = statusCode)
    } else if (witness == null) {
        throw WitnesscalcError(error)
    } else {
        throw WitnesscalcUnknownStatusError(statusCode)
    }
}

open class WitnesscalcError(
    message: String? = null,
    cause: Throwable? = null,
    val code: Int = WITNESSCALC_ERROR,
) : Exception(message, cause)

class WitnesscalcUnknownStatusError(code: Int) :
    WitnesscalcError("Unknown status during witness calculation - $code", null, code)

private class WitnesscalcJniBridge {
    companion object {
        init {
            System.loadLibrary("circomwitnesscalc_module")
        }
    }

    external fun calculateWitness(
        inputs: String,
        graphDataBuffer: ByteArray, circuitSize: Long,
        witnessBuffer: Array<ByteArray?>, witnessSize: LongArray,
        errorMsg: ByteArray, errorMsgMaxSize: Long
    ): Int
}

