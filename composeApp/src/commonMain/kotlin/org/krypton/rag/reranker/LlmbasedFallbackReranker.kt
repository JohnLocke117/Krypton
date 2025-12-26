package org.krypton.rag.reranker

import org.krypton.rag.LlamaClient
import org.krypton.rag.SearchResult
import org.krypton.rag.Reranker
import org.krypton.util.AppLogger

/**
 * Fallback reranker that uses the main generator LLM for reranking.
 * This is used when a dedicated reranker model is not available.
 */
class LlmbasedFallbackReranker(
    private val llamaClient: LlamaClient
) : Reranker {
    
    override suspend fun rerank(
        query: String,
        candidates: List<SearchResult>
    ): List<SearchResult> {
        if (candidates.isEmpty()) {
            return candidates
        }
        
        try {
            AppLogger.d("LlmbasedFallbackReranker", "Reranking ${candidates.size} candidates with generator model")
            
            val prompt = RerankPromptBuilder.buildRerankPrompt(query, candidates)
            
            val response = llamaClient.complete(prompt)
            
            val scores = RerankResponseParser.parseRerankResponse(response)
            
            if (scores.isEmpty()) {
                AppLogger.w("LlmbasedFallbackReranker", "No scores parsed from response, using original order")
                return candidates
            }
            
            // Sort by reranker scores (descending), fallback to original similarity if score missing
            val reranked = candidates.sortedByDescending { result ->
                scores[result.chunk.id] ?: result.similarity
            }
            
            AppLogger.d("LlmbasedFallbackReranker", "Reranking completed. Top score: ${scores.values.maxOrNull()}")
            return reranked
        } catch (e: Exception) {
            AppLogger.w("LlmbasedFallbackReranker", "Reranking failed: ${e.message}, using original order", e)
            return candidates
        }
    }
}

