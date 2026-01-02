package org.krypton.config

import org.krypton.VectorBackend
import org.krypton.config.models.ChunkingConfig
import org.krypton.config.models.LlmModelConfig
import org.krypton.config.models.RetrievalConfig

/**
 * Platform-specific implementation to load OLLAMA embedding model from secrets.
 */
internal expect fun loadOllamaEmbeddingModel(): String

/**
 * Platform-specific implementation to load default embedding max tokens from secrets.
 * Returns default constant if not available in secrets.
 */
internal expect fun loadDefaultEmbeddingMaxTokens(): Int

/**
 * Default configuration values for RAG (Retrieval-Augmented Generation) system.
 * 
 * These defaults are used when user settings are missing or invalid.
 * All values can be overridden via RagSettings in the user's settings.json.
 */
object RagDefaults {
    /**
     * Default LLM model configuration for RAG.
     * 
     * Groups all LLM-related settings that are typically used together.
     */
    val DEFAULT_LLM = LlmModelConfig(
        baseUrl = LlmDefaults.DEFAULT_BASE_URL,
        modelName = loadOllamaGenerationModel(),
        temperature = LlmDefaults.DEFAULT_TEMPERATURE,
        maxTokens = null, // No limit for RAG
        timeoutMs = LlmDefaults.DEFAULT_TIMEOUT_MS
    )
    
    /**
     * Default chunking configuration.
     * 
     * Groups chunk size and overlap settings used in RAG indexing.
     * 
     * Note: maxTokens is set to 400 (instead of 512) to account for:
     * - Prefix added by embedding service ("search_document: " = 18 chars ≈ 4-5 tokens)
     * - Safety margin for models with smaller context windows (e.g., 512 token models)
     * - Token estimation inaccuracy (char-based approximation)
     * 
     * This ensures chunks work with 512-token embedding models while still being
     * large enough for good semantic retrieval.
     */
    val DEFAULT_CHUNKING = ChunkingConfig(
        targetTokens = 350,
        minTokens = 200,
        maxTokens = 400, // Reduced from 512 to account for prefix and safety margin
        overlapTokens = 50,
        charsPerToken = 4,
        minWords = 300,
        maxWords = 500,
        overlapWords = 50
    )
    
    /**
     * Default retrieval configuration.
     * 
     * Groups top-K, similarity threshold, and filtering settings.
     */
    val DEFAULT_RETRIEVAL = RetrievalConfig(
        topK = 5,
        maxK = 10,
        displayK = 5,
        similarityThreshold = 0.25f
    )
    
    /**
     * Chunking-related defaults.
     */
    object Chunking {
        /**
         * Default chunking configuration.
         */
        val DEFAULT = RagDefaults.DEFAULT_CHUNKING
        
        // Individual constants for backward compatibility
        const val DEFAULT_CHUNK_TARGET_TOKENS = 400
        const val DEFAULT_CHUNK_MIN_TOKENS = 300
        const val DEFAULT_CHUNK_MAX_TOKENS = 512
        const val DEFAULT_CHUNK_OVERLAP_TOKENS = 50
        const val DEFAULT_CHARS_PER_TOKEN = 4
        const val DEFAULT_CHUNK_MIN_WORDS = 300
        const val DEFAULT_CHUNK_MAX_WORDS = 500
        const val DEFAULT_CHUNK_OVERLAP_WORDS = 50
    }
    
    /**
     * Retrieval-related defaults.
     */
    object Retrieval {
        /**
         * Default retrieval configuration.
         */
        val DEFAULT = RagDefaults.DEFAULT_RETRIEVAL
        
        // Individual constants for backward compatibility
        const val DEFAULT_TOP_K = 5
        const val DEFAULT_MAX_K = 10
        const val DEFAULT_DISPLAY_K = 5
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.25f
    }
    
    /**
     * ChromaDB-related defaults.
     */
    object ChromaDb {
        /**
         * Default base URL for ChromaDB server.
         */
        const val DEFAULT_BASE_URL = "http://localhost:8000"
        
        /**
         * Default ChromaDB collection name.
         */
        const val DEFAULT_COLLECTION_NAME = "note_chunks"
        
        /**
         * Default ChromaDB tenant name.
         */
        const val DEFAULT_TENANT = "default"
        
        /**
         * Default ChromaDB database name.
         */
        const val DEFAULT_DATABASE = "defaultDB"
    }
    
    /**
     * Embedding-related defaults.
     */
    object Embedding {
        /**
         * Default base URL for embedding API (typically same as Ollama).
         * Loads from local.secrets.properties (OLLAMA_BASE_URL) if available.
         */
        val DEFAULT_BASE_URL: String
            get() = LlmDefaults.DEFAULT_BASE_URL
        
        /**
         * Default embedding model name.
         * Loads from local.secrets.properties (OLLAMA_EMBEDDING_MODEL) if available.
         */
        val DEFAULT_MODEL: String
            get() = loadOllamaEmbeddingModel()
        
        /**
         * Default timeout for HTTP requests to embedding API (milliseconds).
         */
        const val DEFAULT_TIMEOUT_MS = 60_000L // 1 minute
        
