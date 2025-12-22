package org.krypton.krypton.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retrieval-Augmented Generation service.
 * 
 * Combines vector search with LLM generation to answer questions
 * using context from indexed notes.
 */
class RagService(
    private val embedder: Embedder,
    private val vectorStore: VectorStore,
    private val llamaClient: LlamaClient
) {
    /**
     * Answers a question using RAG.
     * 
     * Process:
     * 1. Embed the question
     * 2. Search vector store for top-k relevant chunks
     * 3. Build a prompt with context from chunks
     * 4. Generate answer using LLM
     * 
     * @param question The user's question
     * @param topK Number of chunks to retrieve (default: 5)
     * @return The generated answer
     */
    suspend fun ask(question: String, topK: Int = 5): String = withContext(Dispatchers.Default) {
        try {
            // Step 1: Embed the question
            val questionEmbedding = embedder.embed(listOf(question)).firstOrNull()
                ?: throw RagException("Failed to generate embedding for question")
            
            // Step 2: Search for relevant chunks
            val relevantChunks = vectorStore.search(questionEmbedding, topK)
            
            // Step 3: Build prompt with context
            val prompt = buildPrompt(question, relevantChunks)
            
            // Step 4: Generate answer
            llamaClient.complete(prompt)
        } catch (e: RagException) {
            throw e
        } catch (e: Exception) {
            throw RagException("Failed to answer question: ${e.message}", e)
        }
    }
    
    /**
     * Builds a prompt with context from retrieved chunks.
     */
    private fun buildPrompt(question: String, chunks: List<NoteChunk>): String {
        val promptBuilder = StringBuilder()
        
        promptBuilder.append("You are an assistant that answers questions strictly using the provided context from my personal notes.\n\n")
        
        if (chunks.isEmpty()) {
            promptBuilder.append("Context: No relevant notes found.\n\n")
        } else {
            promptBuilder.append("Context:\n")
            chunks.forEachIndexed { index, chunk ->
                promptBuilder.append("\n[From ${chunk.filePath}, lines ${chunk.startLine}-${chunk.endLine}]\n")
                promptBuilder.append(chunk.text)
                promptBuilder.append("\n")
            }
            promptBuilder.append("\n")
        }
        
        promptBuilder.append("Question: $question\n\n")
        promptBuilder.append("If the answer is not present in the context, say you don't know based on the notes.\n")
        promptBuilder.append("Answer concisely.\n")
        
        return promptBuilder.toString()
    }
}

/**
 * Exception thrown when RAG operations fail.
 */
class RagException(message: String, cause: Throwable? = null) : Exception(message, cause)

