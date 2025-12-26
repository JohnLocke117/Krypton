package org.krypton.rag.models

import org.krypton.rag.RagChunk

/**
 * Result of a RAG query.
 * 
 * Contains the generated answer along with metadata about the retrieval process.
 */
data class RagResult(
    /** The generated answer */
    val answer: String,
    /** Chunks used to generate the answer */
    val chunks: List<RagChunk>,
    /** Whether reranking was used */
    val usedReranker: Boolean,
    /** Additional metadata about the query */
    val metadata: Map<String, String> = emptyMap()
)