        /**
         * Default batch size for embedding requests.
         * Batching reduces HTTP overhead and speeds up indexing.
         */
        const val DEFAULT_BATCH_SIZE = 32
        
        /**
         * Default maximum concurrent embedding requests.
         */
        const val DEFAULT_MAX_CONCURRENT_EMBEDS = 4
        
        /**
         * Default maximum concurrent upsert operations.
         */
        const val DEFAULT_MAX_CONCURRENT_UPSERTS = 8
        
        /**
         * Maximum characters allowed per embedding input (conservative default).
         * 
         * This accounts for the prefix added by HttpEmbedder ("search_document: " = 18 chars).
         * Most embedding models support 8192 tokens (~32K chars), but we use a conservative
         * limit to prevent errors. For nomic-embed-text:v1.5, the context limit is ~8192 tokens.
         * 
         * Default: 2000 chars (≈500 tokens with 4 chars/token) provides safety margin for
         * models with smaller context windows (e.g., 512 token models).
         * 
         * Note: This is a conservative default. Models with larger context windows will
         * work fine, but models with smaller windows (like 512 tokens) need this lower limit.
         */
        const val MAX_EMBEDDING_CONTEXT_CHARS = 2000
        
        /**
         * Maximum tokens allowed per embedding input (for token-based validation).
         * 
         * Default: 500 tokens (conservative, works for 512 token models and larger).
         */
        const val MAX_EMBEDDING_CONTEXT_TOKENS = 500
        
        /**
         * Default maximum tokens for embedding sanitizer.
         * 
         * This is used as the default value for RagSettings.embeddingMaxTokens.
         * Can be overridden by loading from OLLAMA_EMBEDDING_MODEL_CONTEXT_LENGTH in secrets.
         * 
         * Default: 500 tokens (conservative, works for 512 token models and larger).
         */
        val DEFAULT_EMBEDDING_MAX_TOKENS: Int
            get() = loadDefaultEmbeddingMaxTokens()
        
        /**
         * Default maximum characters for embedding sanitizer.
         * 
         * This is used as the default value for RagSettings.embeddingMaxChars.
         * Acts as a hard upper bound fallback.
         * 
         * Default: 2000 chars (conservative fallback).
         */
        const val DEFAULT_EMBEDDING_MAX_CHARS = 2000
        
        /**
         * Prefix length for document embeddings ("search_document: ").
         */
        const val DOCUMENT_PREFIX_LENGTH = 18
        
        /**
         * Prefix length for query embeddings ("search_query: ").
         */
        const val QUERY_PREFIX_LENGTH = 16
    }
    
    /**
     * Default reranker model name.
     */
    const val DEFAULT_RERANKER_MODEL = "xitao/bge-reranker-v2-m3"
    
    /**
     * Default system prompt for RAG answer generation.
     * 
     * This prompt instructs the LLM to answer questions using only the provided context
     * from personal notes, and to be explicit when information is not available.
     */
    const val DEFAULT_RAG_SYSTEM_PROMPT = """You are an assistant that answers questions using only the provided context from personal notes.

Rules:
- Only use information from the provided context
- If the answer is not in the context, explicitly say so
- When referencing sources, mention the note or section naturally (e.g., "According to my notes on X..." or "In the section about Y...")
- Do not mention "chunks" or "chunk numbers" in your response
- Answer naturally and conversationally, as if you're recalling information from memory
- Answer concisely and accurately"""
    
    /**
     * Default instruction template for reranking prompts.
     * 
     * This template is used to build prompts for LLM-based reranking of candidate documents.
     * The template should be combined with query and candidate document information.
     */
    const val DEFAULT_RERANK_INSTRUCTION = "You are a reranker. Given a query and candidate documents, return a JSON object mapping document IDs to relevance scores (0.0 to 1.0, where 1.0 is most relevant)."
    
    /**
     * Default instruction for rerank response format.
     */
    const val DEFAULT_RERANK_RESPONSE_FORMAT = "Return only a JSON object in this format: {\"id1\": 0.95, \"id2\": 0.87, ...}\nDo not include any explanation or additional text, only the JSON object."
    
    /**
     * Default vector backend selection.
     */
    val DEFAULT_VECTOR_BACKEND = VectorBackend.CHROMADB
    
    
    /**
     * Coerces top-K value to valid range.
     * 
     * @param value The top-K value to validate
     * @return Coerced value between 1 and maxK
     */
    fun coerceTopK(value: Int): Int = value.coerceIn(1, DEFAULT_RETRIEVAL.maxK)
    
    /**
     * Coerces similarity threshold to valid range.
     * 
     * @param value The similarity threshold to validate
     * @return Coerced value between 0.0 and 1.0
     */
    fun coerceSimilarityThreshold(value: Float): Float = value.coerceIn(0f, 1f)
    
    /**
     * Coerces chunk size in tokens to valid range.
     * 
     * @param value The chunk size in tokens to validate
     * @return Coerced value between minTokens and maxTokens
     */
    fun coerceChunkTokens(value: Int): Int = 
        value.coerceIn(DEFAULT_CHUNKING.minTokens, DEFAULT_CHUNKING.maxTokens)
}

