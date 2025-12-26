package org.krypton.rag

import org.krypton.rag.models.Embedding

/**
 * Interface for generating embeddings from text.
 * 
 * This interface is platform-independent and suitable for use across different platforms.
 */
interface Embedder {
    /**
     * Generates embeddings for documents (for indexing).
     * 
     * @param texts List of text strings to embed
     * @return List of embedding vectors, one per input text
     */
    suspend fun embedDocument(texts: List<String>): List<Embedding>
    
    /**
     * Generates embedding for a query (for search).
     * 
     * @param text The query text to embed
     * @return Embedding vector
     */
    suspend fun embedQuery(text: String): Embedding
    
    /**
     * Generates embeddings for a list of texts (legacy method for backward compatibility).
     * 
     * @deprecated Use embedDocument or embedQuery instead
     * @param texts List of text strings to embed
     * @param taskType Task type for embedding (document or query)
     * @return List of embedding vectors (FloatArray), one per input text
     */
    @Deprecated("Use embedDocument or embedQuery instead", ReplaceWith("if (taskType == EmbeddingTaskType.SEARCH_DOCUMENT) embedDocument(texts).map { it.vector.toFloatArray() } else texts.map { embedQuery(it).vector.toFloatArray() }"))
    suspend fun embed(texts: List<String>, taskType: EmbeddingTaskType = EmbeddingTaskType.SEARCH_DOCUMENT): List<FloatArray>
}

