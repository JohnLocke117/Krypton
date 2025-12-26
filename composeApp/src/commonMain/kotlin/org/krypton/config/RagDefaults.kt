package org.krypton.config

import org.krypton.VectorBackend
import org.krypton.config.models.ChunkingConfig
import org.krypton.config.models.LlmModelConfig
import org.krypton.config.models.RetrievalConfig

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
        modelName = "llama3.2:1b",
        temperature = LlmDefaults.DEFAULT_TEMPERATURE,
        maxTokens = null, // No limit for RAG
        timeoutMs = LlmDefaults.DEFAULT_TIMEOUT_MS
    )
    
    /**
     * Default chunking configuration.
     * 
     * Groups chunk size and overlap settings used in RAG indexing.
     */
    val DEFAULT_CHUNKING = ChunkingConfig(
        targetTokens = 400,
        minTokens = 300,
        maxTokens = 512,
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
         */
        const val DEFAULT_BASE_URL = LlmDefaults.DEFAULT_BASE_URL
        
        /**
         * Default embedding model name.
         */
        const val DEFAULT_MODEL = "nomic-embed-text:v1.5"
        
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

