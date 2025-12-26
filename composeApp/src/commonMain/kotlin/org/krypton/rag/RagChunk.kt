package org.krypton.rag

/**
 * Represents a chunk of text for RAG operations.
 * 
 * This is the canonical in-memory chunk type for RAG operations. Use this for all new code.
 * Metadata is stored as a map for flexibility and JSON serialization.
 * 
 * This type is platform-agnostic and should be used throughout the RAG layer for:
 * - Vector store operations (upsert, search)
 * - Retrieval results
 * - RAG service operations
 * 
 * For persistence (e.g., SQLite) and legacy code, see [NoteChunk].
 * 
 * @param id Unique identifier (e.g., "filePath:startLine:endLine")
 * @param text The chunk text content
 * @param metadata Metadata map (filePath, startLine, endLine, sectionTitle, etc.)
 * @param embedding Optional embedding vector (null until embedded)
 */
data class RagChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap(),
    val embedding: List<Float>? = null
)

