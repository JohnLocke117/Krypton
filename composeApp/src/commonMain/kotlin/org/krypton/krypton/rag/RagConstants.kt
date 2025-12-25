package org.krypton.krypton.rag

import org.krypton.krypton.config.RagDefaults

/**
 * Constants for RAG-related model names and default values.
 * 
 * Centralizes hard-coded model names to make them easier to maintain and update.
 * 
 * @deprecated Use RagDefaults directly instead. This object is kept for backward compatibility.
 */
@Deprecated("Use RagDefaults directly", ReplaceWith("RagDefaults"))
object RagConstants {
    /**
     * Default LLM model name for text generation.
     */
    const val DEFAULT_LLAMA_MODEL = RagDefaults.DEFAULT_LLAMA_MODEL
    
    /**
     * Default embedding model name.
     */
    const val DEFAULT_EMBEDDING_MODEL = RagDefaults.DEFAULT_EMBEDDING_MODEL
}

/**
 * Task type for embedding generation.
 * 
 * Nomic embedding models use task-specific prefixes to optimize embeddings
 * for different use cases (document indexing vs query search).
 */
enum class EmbeddingTaskType {
    /**
     * Embedding for documents/chunks being indexed.
     * Text should be prefixed with "search_document:" for Nomic models.
     */
    SEARCH_DOCUMENT,
    
    /**
     * Embedding for search queries.
     * Text should be prefixed with "search_query:" for Nomic models.
     */
    SEARCH_QUERY
}

