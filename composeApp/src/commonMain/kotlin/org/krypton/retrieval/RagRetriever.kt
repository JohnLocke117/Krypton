package org.krypton.retrieval

import org.krypton.rag.*
import org.krypton.util.AppLogger
import org.krypton.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retrieves relevant chunks from local notes using RAG.
 * 
 * Extracts the retrieval logic from RagService, returning chunks
 * without generating the final answer.
 */
class RagRetriever(
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val similarityThreshold: Float = 0.25f,
    private val maxK: Int = 10,
    private val displayK: Int = 5,
    private val queryPreprocessor: QueryPreprocessor? = null,
    private val queryRewritingEnabled: Boolean = false,
    private val multiQueryEnabled: Boolean = false,
    private val reranker: Reranker = NoopReranker(),
    private val settingsRepository: SettingsRepository? = null
) {
    
    /**
     * Retrieves relevant chunks for the given query.
     * 
     * Process:
     * 1. Embed the question (with optional query rewriting)
     * 2. Search vector store for top-k relevant chunks (up to maxK)
     * 3. Rerank chunks using reranker (if available and enabled)
     * 4. Filter chunks by similarity threshold
     * 5. Take top displayK chunks
     * 
     * @param question The user's question
     * @return List of retrieved chunks
     */
    suspend fun retrieveChunks(question: String): List<RagChunk> = withContext(Dispatchers.Default) {
        try {
            // Read current settings dynamically if SettingsRepository is available
            val currentSettings = settingsRepository?.settingsFlow?.value?.rag
            // Always prefer settings from repository; only fall back to constructor param if repository is unavailable
            val shouldUseMultiQuery = if (settingsRepository != null) {
                currentSettings?.multiQueryEnabled ?: false
            } else {
                multiQueryEnabled
            }
            val shouldUseReranking = currentSettings?.rerankingEnabled ?: false
            
            // Log settings being used for debugging
            AppLogger.d("RagRetriever", "Settings: multiQuery=$shouldUseMultiQuery, reranking=$shouldUseReranking (from settings: multiQuery=${currentSettings?.multiQueryEnabled}, reranking=${currentSettings?.rerankingEnabled})")
            
            // Step 0: Rewrite query if enabled
            val processedQuestion = if (queryRewritingEnabled && queryPreprocessor != null) {
                queryPreprocessor.rewriteQuery(question)
            } else {
                question
            }
            
            // Step 1: Handle multi-query or single query
            val allResults = if (shouldUseMultiQuery && queryPreprocessor != null) {
                // Multi-query: generate alternatives and search for each
                val queries = queryPreprocessor.generateAlternativeQueries(processedQuestion)
                AppLogger.i("RagRetriever", "═══════════════════════════════════════════════════════════")
                AppLogger.i("RagRetriever", "Multi-query mode: Generated ${queries.size} queries:")
                queries.forEachIndexed { index, query ->
                    AppLogger.i("RagRetriever", "  Query ${index + 1}: \"$query\"")
                }
                AppLogger.i("RagRetriever", "═══════════════════════════════════════════════════════════")
                
                // Embed all queries
                val queryEmbeddings = queries.map { embedder.embedQuery(it) }
                
                // Search for each query and collect results
                val allSearchResults = mutableListOf<SearchResult>()
                for (embedding in queryEmbeddings) {
                    val results = vectorStore.search(embedding, maxK)
                    allSearchResults.addAll(results)
                }
                
                // Deduplicate by chunk ID, keeping highest similarity
                val deduplicated = allSearchResults
                    .groupBy { it.chunk.id }
                    .mapValues { (_, results) -> results.maxByOrNull { it.similarity }!! }
                    .values
                    .toList()
                
                AppLogger.d("RagRetriever", "Multi-query: ${allSearchResults.size} total results, ${deduplicated.size} after deduplication")
                deduplicated
            } else {
                // Single query: embed and search
                val questionEmbedding = embedder.embedQuery(processedQuestion)
                vectorStore.search(questionEmbedding, maxK)
            }
            
            // Step 2: Rerank chunks (if reranking is enabled and reranker is available and not NoopReranker)
            val rerankedResults = try {
                if (!shouldUseReranking || reranker is NoopReranker) {
                    // Skip reranking if disabled or using NoopReranker
                    allResults
                } else {
                    AppLogger.d("RagRetriever", "Reranking ${allResults.size} candidates")
                    reranker.rerank(processedQuestion, allResults)
                }
            } catch (e: Exception) {
                AppLogger.w("RagRetriever", "Reranking failed, using original order: ${e.message}", e)
                allResults
            }
            
            // Step 3: Filter by similarity threshold
            val filteredResults = rerankedResults.filter { it.similarity >= similarityThreshold }
            
            // Step 4: Sort by similarity (descending) and take top displayK
            val topResults = filteredResults
                .sortedByDescending { it.similarity }
                .take(displayK)
            
            // Log retrieval metrics
            logRetrieval(processedQuestion, allResults, filteredResults, topResults)
            
            // Convert to RagChunk list (extract chunks from SearchResult)
            topResults.map { it.chunk }
        } catch (e: RagException) {
            throw e
        } catch (e: Exception) {
            throw RagException("Failed to retrieve chunks: ${e.message}", e)
        }
    }
    
    /**
     * Logs retrieval metrics for evaluation.
     */
    private fun logRetrieval(
        query: String,
        allResults: List<SearchResult>,
        filteredResults: List<SearchResult>,
        finalResults: List<SearchResult>
    ) {
        AppLogger.d("RagRetriever", "Query: $query")
        AppLogger.d("RagRetriever", "Retrieved: ${allResults.size} chunks")
        AppLogger.d("RagRetriever", "Similarities: ${allResults.map { String.format("%.3f", it.similarity) }.joinToString(", ")}")
        AppLogger.d("RagRetriever", "After threshold (${similarityThreshold}): ${filteredResults.size} chunks")
        AppLogger.d("RagRetriever", "Final chunks used: ${finalResults.size}")
        if (finalResults.isNotEmpty()) {
            AppLogger.d("RagRetriever", "Final similarities: ${finalResults.map { String.format("%.3f", it.similarity) }.joinToString(", ")}")
        }
    }
    
    /**
     * Sets whether reranking is enabled.
     * @deprecated Use settings repository instead. This method is kept for backward compatibility.
     */
    @Deprecated("Use settings repository to control reranking")
    fun setRerankingEnabled(enabled: Boolean) {
        AppLogger.w("RagRetriever", "setRerankingEnabled is deprecated. Use settings repository instead.")
    }
    
    /**
     * Gets whether reranking is currently enabled.
     * @deprecated Use settings repository instead. This method is kept for backward compatibility.
     */
    @Deprecated("Use settings repository to control reranking")
    fun isRerankingEnabled(): Boolean {
        return settingsRepository?.settingsFlow?.value?.rag?.rerankingEnabled ?: false
    }
}

