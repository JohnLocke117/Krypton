package org.krypton.krypton.di

import io.ktor.client.engine.*
import org.krypton.krypton.config.RagDefaults
import org.krypton.krypton.rag.*
import org.koin.dsl.module

/**
 * RAG (Retrieval-Augmented Generation) dependency injection module.
 * 
 * Provides RAG components including vector stores, embedders, LLM clients, and indexers.
 */
val ragModule = module {
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
                embeddingModel = RagDefaults.DEFAULT_EMBEDDING_MODEL
            )
            
            // Get notes root from settings (if available)
            val notesRoot = null // TODO: Get from settings or current directory
            
            createRagComponents(
                config = config,
                notesRoot = notesRoot,
                httpClientEngine = httpEngine
            )
        } catch (e: Exception) {
            // If RAG initialization fails, return null
            // Chat will fall back to direct Ollama
            null
        }
    }
}

