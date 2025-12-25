package org.krypton.krypton.rag.reranker

import org.krypton.krypton.rag.RetrievedChunk

/**
 * Builds prompts for reranking tasks.
 */
object RerankPromptBuilder {
    /**
     * Builds a prompt for reranking candidate documents based on query relevance.
     * 
     * @param query The search query
     * @param candidates List of candidate chunks to rerank
     * @return Formatted prompt string
     */
    fun buildRerankPrompt(query: String, candidates: List<RetrievedChunk>): String {
        if (candidates.isEmpty()) {
            return "Query: $query\n\nNo candidates to rerank."
        }
        
        val prompt = StringBuilder()
        prompt.append("You are a reranker. Given a query and candidate documents, return a JSON object mapping document IDs to relevance scores (0.0 to 1.0, where 1.0 is most relevant).\n\n")
        prompt.append("Query: $query\n\n")
        prompt.append("Candidate documents:\n\n")
        
        candidates.forEachIndexed { index, chunk ->
            prompt.append("ID: ${chunk.id}\n")
            prompt.append("Text: ${chunk.text.take(500)}${if (chunk.text.length > 500) "..." else ""}\n")
            if (chunk.metadata.isNotEmpty()) {
                val metadataStr = chunk.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
                prompt.append("Metadata: $metadataStr\n")
            }
            prompt.append("\n")
        }
        
        prompt.append("Return only a JSON object in this format: {\"id1\": 0.95, \"id2\": 0.87, ...}\n")
        prompt.append("Do not include any explanation or additional text, only the JSON object.")
        
        return prompt.toString()
    }
}

