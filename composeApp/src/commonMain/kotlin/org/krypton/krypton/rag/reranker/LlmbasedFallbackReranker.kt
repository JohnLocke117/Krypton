package org.krypton.krypton.rag.reranker

import org.krypton.krypton.rag.LlamaClient
import org.krypton.krypton.rag.RetrievedChunk
import org.krypton.krypton.rag.Reranker
import org.krypton.krypton.util.AppLogger

/**
 * Fallback reranker that uses the main generator LLM for reranking.
 * This is used when a dedicated reranker model is not available.
 */
class LlmbasedFallbackReranker(
    private val llamaClient: LlamaClient,
    private val generatorModel: String
) : Reranker {
    
    override suspend fun rerank(
        query: String,
        candidates: List<RetrievedChunk>
    ): List<RetrievedChunk> {
        if (candidates.isEmpty()) {
            return candidates
        }
        
        try {
            AppLogger.d("LlmbasedFallbackReranker", "Reranking ${candidates.size} candidates with generator model: $generatorModel")
            
            val prompt = RerankPromptBuilder.buildRerankPrompt(query, candidates)
            
            val response = llamaClient.complete(
                model = generatorModel,
                prompt = prompt,
                temperature = 0.0
            )
            
            val scores = RerankResponseParser.parseRerankResponse(response)
            
            if (scores.isEmpty()) {
                AppLogger.w("LlmbasedFallbackReranker", "No scores parsed from response, using original order")
                return candidates
            }
            
            // Sort by reranker scores (descending), fallback to original similarity if score missing
            val reranked = candidates.sortedByDescending { chunk ->
                scores[chunk.id] ?: chunk.similarity
            }
            
            AppLogger.d("LlmbasedFallbackReranker", "Reranking completed. Top score: ${scores.values.maxOrNull()}")
            return reranked
        } catch (e: Exception) {
            AppLogger.w("LlmbasedFallbackReranker", "Reranking failed: ${e.message}, using original order", e)
            return candidates
        }
    }
}

