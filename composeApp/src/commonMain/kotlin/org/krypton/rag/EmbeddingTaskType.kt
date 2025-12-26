package org.krypton.rag

/**
 * Task type for embedding generation.
 * 
 * Nomic embedding models use task-specific prefixes to optimize embeddings
 * for different use cases (document indexing vs query search).
 * 
 * @deprecated Use Embedder.embedDocument() or Embedder.embedQuery() instead
 */
@Deprecated("Use Embedder.embedDocument() or Embedder.embedQuery() instead")
enum class EmbeddingTaskType {
    /**
     * Embedding for documents/chunks being indexed.
     * Text should be prefixed with "search_document:" for Nomic models.
     */
    SEARCH_DOCUMENT,
    
    /**
     * Embedding for search queries.
     * Text should be prefixed with "search_query:" for Nomic models.
     */
    SEARCH_QUERY
}

