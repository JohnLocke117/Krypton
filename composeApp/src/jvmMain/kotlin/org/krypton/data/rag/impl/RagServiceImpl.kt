package org.krypton.data.rag.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.config.RagDefaults
import org.krypton.rag.*
import org.krypton.rag.models.RagQueryOptions
import org.krypton.rag.models.RagResult
import org.krypton.util.AppLogger

/**
 * Default implementation of RagService.
 * 
 * JVM-specific implementation that coordinates embedding, vector search, reranking, and LLM generation.
 */
class RagServiceImpl(
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val llamaClient: LlamaClient,
    private val similarityThreshold: Float = 0.25f,
    private val maxK: Int = 10,
    private val displayK: Int = 5,
    private val queryPreprocessor: QueryPreprocessor? = null,
    private val queryRewritingEnabled: Boolean = false,
    private val multiQueryEnabled: Boolean = false,
    private val reranker: Reranker = NoopReranker()
) : RagService {
    // Reranking enabled flag (can be toggled at runtime)
    @Volatile
    private var rerankingEnabled: Boolean = true
    
    override suspend fun answer(
        query: String,
        options: RagQueryOptions
    ): RagResult = withContext(Dispatchers.Default) {
        try {
            val effectiveTopK = options.topK ?: maxK
            val effectiveThreshold = (options.similarityThreshold?.toFloat() ?: similarityThreshold)
            
            // Step 0: Rewrite query if enabled
            val processedQuestion = if (queryRewritingEnabled && queryPreprocessor != null) {
                queryPreprocessor.rewriteQuery(query)
            } else {
                query
            }
            
            // Step 1: Handle multi-query or single query
            val allResults = if (multiQueryEnabled && queryPreprocessor != null) {
                // Multi-query: generate alternatives and search for each
                val queries = queryPreprocessor.generateAlternativeQueries(processedQuestion)
                AppLogger.i("RagService", "Multi-query mode: Generated ${queries.size} queries")
                
                // Embed all queries
                val queryEmbeddings = queries.map { embedder.embedQuery(it) }
                
                // Search for each query and collect results
                val allSearchResults = mutableListOf<SearchResult>()
                for (embedding in queryEmbeddings) {
                    val results = vectorStore.search(embedding, effectiveTopK, options.filters)
                    allSearchResults.addAll(results)
                }
                
                // Deduplicate by chunk ID, keeping highest similarity
                allSearchResults
                    .groupBy { it.chunk.id }
                    .mapValues { (_, results) -> results.maxByOrNull { it.similarity }!! }
                    .values
                    .toList()
            } else {
                // Single query: embed and search
                val questionEmbedding = embedder.embedQuery(processedQuestion)
                vectorStore.search(questionEmbedding, effectiveTopK, options.filters)
            }
            
            // Step 2: Rerank chunks (if reranking is enabled and reranker is available and not NoopReranker)
            val usedReranker = rerankingEnabled && reranker !is NoopReranker
            val rerankedResults = try {
                if (!usedReranker) {
                    allResults
                } else {
                    AppLogger.d("RagService", "Reranking ${allResults.size} candidates")
                    reranker.rerank(processedQuestion, allResults)
                }
            } catch (e: Exception) {
                AppLogger.w("RagService", "Reranking failed, using original order: ${e.message}", e)
                allResults
            }
            
            // Step 3: Filter by similarity threshold
            val filteredResults = rerankedResults.filter { it.similarity >= effectiveThreshold.toDouble() }
            
            // Step 4: Sort by similarity (descending) and take top displayK
            val topResults = filteredResults
                .sortedByDescending { it.similarity }
                .take(options.topK ?: displayK)
            
            // Step 5: Build prompt with context (use original question for prompt)
            val prompt = buildPrompt(query, topResults)
            
            // Step 6: Generate answer
            val answer = llamaClient.complete(prompt)
            
            // Chunks are already RagChunk in SearchResult
            val ragChunks = topResults.map { it.chunk }
            
            RagResult(
                answer = answer,
                chunks = ragChunks,
                usedReranker = usedReranker,
                metadata = mapOf(
                    "query" to query,
                    "processedQuery" to processedQuestion,
                    "retrievedCount" to allResults.size.toString(),
                    "filteredCount" to filteredResults.size.toString(),
                    "finalCount" to topResults.size.toString()
                )
            )
        } catch (e: RagException) {
            throw e
        } catch (e: Exception) {
            throw RagException("Failed to answer question: ${e.message}", e)
        }
    }
    
    /**
     * Builds a system prompt with global rules.
     */
    private fun buildSystemPrompt(): String {
        return RagDefaults.DEFAULT_RAG_SYSTEM_PROMPT
    }
    
    /**
     * Builds a user prompt with context and question.
     */
    private fun buildUserPrompt(question: String, results: List<SearchResult>): String {
        val promptBuilder = StringBuilder()
        
        val chunks = results.map { it.chunk }
        if (chunks.isEmpty()) {
            promptBuilder.append("Context: No relevant notes found.\n\n")
        } else {
            promptBuilder.append("Relevant information from my notes:\n\n")
            chunks.forEachIndexed { index, chunk ->
                // Source label with section title if available
                val sectionTitle = chunk.metadata["sectionTitle"]
                val filePath = chunk.metadata["filePath"] ?: ""
                val sourceLabel = if (sectionTitle != null) {
                    sectionTitle
                } else {
                    // Extract just the filename from path for cleaner display
                    filePath.substringAfterLast('/').substringBeforeLast('.')
                }
                
                // Format as natural note reference
                promptBuilder.append("From \"$sourceLabel\":\n")
                
                // Chunk text
                promptBuilder.append(chunk.text)
                promptBuilder.append("\n\n")
            }
        }
        
        promptBuilder.append("Question: $question")
        
        return promptBuilder.toString()
    }
    
    /**
     * Builds a prompt with context from retrieved chunks.
     * Combines system and user prompts.
     */
    private fun buildPrompt(question: String, results: List<SearchResult>): String {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(question, results)
        
        // Combine system and user prompts
        // Note: For now, we combine them as a single prompt since LlamaClient interface
        // doesn't support separate system/user prompts. This can be enhanced later.
        return "$systemPrompt\n\n$userPrompt"
    }
    
    /**
     * Sets whether reranking is enabled.
     */
    fun setRerankingEnabled(enabled: Boolean) {
        rerankingEnabled = enabled
        AppLogger.d("RagService", "Reranking ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Gets whether reranking is currently enabled.
     */
    fun isRerankingEnabled(): Boolean = rerankingEnabled
}

