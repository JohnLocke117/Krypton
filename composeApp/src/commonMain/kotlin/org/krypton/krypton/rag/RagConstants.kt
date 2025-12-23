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

