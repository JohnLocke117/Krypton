package org.krypton.di

import org.krypton.chat.ChatService
import org.krypton.data.chat.impl.OllamaChatService
import org.krypton.data.repository.SettingsRepository
import org.krypton.prompt.PromptBuilder
import org.krypton.prompt.impl.DefaultPromptBuilder
import org.krypton.rag.*
import org.krypton.retrieval.RagRetriever
import org.krypton.retrieval.RetrievalService
import org.krypton.retrieval.impl.DefaultRetrievalService
import org.krypton.web.WebSearchClient
import org.koin.dsl.module

/**
 * Chat dependency injection module.
 * 
 * Provides chat service with optional RAG and web search support.
 */
val chatModule = module {
    // PromptBuilder (always available)
    single<PromptBuilder> {
        DefaultPromptBuilder()
    }
    
    // RagRetriever (optional - created from RagComponents if available)
    single<RagRetriever?> {
        try {
            val ragComponents: RagComponents? = try {
                get<RagComponents>()
            } catch (e: Exception) {
                null
            }
            ragComponents?.let { components ->
                val settingsRepository: SettingsRepository = get()
                val ragSettings = settingsRepository.settingsFlow.value.rag
                
                // Get reranker (may be NoopReranker)
                val reranker: Reranker = try {
                    get<Reranker>()
                } catch (e: Exception) {
                    NoopReranker()
                }
                
                // Create query preprocessor if needed
                val queryPreprocessor = if (ragSettings.queryRewritingEnabled || ragSettings.multiQueryEnabled) {
                    QueryPreprocessor(components.llamaClient)
                } else {
                    null
                }
                
                RagRetriever(
                    embedder = components.embedder,
                    vectorStore = components.vectorStore,
                    similarityThreshold = ragSettings.similarityThreshold,
                    maxK = ragSettings.maxK,
                    displayK = ragSettings.displayK,
                    queryPreprocessor = queryPreprocessor,
                    queryRewritingEnabled = ragSettings.queryRewritingEnabled,
                    multiQueryEnabled = ragSettings.multiQueryEnabled,
                    reranker = reranker
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // RetrievalService (uses RagRetriever and WebSearchClient if available)
    single<RetrievalService?> {
        val ragRetriever: RagRetriever? = try {
            get<RagRetriever>()
        } catch (e: Exception) {
            null
        }
        val webSearchClient: WebSearchClient? = try {
            get<WebSearchClient>()
        } catch (e: Exception) {
            null
        }
        
        if (ragRetriever != null || webSearchClient != null) {
            DefaultRetrievalService(
                ragRetriever = ragRetriever,
                webSearchClient = webSearchClient
            )
        } else {
            null
        }
    }
    
    // ChatService
    single<ChatService> {
        val llamaClient: LlamaClient = get()
        val promptBuilder: PromptBuilder = get()
        val retrievalService: RetrievalService? = try {
            get<RetrievalService>()
        } catch (e: Exception) {
            null
        }
        val settingsRepository: SettingsRepository = get()
        
        // OllamaChatService now handles retrieval internally
        OllamaChatService(
            llamaClient = llamaClient,
            promptBuilder = promptBuilder,
            retrievalService = retrievalService,
            settingsRepository = settingsRepository
        )
    }
}

