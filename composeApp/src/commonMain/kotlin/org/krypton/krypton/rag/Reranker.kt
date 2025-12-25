package org.krypton.krypton.rag

/**
 * Interface for reranking retrieved chunks based on query relevance.
 */
interface Reranker {
    /**
     * Reranks candidate chunks based on their relevance to the query.
     * 
     * @param query The search query
     * @param candidates List of candidate chunks to rerank
     * @return Reranked list of chunks (most relevant first)
     */
    suspend fun rerank(
        query: String,
        candidates: List<RetrievedChunk>
    ): List<RetrievedChunk>
}

/**
 * No-op reranker that returns candidates unchanged.
 * Used as a fallback when reranking is unavailable or disabled.
 */
class NoopReranker : Reranker {
    override suspend fun rerank(
        query: String,
        candidates: List<RetrievedChunk>
    ): List<RetrievedChunk> = candidates
}

