package org.krypton.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android implementation of FloatArray to ByteArray conversion.
 */
actual fun FloatArray.toByteArray(): ByteArray {
    val bytes = ByteArray(size * 4)
    val buffer = ByteBuffer.wrap(bytes)
    buffer.order(ByteOrder.BIG_ENDIAN)
    for (i in indices) {
        buffer.putFloat(this[i])
    }
    return bytes
}

/**
 * Android implementation of ByteArray to FloatArray conversion.
 */
actual fun ByteArray.toFloatArray(): FloatArray {
    require(size % 4 == 0) { "ByteArray size must be a multiple of 4" }
    val floats = FloatArray(size / 4)
    val buffer = ByteBuffer.wrap(this)
    buffer.order(ByteOrder.BIG_ENDIAN)
    for (i in floats.indices) {
        floats[i] = buffer.getFloat()
    }
    return floats
}

