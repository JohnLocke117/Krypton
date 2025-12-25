package org.krypton.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.krypton.util.AppLogger

/**
 * Retrieval-Augmented Generation service.
 * 
 * Combines vector search with LLM generation to answer questions
 * using context from indexed notes.
 */
class RagService(
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val llamaClient: LlamaClient,
    private val similarityThreshold: Float = 0.25f,
    private val maxK: Int = 10,
    private val displayK: Int = 5,
    private val queryPreprocessor: QueryPreprocessor? = null,
    private val queryRewritingEnabled: Boolean = false,
    private val multiQueryEnabled: Boolean = false
) {
    /**
     * Answers a question using RAG.
     * 
     * Process:
     * 1. Embed the question
     * 2. Search vector store for top-k relevant chunks (up to maxK)
     * 3. Filter chunks by similarity threshold
     * 4. Take top displayK chunks
     * 5. Build a prompt with context from chunks
     * 6. Generate answer using LLM
     * 
     * @param question The user's question
     * @return The generated answer
     */
    suspend fun ask(question: String): String = withContext(Dispatchers.Default) {
        try {
            // Step 0: Rewrite query if enabled
            val processedQuestion = if (queryRewritingEnabled && queryPreprocessor != null) {
                queryPreprocessor.rewriteQuery(question)
            } else {
                question
            }
            
            // Step 1: Handle multi-query or single query
            val allResults = if (multiQueryEnabled && queryPreprocessor != null) {
                // Multi-query: generate alternatives and search for each
                val queries = queryPreprocessor.generateAlternativeQueries(processedQuestion)
                AppLogger.i("RagService", "═══════════════════════════════════════════════════════════")
                AppLogger.i("RagService", "Multi-query mode: Generated ${queries.size} queries:")
                queries.forEachIndexed { index, query ->
                    AppLogger.i("RagService", "  Query ${index + 1}: \"$query\"")
                }
                AppLogger.i("RagService", "═══════════════════════════════════════════════════════════")
                
                // Embed all queries
                val queryEmbeddings = embedder.embed(queries, EmbeddingTaskType.SEARCH_QUERY)
                
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
                
                AppLogger.d("RagService", "Multi-query: ${allSearchResults.size} total results, ${deduplicated.size} after deduplication")
                deduplicated
            } else {
                // Single query: embed and search
                val questionEmbedding = embedder.embed(listOf(processedQuestion), EmbeddingTaskType.SEARCH_QUERY).firstOrNull()
                    ?: throw RagException("Failed to generate embedding for question")
                vectorStore.search(questionEmbedding, maxK)
            }
            
            // Step 2: Filter by similarity threshold
            val filteredResults = allResults.filter { it.similarity >= similarityThreshold }
            
            // Step 3: Sort by similarity (descending) and take top displayK
            val topResults = filteredResults
                .sortedByDescending { it.similarity }
                .take(displayK)
            
            // Log retrieval metrics
            logRetrieval(processedQuestion, allResults, filteredResults, topResults)
            
            // Step 4: Build prompt with context (use original question for prompt)
            val prompt = buildPrompt(question, topResults)
            
            // Step 5: Generate answer
            llamaClient.complete(prompt)
        } catch (e: RagException) {
            throw e
        } catch (e: Exception) {
            throw RagException("Failed to answer question: ${e.message}", e)
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
        AppLogger.d("RagService", "Query: $query")
        AppLogger.d("RagService", "Retrieved: ${allResults.size} chunks")
        AppLogger.d("RagService", "Similarities: ${allResults.map { String.format("%.3f", it.similarity) }.joinToString(", ")}")
        AppLogger.d("RagService", "After threshold (${similarityThreshold}): ${filteredResults.size} chunks")
        AppLogger.d("RagService", "Final chunks used: ${finalResults.size}")
        if (finalResults.isNotEmpty()) {
            AppLogger.d("RagService", "Final similarities: ${finalResults.map { String.format("%.3f", it.similarity) }.joinToString(", ")}")
        }
    }
    
    /**
     * Builds a system prompt with global rules.
     */
    private fun buildSystemPrompt(): String {
        return """You are an assistant that answers questions using only the provided context from personal notes.

Rules:
- Only use information from the provided context
- If the answer is not in the context, explicitly say so
- When referencing sources, mention the note or section naturally (e.g., "According to my notes on X..." or "In the section about Y...")
- Do not mention "chunks" or "chunk numbers" in your response
- Answer naturally and conversationally, as if you're recalling information from memory
- Answer concisely and accurately"""
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
                val sourceLabel = if (chunk.sectionTitle != null) {
                    chunk.sectionTitle
                } else {
                    // Extract just the filename from path for cleaner display
                    chunk.filePath.substringAfterLast('/').substringBeforeLast('.')
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
}

/**
 * Exception thrown when RAG operations fail.
 */
class RagException(message: String, cause: Throwable? = null) : Exception(message, cause)

