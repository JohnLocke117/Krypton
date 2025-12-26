package org.krypton.rag

import kotlinx.serialization.Serializable

/**
 * Represents a chunk of text from a markdown note file.
 * 
 * Primarily for persistence (e.g., SQLite) and legacy code. New code should use [RagChunk].
 * 
 * This type includes structured fields (filePath, startLine, endLine) that are useful for
 * database storage and serialization. For in-memory RAG operations, use [RagChunk] instead,
 * which stores metadata as a flexible map.
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
 * 
 * Uses platform-specific bit manipulation via expect/actual.
 */
expect fun FloatArray.toByteArray(): ByteArray

/**
 * Converts a ByteArray back to a FloatArray from SQLite BLOB.
 * 
 * Uses platform-specific bit manipulation via expect/actual.
 */
expect fun ByteArray.toFloatArray(): FloatArray

/**
 * Converts a NoteChunk to a RagChunk.
 */
fun NoteChunk.toRagChunk(): RagChunk {
    val metadata = buildMap<String, String> {
        put("filePath", filePath)
        put("startLine", startLine.toString())
        put("endLine", endLine.toString())
        if (sectionTitle != null) {
            put("sectionTitle", sectionTitle)
        }
    }
    return RagChunk(
        id = id,
        text = text,
        metadata = metadata,
        embedding = embedding?.toList()
    )
}

/**
 * Converts a RagChunk to a NoteChunk.
 * 
 * Note: This conversion may lose information if metadata is incomplete.
 */
fun RagChunk.toNoteChunk(): NoteChunk {
    val filePath = metadata["filePath"] ?: ""
    val startLine = metadata["startLine"]?.toIntOrNull() ?: 0
    val endLine = metadata["endLine"]?.toIntOrNull() ?: 0
    val sectionTitle = metadata["sectionTitle"]
    return NoteChunk(
        id = id,
        filePath = filePath,
        startLine = startLine,
        endLine = endLine,
        text = text,
        embedding = embedding?.toFloatArray(),
        sectionTitle = sectionTitle
    )
}

/**
 * Converts a List<Float> to FloatArray.
 */
fun List<Float>.toFloatArray(): FloatArray = FloatArray(size) { this[it] }

