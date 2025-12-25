package org.krypton.krypton.rag

import kotlinx.serialization.Serializable

/**
 * Represents a chunk of text from a markdown note file.
 * 
 * @param id Unique identifier for this chunk (e.g., filePath:startLine:endLine)
 * @param filePath Path to the source markdown file
 * @param startLine Starting line number (1-indexed)
 * @param endLine Ending line number (1-indexed, inclusive)
 * @param text The chunk text content
 * @param embedding Optional embedding vector for this chunk
 * @param sectionTitle Optional section title (heading path like "filePath#H1 > H2")
 */
@Serializable
data class NoteChunk(
    val id: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val embedding: FloatArray? = null,
    val sectionTitle: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NoteChunk

        if (id != other.id) return false
        if (filePath != other.filePath) return false
        if (startLine != other.startLine) return false
        if (endLine != other.endLine) return false
        if (text != other.text) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + startLine
        result = 31 * result + endLine
        result = 31 * result + text.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Converts a FloatArray to a ByteArray for storage in SQLite BLOB.
 */
fun FloatArray.toByteArray(): ByteArray {
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
fun ByteArray.toFloatArray(): FloatArray {
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

