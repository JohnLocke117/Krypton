package org.krypton.rag

import org.krypton.rag.models.RagQueryOptions
import org.krypton.rag.models.RagResult

/**
 * Main interface for Retrieval-Augmented Generation operations.
 * 
 * Pure retrieval and answer generation based on indexed notes.
 * Does not modify the underlying index.
 * 
 * This interface is platform-independent and suitable for use as a tool/agent.
 */
interface RagService {
    /**
     * Answers a question using RAG.
     * 
     * Process:
     * 1. Embed the question
     * 2. Search vector store for top-k relevant chunks
     * 3. Rerank chunks (if enabled)
     * 4. Filter chunks by similarity threshold
     * 5. Generate answer using LLM with retrieved context
     * 
     * @param query The user's question
     * @param options Optional query options (topK, similarity threshold, filters)
     * @return RagResult containing answer, chunks, and metadata
     */
    suspend fun answer(
        query: String,
        options: RagQueryOptions = RagQueryOptions()
    ): RagResult
}

/**
 * Exception thrown when RAG operations fail.
 */
class RagException(message: String, cause: Throwable? = null) : Exception(message, cause)
