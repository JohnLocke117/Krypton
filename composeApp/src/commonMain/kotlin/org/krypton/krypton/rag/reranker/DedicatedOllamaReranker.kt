package org.krypton.krypton.rag.reranker

import org.krypton.krypton.rag.LlamaClient
import org.krypton.krypton.rag.RetrievedChunk
import org.krypton.krypton.rag.Reranker
import org.krypton.krypton.util.AppLogger

/**
 * Reranker that uses a dedicated reranker model via Ollama.
 */
class DedicatedOllamaReranker(
    private val llamaClient: LlamaClient,
    private val modelName: String
) : Reranker {
    
    override suspend fun rerank(
        query: String,
        candidates: List<RetrievedChunk>
    ): List<RetrievedChunk> {
        if (candidates.isEmpty()) {
            return candidates
        }
        
        try {
            AppLogger.d("DedicatedOllamaReranker", "Reranking ${candidates.size} candidates with model: $modelName")
            
            val prompt = RerankPromptBuilder.buildRerankPrompt(query, candidates)
            
            val response = llamaClient.complete(
                model = modelName,
                prompt = prompt,
                temperature = 0.0
            )
            
            val scores = RerankResponseParser.parseRerankResponse(response)
            
            if (scores.isEmpty()) {
                AppLogger.w("DedicatedOllamaReranker", "No scores parsed from response, using original order")
                return candidates
            }
            
            // Sort by reranker scores (descending), fallback to original similarity if score missing
            val reranked = candidates.sortedByDescending { chunk ->
                scores[chunk.id] ?: chunk.similarity
            }
            
            AppLogger.d("DedicatedOllamaReranker", "Reranking completed. Top score: ${scores.values.maxOrNull()}")
            return reranked
        } catch (e: Exception) {
            AppLogger.w("DedicatedOllamaReranker", "Reranking failed: ${e.message}, using original order", e)
            return candidates
        }
    }
}

