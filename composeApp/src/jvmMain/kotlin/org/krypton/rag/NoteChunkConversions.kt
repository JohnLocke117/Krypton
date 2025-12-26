package org.krypton.rag

/**
 * JVM-specific implementations of FloatArray conversion functions.
 */

/**
 * Converts a FloatArray to a ByteArray for storage in SQLite BLOB.
 */
actual fun FloatArray.toByteArray(): ByteArray {
    val bytes = ByteArray(size * 4)
    for (i in indices) {
        val bits = java.lang.Float.floatToIntBits(this[i])
        bytes[i * 4] = (bits shr 24).toByte()
        bytes[i * 4 + 1] = (bits shr 16).toByte()
        bytes[i * 4 + 2] = (bits shr 8).toByte()
        bytes[i * 4 + 3] = bits.toByte()
    }
    return bytes
}

/**
 * Converts a ByteArray back to a FloatArray from SQLite BLOB.
 */
actual fun ByteArray.toFloatArray(): FloatArray {
    require(size % 4 == 0) { "ByteArray size must be a multiple of 4" }
    val floats = FloatArray(size / 4)
    for (i in floats.indices) {
        val byte1 = this[i * 4].toInt() and 0xFF
        val byte2 = this[i * 4 + 1].toInt() and 0xFF
        val byte3 = this[i * 4 + 2].toInt() and 0xFF
        val byte4 = this[i * 4 + 3].toInt() and 0xFF
        val bits = (byte1 shl 24) or (byte2 shl 16) or (byte3 shl 8) or byte4
        floats[i] = java.lang.Float.intBitsToFloat(bits)
    }
    return floats
}

