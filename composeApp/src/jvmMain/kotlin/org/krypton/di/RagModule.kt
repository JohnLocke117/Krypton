package org.krypton.di

import io.ktor.client.engine.*
import org.krypton.config.RagDefaults
import org.krypton.data.rag.impl.HttpOllamaModelRegistry
import org.krypton.data.rag.impl.OllamaModelRegistry
import org.krypton.rag.*
import org.krypton.data.rag.impl.HttpLlamaClient
import org.krypton.data.rag.impl.GeminiClient
import org.krypton.data.rag.impl.HttpEmbedder
import org.krypton.data.rag.impl.GeminiEmbedder
import org.krypton.data.rag.impl.ChromaDBVectorStore
import org.krypton.data.rag.impl.ChromaCloudVectorStore
import org.krypton.data.rag.impl.RagServiceImpl
import org.krypton.util.SecretsLoader
import org.krypton.VectorBackend
import org.krypton.LlmProvider
import org.krypton.rag.reranker.DedicatedOllamaReranker
import org.krypton.rag.reranker.LlmbasedFallbackReranker
import org.krypton.rag.EmbeddingTextSanitizer
import org.krypton.util.AppLogger
import org.koin.dsl.module
import kotlinx.coroutines.runBlocking

/**
 * RAG (Retrieval-Augmented Generation) dependency injection module.
 * 
 * Provides RAG components including vector stores, embedders, LLM clients, and indexers.
 */
