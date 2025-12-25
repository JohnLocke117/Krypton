package org.krypton.krypton.rag

/**
 * Interface for generating embeddings from text.
 */
interface Embedder {
    /**
     * Generates embeddings for a list of texts.
     * 
     * @param texts List of text strings to embed
     * @param taskType Task type for embedding (document or query)
     * @return List of embedding vectors (FloatArray), one per input text
     */
    suspend fun embed(texts: List<String>, taskType: EmbeddingTaskType = EmbeddingTaskType.SEARCH_DOCUMENT): List<FloatArray>
}

