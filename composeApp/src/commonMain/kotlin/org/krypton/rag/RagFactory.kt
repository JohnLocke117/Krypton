package org.krypton.rag

import org.krypton.VectorBackend
import org.krypton.config.RagDefaults

/**
 * Configuration for RAG components.
 */
data class RagConfig(
    val vectorBackend: VectorBackend,
    val llamaBaseUrl: String,
    val embeddingBaseUrl: String,
    val chromaBaseUrl: String = RagDefaults.ChromaDb.DEFAULT_BASE_URL,
    val chromaCollectionName: String = RagDefaults.ChromaDb.DEFAULT_COLLECTION_NAME,
    val chromaTenant: String = RagDefaults.ChromaDb.DEFAULT_TENANT,
    val chromaDatabase: String = RagDefaults.ChromaDb.DEFAULT_DATABASE,
    val llamaModel: String = RagDefaults.DEFAULT_LLM.modelName,
    val embeddingModel: String = RagDefaults.Embedding.DEFAULT_MODEL,
    val similarityThreshold: Float = RagDefaults.Retrieval.DEFAULT_SIMILARITY_THRESHOLD,
    val maxK: Int = RagDefaults.Retrieval.DEFAULT_MAX_K,
    val displayK: Int = RagDefaults.Retrieval.DEFAULT_DISPLAY_K,
    val queryRewritingEnabled: Boolean = false,
    val multiQueryEnabled: Boolean = false,
    val rerankerModel: String? = null
)

/**
 * Container for all RAG components.
 */
data class RagComponents(
    val vectorStore: VectorStore,
    val embedder: Embedder,
    val llamaClient: LlamaClient,
    val indexer: VaultIndexService,
    val ragService: RagService
)

/**
 * Platform-specific HTTP client engine factory.
 * 
 * This is an expect type that will be implemented per platform.
 * On JVM, this wraps HttpClientEngine from Ktor.
 */
expect class HttpClientEngineFactory

/**
 * Factory for creating RAG components.
 * 
 * This is a platform-specific function that will be implemented per platform.
 * On JVM, it will use Ktor's HttpClientEngine.
 * 
 * @param config RAG configuration
 * @param notesRoot Root directory containing markdown notes (null = current directory)
 * @param httpClientEngineFactory Factory for creating HTTP client engine (platform-specific)
 * @param llamaClient LlamaClient instance (optional, will be created if not provided)
 * @param reranker Reranker instance (optional, will use NoopReranker if not provided)
 */
expect fun createRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngineFactory: HttpClientEngineFactory,
    llamaClient: LlamaClient? = null,
    reranker: Reranker? = null
): RagComponents

