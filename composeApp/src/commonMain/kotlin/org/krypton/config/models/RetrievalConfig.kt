package org.krypton.config.models

/**
 * Configuration for RAG retrieval parameters.
 * 
 * Groups top-K, similarity threshold, and filtering settings.
 */
data class RetrievalConfig(
    /** Number of top-K chunks to retrieve */
    val topK: Int,
    /** Maximum K for retrieval (before filtering) */
    val maxK: Int,
    /** Display K (number of chunks to use after filtering) */
    val displayK: Int,
    /** Similarity threshold (0.0 to 1.0) */
    val similarityThreshold: Float
)

