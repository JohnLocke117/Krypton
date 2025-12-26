package org.krypton.rag.models

/**
 * Options for RAG queries.
 * 
 * Allows customization of retrieval behavior without requiring many method parameters.
 */
data class RagQueryOptions(
    /** Number of top-K chunks to retrieve (null = use default) */
    val topK: Int? = null,
    /** Similarity threshold (null = use default) */
    val similarityThreshold: Double? = null,
    /** Optional filters for metadata (e.g., filePath, sectionTitle) */
    val filters: Map<String, String> = emptyMap()
)

