package org.krypton.krypton.rag

/**
 * Interface for generating embeddings from text.
 */
interface Embedder {
    /**
     * Generates embeddings for a list of texts.
     * 
     * @param texts List of text strings to embed
     * @return List of embedding vectors (FloatArray), one per input text
     */
    suspend fun embed(texts: List<String>): List<FloatArray>
}

