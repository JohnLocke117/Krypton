package org.krypton.rag

import org.krypton.util.AppLogger

/**
 * Preprocesses queries for improved retrieval.
 * 
 * Supports query rewriting and multi-query generation.
 */
class QueryPreprocessor(
    private val llamaClient: LlamaClient
) {
    /**
     * Rewrites a query to be clearer and more specific for searching personal notes.
     * 
     * @param original The original user query
     * @return The rewritten query
     */
    suspend fun rewriteQuery(original: String): String {
        val prompt = """Rewrite this query to be clear and specific for searching personal notes. Remove chit-chat. Expand acronyms if needed. Output only the rewritten query, nothing else.

Original query: $original

Rewritten query:"""
        
        try {
            val rewritten = llamaClient.complete(prompt).trim()
            AppLogger.d("QueryPreprocessor", "Query rewritten: '$original' -> '$rewritten'")
            return rewritten.ifBlank { original }
        } catch (e: Exception) {
            AppLogger.w("QueryPreprocessor", "Failed to rewrite query, using original: ${e.message}")
            return original
        }
    }
    
    /**
     * Generates alternative phrasings of a query for multi-query retrieval.
     * 
     * @param original The original query
     * @return List of alternative queries (including original)
     */
    suspend fun generateAlternativeQueries(original: String): List<String> {
        val prompt = """Generate 2-3 alternative phrasings of this query for semantic search.

Rules:
- Output ONLY the queries, one per line
- No explanations, no prefixes, no numbering
- Each line must be a complete search query
- Do not include the original query in your output

Original query: $original

Alternative queries (one per line, no other text):"""
        
        try {
            val response = llamaClient.complete(prompt).trim()
            
            // Parse and clean the response
            val alternatives = response.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                // Remove common prefixes and explanatory text
                .map { line ->
                    // Remove numbered prefixes like "1. ", "2. ", "- ", "* "
                    line.replaceFirst(Regex("^\\d+[.)]\\s*"), "")
                        .replaceFirst(Regex("^[-*]\\s*"), "")
                        .trim()
                }
                // Filter out lines that look like explanations or instructions
                .filter { line ->
                    val lower = line.lowercase()
                    !lower.contains("here are") &&
                    !lower.contains("alternative") &&
                    !lower.contains("phrasing") &&
                    !lower.contains("query:") &&
                    !lower.contains("queries:") &&
                    !lower.startsWith("original") &&
                    line.length > 5 // Filter out very short lines that are likely not queries
                }
                .take(3) // Limit to 3 alternatives
            
            val allQueries = listOf(original) + alternatives
            AppLogger.d("QueryPreprocessor", "Generated ${alternatives.size} alternative queries for: '$original'")
            return allQueries.distinct()
        } catch (e: Exception) {
            AppLogger.w("QueryPreprocessor", "Failed to generate alternative queries, using original only: ${e.message}")
            return listOf(original)
        }
    }
}

