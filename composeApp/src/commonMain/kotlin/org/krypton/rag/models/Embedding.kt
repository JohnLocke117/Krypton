package org.krypton.rag.models

/**
 * Represents an embedding vector.
 * 
 * Embeddings are used for semantic search and similarity matching.
 */
data class Embedding(
    /** The embedding vector as a list of floats */
    val vector: List<Float>
)

