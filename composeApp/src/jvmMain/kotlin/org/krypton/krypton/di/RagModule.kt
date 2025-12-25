package org.krypton.krypton.di

import io.ktor.client.engine.*
import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.data.rag.impl.HttpOllamaModelRegistry
import org.krypton.krypton.data.rag.impl.OllamaModelRegistry
import org.krypton.krypton.rag.*
import org.krypton.krypton.data.rag.impl.HttpLlamaClient
import org.krypton.krypton.rag.reranker.DedicatedOllamaReranker
import org.krypton.krypton.rag.reranker.LlmbasedFallbackReranker
import org.krypton.krypton.util.AppLogger
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
        val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        val httpEngine: HttpClientEngine = get()
        HttpOllamaModelRegistry(
            baseUrl = ragSettings.llamaBaseUrl,
            httpClientEngine = httpEngine
        )
    }
    
    // LlamaClient for LLM operations (used by both RAG service and reranker)
    single<LlamaClient> {
        val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        val httpEngine: HttpClientEngine = get()
        HttpLlamaClient(
            baseUrl = ragSettings.llamaBaseUrl,
            model = RagDefaults.DEFAULT_LLAMA_MODEL,
            apiPath = "/api/generate",
            httpClientEngine = httpEngine
        )
    }
    
    // Reranker with conditional binding and fallback logic
    single<Reranker> {
        val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = get()
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
                val generatorModel = RagDefaults.DEFAULT_LLAMA_MODEL
                if (rerankerModel != null) {
                    AppLogger.i("RagModule", "Dedicated reranker model '$rerankerModel' not found, falling back to generator LLM: $generatorModel")
                } else {
                    AppLogger.i("RagModule", "No reranker model configured, using generator LLM as reranker: $generatorModel")
                }
                LlmbasedFallbackReranker(
                    llamaClient = llamaClient,
                    generatorModel = generatorModel
                )
            }
        } catch (e: Exception) {
            AppLogger.w("RagModule", "Failed to initialize reranker, using NoopReranker: ${e.message}", e)
            NoopReranker()
        }
    }
    // RAG components factory (optional - may be null if initialization fails)
    single<RagComponents?>(createdAtStart = false) {
        try {
            val settingsRepository: org.krypton.krypton.data.repository.SettingsRepository = get()
            val ragSettings = settingsRepository.settingsFlow.value.rag
            val httpEngine: HttpClientEngine = get()
            
            val config = RagConfig(
                vectorBackend = ragSettings.vectorBackend,
                llamaBaseUrl = ragSettings.llamaBaseUrl,
                embeddingBaseUrl = ragSettings.embeddingBaseUrl,
                chromaBaseUrl = ragSettings.chromaBaseUrl,
                chromaCollectionName = ragSettings.chromaCollectionName,
                chromaTenant = ragSettings.chromaTenant,
                chromaDatabase = ragSettings.chromaDatabase,
                llamaModel = RagDefaults.DEFAULT_LLAMA_MODEL,
                embeddingModel = RagDefaults.DEFAULT_EMBEDDING_MODEL,
                similarityThreshold = ragSettings.similarityThreshold,
                maxK = ragSettings.maxK,
                displayK = ragSettings.displayK,
                queryRewritingEnabled = ragSettings.queryRewritingEnabled,
                multiQueryEnabled = ragSettings.multiQueryEnabled,
                rerankerModel = ragSettings.rerankerModel
            )
            
            // Get notes root from settings (if available)
            val notesRoot = null // TODO: Get from settings or current directory
            
            // Get LlamaClient and Reranker (may fall back to NoopReranker if initialization fails)
            val llamaClient: LlamaClient = get()
            val reranker: Reranker = try {
                get<Reranker>()
            } catch (e: Exception) {
                AppLogger.w("RagModule", "Failed to get reranker, using NoopReranker: ${e.message}", e)
                NoopReranker()
            }
            
            createRagComponents(
                config = config,
                notesRoot = notesRoot,
                httpClientEngine = httpEngine,
                llamaClient = llamaClient,
                reranker = reranker
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
}

