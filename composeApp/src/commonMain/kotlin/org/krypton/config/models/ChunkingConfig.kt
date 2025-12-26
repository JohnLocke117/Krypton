package org.krypton.config.models

/**
 * Configuration for text chunking parameters.
 * 
 * Groups chunk size and overlap settings used in RAG indexing.
 */
data class ChunkingConfig(
    /** Target chunk size in tokens */
    val targetTokens: Int,
    /** Minimum chunk size in tokens */
    val minTokens: Int,
    /** Maximum chunk size in tokens */
    val maxTokens: Int,
    /** Overlap between chunks in tokens */
    val overlapTokens: Int,
    /** Characters per token estimation */
    val charsPerToken: Int,
    /** Minimum chunk size in words (legacy) */
    val minWords: Int,
    /** Maximum chunk size in words (legacy) */
    val maxWords: Int,
    /** Overlap in words (legacy) */
    val overlapWords: Int
)

