package org.krypton.krypton.rag

/**
 * Constants for RAG-related model names and default values.
 * 
 * Centralizes hard-coded model names to make them easier to maintain and update.
 */
object RagConstants {
    /**
     * Default LLM model name for text generation.
     */
    const val DEFAULT_LLAMA_MODEL = "llama3.2:1b"
    
    /**
     * Default embedding model name.
     */
    const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text:v1.5"
}

