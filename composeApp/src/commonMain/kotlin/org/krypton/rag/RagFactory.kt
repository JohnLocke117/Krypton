package org.krypton.rag

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * 
 * Observes settings changes and updates LlamaClient when model/baseUrl changes.
 */
class RagComponents(
    val vectorStore: VectorStore,
    val embedder: Embedder,
    llamaClient: LlamaClient,
    val indexer: VaultIndexService,
    val ragService: RagService,
    private val settingsRepository: org.krypton.data.repository.SettingsRepository? = null,
    private val llamaClientFactory: (() -> org.krypton.rag.LlamaClient)? = null
) {
    private var _llamaClient: LlamaClient = llamaClient
    private var lastModel: String? = null
    private var lastBaseUrl: String? = null
    
    init {
        // Initialize last known values
        settingsRepository?.let { repo ->
            val settings = repo.settingsFlow.value
            lastModel = settings.llm.ollamaModel
            lastBaseUrl = settings.llm.ollamaBaseUrl
            
            // Observe settings changes and update client when model/baseUrl changes
            CoroutineScope(Dispatchers.Default).launch {
                repo.settingsFlow.collect { settings ->
                    val newModel = settings.llm.ollamaModel
                    val newBaseUrl = settings.llm.ollamaBaseUrl
                    
                    if (newModel != lastModel || newBaseUrl != lastBaseUrl) {
                        lastModel = newModel
                        lastBaseUrl = newBaseUrl
                        llamaClientFactory?.let { factory ->
                            _llamaClient = factory()
                            org.krypton.util.AppLogger.d("RagComponents", "LlamaClient updated: model=$newModel, baseUrl=$newBaseUrl")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Gets the current LlamaClient.
     * The client is automatically updated when settings change.
     */
    val llamaClient: LlamaClient
        get() = _llamaClient
}

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
 * @param vectorStore VectorStore instance (optional, will be created if not provided - for backward compatibility)
 * @param llamaClient LlamaClient instance (optional, will be created if not provided)
 * @param reranker Reranker instance (optional, will use NoopReranker if not provided)
 * @param embedder Embedder instance (optional, will be created if not provided)
 * @param settingsRepository SettingsRepository for observing settings changes (optional)
 * @param llamaClientFactory Factory function for creating LlamaClient when settings change (optional)
 */
expect fun createRagComponents(
    config: RagConfig,
    notesRoot: String?,
    httpClientEngineFactory: HttpClientEngineFactory,
    vectorStore: VectorStore? = null,
    llamaClient: LlamaClient? = null,
    reranker: Reranker? = null,
    embedder: Embedder? = null,
    settingsRepository: org.krypton.data.repository.SettingsRepository? = null,
    llamaClientFactory: (() -> LlamaClient)? = null
): RagComponents

