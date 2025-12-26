package org.krypton.rag.reranker

import org.krypton.config.RagDefaults
import org.krypton.rag.SearchResult

/**
 * Builds prompts for reranking tasks.
 */
object RerankPromptBuilder {
    /**
     * Builds a prompt for reranking candidate documents based on query relevance.
     * 
     * @param query The search query
     * @param candidates List of candidate search results to rerank
     * @return Formatted prompt string
     */
    fun buildRerankPrompt(query: String, candidates: List<SearchResult>): String {
        if (candidates.isEmpty()) {
            return "Query: $query\n\nNo candidates to rerank."
        }
        
        val prompt = StringBuilder()
        prompt.append(RagDefaults.DEFAULT_RERANK_INSTRUCTION)
        prompt.append("\n\n")
        prompt.append("Query: $query\n\n")
        prompt.append("Candidate documents:\n\n")
        
        candidates.forEachIndexed { index, result ->
            val chunk = result.chunk
            prompt.append("ID: ${chunk.id}\n")
            prompt.append("Text: ${chunk.text.take(500)}${if (chunk.text.length > 500) "..." else ""}\n")
            if (chunk.metadata.isNotEmpty()) {
                val metadataStr = chunk.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
                prompt.append("Metadata: $metadataStr\n")
            }
            prompt.append("\n")
        }
        
        prompt.append(RagDefaults.DEFAULT_RERANK_RESPONSE_FORMAT)
        
        return prompt.toString()
    }
}

