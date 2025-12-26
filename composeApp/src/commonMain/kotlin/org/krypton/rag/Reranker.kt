package org.krypton.rag

/**
 * Interface for reranking retrieved chunks based on query relevance.
 * 
 * This interface is platform-independent and suitable for use across different platforms.
 */
interface Reranker {
    /**
     * Reranks candidate chunks based on their relevance to the query.
     * 
     * @param query The search query
     * @param candidates List of candidate search results to rerank
     * @return Reranked list of search results (most relevant first)
     */
    suspend fun rerank(
        query: String,
        candidates: List<SearchResult>
    ): List<SearchResult>
}

/**
 * No-op reranker that returns candidates unchanged.
 * Used as a fallback when reranking is unavailable or disabled.
 */
class NoopReranker : Reranker {
    override suspend fun rerank(
        query: String,
        candidates: List<SearchResult>
    ): List<SearchResult> = candidates
}

