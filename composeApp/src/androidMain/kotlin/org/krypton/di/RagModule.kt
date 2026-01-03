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
import org.krypton.util.AppLogger
import org.koin.dsl.module
import kotlinx.coroutines.runBlocking

/**
 * RAG (Retrieval-Augmented Generation) dependency injection module for Android.
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
    
    // Embedder for generating embeddings (Android uses Gemini embedding API)
    single<Embedder> {
        val apiKey = SecretsLoader.loadSecret("GEMINI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("GEMINI_API_KEY not found in local.properties. Please add it to use Gemini embedding API.")
        }
        val httpEngine: HttpClientEngine = get()
        GeminiEmbedder(
            apiKey = apiKey,
            httpClientEngine = httpEngine
        )
    }
    
    // VectorStore for storing and searching embeddings
    // Using factory instead of single to allow recreation when settings change
    // Android only supports CHROMA_CLOUD - local CHROMADB is not supported on Android
    factory<VectorStore> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        val httpEngine: HttpClientEngine = get()
        
        when (ragSettings.vectorBackend) {
            VectorBackend.CHROMADB -> {
                // Local ChromaDB is not supported on Android
                throw IllegalStateException("Local ChromaDB (CHROMADB) is not supported on Android. Please use CHROMA_CLOUD.")
            }
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
    // Android only supports GEMINI - OLLAMA is not supported on Android
    // Using factory instead of single to allow recreation when provider changes
    factory<LlamaClient> {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val httpEngine: HttpClientEngine = get()
        
        when (llmSettings.provider) {
            LlmProvider.OLLAMA -> {
                // OLLAMA is not supported on Android
                throw IllegalStateException("OLLAMA is not supported on Android. Please use GEMINI provider.")
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
    // Android: Always uses Gemini (only option available)
    // Using factory to allow recreation when settings change
    factory<LlamaClient>(qualifier = org.koin.core.qualifier.named("AgentRouting")) {
        val settingsRepository: org.krypton.data.repository.SettingsRepository = get()
        val settings = settingsRepository.settingsFlow.value
        val llmSettings = settings.llm
        val httpEngine: HttpClientEngine = get()
        
        // Android always uses Gemini for agent routing
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
            
            if (hasDedicatedModel) {
                AppLogger.i("RagModule", "Using dedicated reranker model: $rerankerModel")
                DedicatedOllamaReranker(
                    llamaClient = llamaClient,
                    modelName = rerankerModel!!
                )
            } else {
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
    
    // MarkdownChunker - use the implementation from commonMain
    single<MarkdownChunker> {
        org.krypton.rag.MarkdownChunkerImpl()
    }
    
    // Indexer - Android is query-only, so indexing is not supported
    single<VaultIndexService> {
        // Return a no-op implementation that throws on indexing calls
        object : VaultIndexService {
            override suspend fun indexVault(
                rootPath: String,
                existingFileHashes: Map<String, String>?
            ) {
                throw UnsupportedOperationException("Indexing not supported on Android. Collections must be indexed on Desktop.")
            }
            
            override suspend fun indexFile(path: String) {
                throw UnsupportedOperationException("Indexing not supported on Android. Collections must be indexed on Desktop.")
            }
            
            override suspend fun removeFile(path: String) {
                // No-op: Android doesn't manage collections
                AppLogger.w("VaultIndexService", "removeFile not supported on Android (query-only mode). Attempted to remove: $path")
            }
        }
    }
    
    // RagService
    single<RagService> {
        val embedder: Embedder = get()
        val vectorStore: VectorStore = get()
        val llamaClient: LlamaClient = get()
        val reranker: Reranker = get()
        RagServiceImpl(
            embedder = embedder,
            vectorStore = vectorStore,
            llamaClient = llamaClient,
            reranker = reranker
        )
    }
    
    // RAG components factory (optional - may be null if initialization fails)
    single<RagComponents?>(createdAtStart = false) {
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
            
            // Get notes root from settings (if available)
            val notesRoot = null // TODO: Get from settings or current directory
            
            // Get VectorStore, LlamaClient, Embedder, and Reranker (may fall back to NoopReranker if initialization fails)
            val vectorStore: VectorStore = get()
            val llamaClient: LlamaClient = get()
            val embedder: Embedder = get() // Get GeminiEmbedder from DI
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
                reranker = reranker,
                embedder = embedder
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
    
    // VaultIndexService (created from RagComponents if available)
    single<VaultIndexService?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            ragComponents?.indexer ?: get<VaultIndexService>()
        } catch (e: Exception) {
            null
        }
    }
    
    // RagService (created from RagComponents if available)
    single<RagService?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            ragComponents?.ragService ?: get<RagService>()
        } catch (e: Exception) {
            null
        }
    }
}
