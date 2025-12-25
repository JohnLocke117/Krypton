package org.krypton.krypton.config

import org.krypton.krypton.VectorBackend

/**
 * Default configuration values for RAG (Retrieval-Augmented Generation) system.
 * 
 * These defaults are used when user settings are missing or invalid.
 * All values can be overridden via RagSettings in the user's settings.json.
 */
object RagDefaults {
    /**
     * Default base URL for Ollama LLM API.
     */
    const val DEFAULT_LLAMA_BASE_URL = "http://localhost:11434"
    
    /**
     * Default base URL for embedding API (typically same as Ollama).
     */
    const val DEFAULT_EMBEDDING_BASE_URL = "http://localhost:11434"
    
    /**
     * Default LLM model name for text generation.
     */
    const val DEFAULT_LLAMA_MODEL = "llama3.2:1b"
    
    /**
     * Default embedding model name.
     */
    const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text:v1.5"
    
    /**
     * Default reranker model name.
     */
    const val DEFAULT_RERANKER_MODEL = "xitao/bge-reranker-v2-m3"
    
    /**
     * Default vector backend selection.
     */
    val DEFAULT_VECTOR_BACKEND = VectorBackend.CHROMADB
    
    /**
     * Default base URL for ChromaDB server.
     */
    const val DEFAULT_CHROMA_BASE_URL = "http://localhost:8000"
    
    /**
     * Default ChromaDB collection name.
     */
    const val DEFAULT_CHROMA_COLLECTION_NAME = "note_chunks"
    
    /**
     * Default ChromaDB tenant name.
     */
    const val DEFAULT_CHROMA_TENANT = "default"
    
    /**
     * Default ChromaDB database name.
     */
    const val DEFAULT_CHROMA_DATABASE = "defaultDB"
    
    /**
     * Default number of top-K chunks to retrieve during RAG queries.
     */
    const val DEFAULT_TOP_K = 5
    
    /**
     * Default chunk size in words (target minimum).
     */
    const val DEFAULT_CHUNK_MIN_WORDS = 300
    
    /**
     * Default chunk size in words (target maximum).
     */
    const val DEFAULT_CHUNK_MAX_WORDS = 500
    
    /**
     * Default chunk overlap in words (for better context continuity).
     */
    const val DEFAULT_CHUNK_OVERLAP_WORDS = 50
    
    /**
     * Default similarity threshold for vector search (0.0 to 1.0).
     * Chunks below this threshold are filtered out.
     */
    const val DEFAULT_SIMILARITY_THRESHOLD = 0.25f
    
    /**
     * Default maximum K for retrieval (before filtering).
     */
    const val DEFAULT_MAX_K = 10
    
    /**
     * Default display K (number of chunks to use in prompt after filtering).
     */
    const val DEFAULT_DISPLAY_K = 5
    
    /**
     * Default timeout for HTTP requests to LLM API (milliseconds).
     */
    const val DEFAULT_LLM_TIMEOUT_MS = 120_000L // 2 minutes
    
    /**
     * Default timeout for HTTP requests to embedding API (milliseconds).
     */
    const val DEFAULT_EMBEDDING_TIMEOUT_MS = 60_000L // 1 minute
    
    /**
     * Default number of retry attempts for failed HTTP requests.
     */
    const val DEFAULT_MAX_RETRIES = 3
    
    /**
     * Default delay between retries (milliseconds).
     */
    const val DEFAULT_RETRY_DELAY_MS = 1_000L
    
    /**
     * Default batch size for embedding requests.
     * Batching reduces HTTP overhead and speeds up indexing.
     */
    const val DEFAULT_EMBEDDING_BATCH_SIZE = 32
    
    /**
     * Default target chunk size in tokens.
     */
    const val DEFAULT_CHUNK_TARGET_TOKENS = 400
    
    /**
     * Default minimum chunk size in tokens.
     */
    const val DEFAULT_CHUNK_MIN_TOKENS = 300
    
    /**
     * Default maximum chunk size in tokens.
     */
    const val DEFAULT_CHUNK_MAX_TOKENS = 512
    
    /**
     * Default chunk overlap in tokens (10-20% of target).
     */
    const val DEFAULT_CHUNK_OVERLAP_TOKENS = 50
    
    /**
     * Default characters per token estimation (simple approximation).
     */
    const val DEFAULT_CHARS_PER_TOKEN = 4
    
    /**
     * Default maximum concurrent embedding requests.
     */
    const val DEFAULT_MAX_CONCURRENT_EMBEDS = 4
    
    /**
     * Default maximum concurrent upsert operations.
     */
    const val DEFAULT_MAX_CONCURRENT_UPSERTS = 8
}