val ragModule = module {
    // Ollama model registry for checking model availability
    single<OllamaModelRegistry> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val httpEngine: HttpClientEngine = get()
        HttpOllamaModelRegistry(
            baseUrl = llmSettings.ollamaBaseUrl,
            httpClientEngine = httpEngine
        )
    }
    
    // EmbeddingTextSanitizer for ensuring texts don't exceed embedding limits
    single<EmbeddingTextSanitizer> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        EmbeddingTextSanitizer(
            maxTokens = ragSettings.embeddingMaxTokens,
            maxChars = ragSettings.embeddingMaxChars
        )
    }
    
    // Embedder for generating embeddings
    // Uses the same provider as the LLM (Gemini or Ollama)
    // Using factory instead of single to allow recreation when provider changes
    factory<Embedder> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val ragSettings = settings.rag
        val httpEngine: HttpClientEngine = get()
        
        when (llmSettings.provider) {
            LlmProvider.GEMINI -> {
                val apiKey = SecretsLoader.loadSecret("GEMINI_API_KEY")
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("GEMINI_API_KEY not found in local.properties. Please add it to use Gemini embedding API.")
                }
                // Use geminiEmbeddingModel if available, otherwise default to gemini-embedding-001
                val embeddingModel = llmSettings.geminiEmbeddingModel.ifBlank { "gemini-embedding-001" }
                AppLogger.i("RagModule", "Creating GeminiEmbedder for embeddings (provider=GEMINI, model=$embeddingModel)")
                GeminiEmbedder(
                    apiKey = apiKey,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                    model = embeddingModel,
                    outputDimension = 768,
                    httpClientEngine = httpEngine
                )
            }
            LlmProvider.OLLAMA -> {
                val sanitizer: EmbeddingTextSanitizer = get()
                // Use ollamaEmbeddingModel if available, otherwise fall back to deprecated embeddingModel
                val embeddingModel = llmSettings.ollamaEmbeddingModel.ifBlank { 
                    ragSettings.embeddingModel.ifBlank { org.krypton.config.RagDefaults.Embedding.DEFAULT_MODEL }
                }
                AppLogger.i("RagModule", "Creating HttpEmbedder for embeddings (provider=OLLAMA, model=$embeddingModel)")
                HttpEmbedder(
                    baseUrl = ragSettings.embeddingBaseUrl,
                    model = embeddingModel,
                    apiPath = "/api/embed",
                    sanitizer = sanitizer,
                    httpClientEngine = httpEngine
                )
            }
        }
    }
    
    // VectorStore for storing and searching embeddings
    // Using factory instead of single to allow recreation when settings change
    factory<VectorStore> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        val httpEngine: HttpClientEngine = get()
        
        when (ragSettings.vectorBackend) {
            VectorBackend.CHROMADB -> ChromaDBVectorStore(
                baseUrl = ragSettings.chromaBaseUrl,
                collectionName = ragSettings.chromaCollectionName,
                httpClientEngine = httpEngine,
                tenant = ragSettings.chromaTenant,
                database = ragSettings.chromaDatabase
            )
            VectorBackend.CHROMA_CLOUD -> {
                val apiKey = SecretsLoader.loadSecret("CHROMA_API_KEY")
                val host = SecretsLoader.loadSecret("CHROMA_HOST") ?: "api.trychroma.com"
                val tenant = SecretsLoader.loadSecret("CHROMA_TENANT") ?: "default"
                val database = SecretsLoader.loadSecret("CHROMA_DATABASE") ?: "defaultDB"
                
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("CHROMA_API_KEY not found in local.properties. Please add it to use ChromaDB Cloud.")
                }
                
                // Ensure baseUrl uses https
                val baseUrl = if (host.startsWith("http://") || host.startsWith("https://")) {
                    host
                } else {
                    "https://$host"
                }
                
                ChromaCloudVectorStore(
                    baseUrl = baseUrl,
                    collectionName = ragSettings.chromaCollectionName,
                    httpClientEngine = httpEngine,
                    apiKey = apiKey,
                    tenant = tenant,
                    database = database
                )
            }
        }
    }
    
    // LlamaClient for LLM operations (used by both RAG service and reranker)
    // Using factory instead of single to allow recreation when provider changes
    factory<LlamaClient> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val httpEngine: HttpClientEngine = get()
        
        when (llmSettings.provider) {
            LlmProvider.OLLAMA -> {
                HttpLlamaClient(
                    baseUrl = llmSettings.ollamaBaseUrl,
                    model = llmSettings.ollamaModel,
                    apiPath = "/api/generate",
                    httpClientEngine = httpEngine
                )
            }
            LlmProvider.GEMINI -> {
                val apiKey = SecretsLoader.loadSecret("GEMINI_API_KEY")
                val baseUrl = SecretsLoader.loadSecret("GEMINI_API_BASE_URL")
                    ?: "https://generativelanguage.googleapis.com/v1beta/models/${llmSettings.geminiModel}:generateContent"
                
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("GEMINI_API_KEY not found in local.properties. Please add it to use Gemini API.")
                }
                
                GeminiClient(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = llmSettings.geminiModel,
                    httpClientEngine = httpEngine
                )
            }
        }
    }
    
    // LlamaClient specifically for agent intent classification/routing
    // Desktop: Uses agentRoutingLlmProvider if set, otherwise uses the same provider as main chat
    // Using factory to allow recreation when settings change
    factory<LlamaClient>(qualifier = org.koin.core.qualifier.named("AgentRouting")) {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val httpEngine: HttpClientEngine = get()
        
        // Determine which provider to use for agent routing
        // If agentRoutingLlmProvider is not explicitly set, use the same provider as main chat
        val routingProvider = llmSettings.agentRoutingLlmProvider ?: llmSettings.provider
        
        when (routingProvider) {
            LlmProvider.OLLAMA -> {
                HttpLlamaClient(
                    baseUrl = llmSettings.ollamaBaseUrl,
                    model = llmSettings.ollamaModel,
                    apiPath = "/api/generate",
                    httpClientEngine = httpEngine
                )
            }
            LlmProvider.GEMINI -> {
                val apiKey = SecretsLoader.loadSecret("GEMINI_API_KEY")
                val baseUrl = SecretsLoader.loadSecret("GEMINI_API_BASE_URL")
                    ?: "https://generativelanguage.googleapis.com/v1beta/models/${llmSettings.geminiModel}:generateContent"
                
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException("GEMINI_API_KEY not found in local.properties. Please add it to use Gemini API.")
                }
                
                GeminiClient(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = llmSettings.geminiModel,
                    httpClientEngine = httpEngine
                )
            }
        }
    }
    
    // Reranker with conditional binding and fallback logic
    single<Reranker> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        val llamaClient: LlamaClient = get()
        val registry: OllamaModelRegistry = get()
        
        try {
            val rerankerModel = ragSettings.rerankerModel
            
            // Check if dedicated reranker model is available (using runBlocking since Koin doesn't support suspend)
            val hasDedicatedModel = if (rerankerModel != null) {
                runBlocking {
                    try {
                        registry.hasModel(rerankerModel)
                    } catch (e: Exception) {
                        AppLogger.w("RagModule", "Error checking for reranker model '$rerankerModel': ${e.message}", e)
                        false
                    }
                }
            } else {
                false
            }
            
            rerankerModel?.let { model ->
                AppLogger.i("RagModule", "Using dedicated reranker model: $model")
                DedicatedOllamaReranker(
                    llamaClient = llamaClient,
                    modelName = model
                )
            } ?: run {
                val settings = settingsRepository.settingsFlow.value
                val generatorModel = settings.llm.ollamaModel
                if (rerankerModel != null) {
                    AppLogger.i("RagModule", "Dedicated reranker model '$rerankerModel' not found, falling back to generator LLM: $generatorModel")
                } else {
                    AppLogger.i("RagModule", "No reranker model configured, using generator LLM as reranker: $generatorModel")
                }
                LlmbasedFallbackReranker(
                    llamaClient = llamaClient
                )
            }
        } catch (e: Exception) {
            AppLogger.w("RagModule", "Failed to initialize reranker, using NoopReranker: ${e.message}", e)
            NoopReranker()
        }
    }
    // RAG components factory (optional - may be null if initialization fails)
    // Using factory instead of single to allow recreation when provider changes
    factory<RagComponents?> {
        try {
            val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
            val ragSettings = settingsRepository.settingsFlow.value.rag
            val httpEngine: HttpClientEngine = get()
            
            val llmSettings = settingsRepository.settingsFlow.value.llm
            val config = RagConfig(
                vectorBackend = ragSettings.vectorBackend,
                llamaBaseUrl = llmSettings.ollamaBaseUrl,
                embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                chromaBaseUrl = ragSettings.chromaBaseUrl,
                chromaCollectionName = ragSettings.chromaCollectionName,
                chromaTenant = ragSettings.chromaTenant,
                chromaDatabase = ragSettings.chromaDatabase,
                llamaModel = llmSettings.ollamaModel,
                embeddingModel = ragSettings.embeddingModel,
                similarityThreshold = ragSettings.similarityThreshold,
                maxK = ragSettings.maxK,
                displayK = ragSettings.displayK,
                queryRewritingEnabled = ragSettings.queryRewritingEnabled,
                multiQueryEnabled = ragSettings.multiQueryEnabled,
                rerankerModel = ragSettings.rerankerModel
            )
            
            // Notes root is set dynamically when indexing (via indexer.index() call with vault path)
            // RAG components are created without a specific root here; the indexer uses the vault path when indexing
            val notesRoot = null
            
            // Get VectorStore, LlamaClient, Embedder and Reranker (may fall back to NoopReranker if initialization fails)
            val vectorStore: VectorStore = get()
            val llamaClient: LlamaClient = get()
            val embedder: Embedder = get() // Get fresh embedder based on current provider
            val reranker: Reranker = try {
                get<Reranker>()
            } catch (e: Exception) {
                AppLogger.w("RagModule", "Failed to get reranker, using NoopReranker: ${e.message}", e)
                NoopReranker()
            }
            
            createRagComponents(
                config = config,
                notesRoot = notesRoot,
                httpClientEngineFactory = org.krypton.rag.HttpClientEngineFactory(httpEngine),
                vectorStore = vectorStore,
                llamaClient = llamaClient,
                embedder = embedder, // Pass the embedder from DI (will be Gemini or Ollama based on provider)
                reranker = reranker,
                settingsRepository = settingsRepository,
                llamaClientFactory = { get<LlamaClient>() } // Factory to get new LlamaClient with current settings
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
    
    // VaultIndexService (created from RagComponents if available)
    // Using factory to get fresh RagComponents when provider changes
    factory<VaultIndexService?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            ragComponents?.indexer
        } catch (e: Exception) {
            null
        }
    }
    
    // RagService (created from RagComponents if available)
    // Using factory to get fresh RagComponents when provider changes
    factory<RagService?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            ragComponents?.ragService
        } catch (e: Exception) {
            null
        }
    }
    
    // ExtendedRagComponents (created from RagComponents if available)
    // Using factory instead of single to allow recreation when settings change (e.g., vectorBackend)
    factory<ExtendedRagComponents?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            
            ragComponents?.let { base ->
                // Use the RagComponents that was already created with the correct embedder from DI
                // No need to recreate it - just create the extended services
                val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
                val ragSettings = settingsRepository.settingsFlow.value.rag
                val httpEngine: HttpClientEngine = get()
                val vectorStore: VectorStore = get()
                
                val llmSettings = settingsRepository.settingsFlow.value.llm
                val config = RagConfig(
                    vectorBackend = ragSettings.vectorBackend,
                    llamaBaseUrl = llmSettings.ollamaBaseUrl,
                    embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                    chromaBaseUrl = ragSettings.chromaBaseUrl,
                    chromaCollectionName = ragSettings.chromaCollectionName,
                    chromaTenant = ragSettings.chromaTenant,
                    chromaDatabase = ragSettings.chromaDatabase,
                    llamaModel = llmSettings.ollamaModel,
                    embeddingModel = ragSettings.embeddingModel,
                    similarityThreshold = ragSettings.similarityThreshold,
                    maxK = ragSettings.maxK,
                    displayK = ragSettings.displayK,
                    queryRewritingEnabled = ragSettings.queryRewritingEnabled,
                    multiQueryEnabled = ragSettings.multiQueryEnabled,
                    rerankerModel = ragSettings.rerankerModel
                )
                
                // Create extended services using the base RagComponents (which already has the correct embedder)
                val healthService = org.krypton.rag.ChromaDBHealthService(
                    baseUrl = config.chromaBaseUrl,
                    collectionName = config.chromaCollectionName,
                    httpClientEngine = httpEngine,
                    tenant = config.chromaTenant,
                    database = config.chromaDatabase
                )
                
                val vaultMetadataService = org.krypton.rag.VaultMetadataService(
                    baseUrl = config.chromaBaseUrl,
                    metadataCollectionName = "vault_metadata",
                    httpClientEngine = httpEngine,
                    tenant = config.chromaTenant,
                    database = config.chromaDatabase
                )
                
                val vaultSyncService = org.krypton.rag.VaultSyncService(
                    vaultMetadataService = vaultMetadataService,
                    healthService = healthService,
                    vectorStore = base.vectorStore
                )
                
                val vaultWatcher = org.krypton.rag.JvmVaultWatcher()
                
                // Set up indexer callback to update metadata with hashes
                (base.indexer as? org.krypton.rag.Indexer)?.onIndexingComplete = { vaultPath, indexedFiles, indexedFileHashes ->
                    vaultMetadataService.updateVaultMetadata(vaultPath, indexedFileHashes)
                }
                
                org.krypton.rag.ExtendedRagComponents(
                    base = base, // Use the RagComponents with correct embedder from DI
                    healthService = healthService,
                    vaultMetadataService = vaultMetadataService,
                    vaultSyncService = vaultSyncService,
                    vaultWatcher = vaultWatcher
                )
            }
        } catch (e: Exception) {
            AppLogger.w("RagModule", "Failed to create ExtendedRagComponents: ${e.message}", e)
            null
        }
    }
}

