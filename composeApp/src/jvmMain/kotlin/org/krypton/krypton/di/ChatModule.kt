package org.krypton.krypton.di

import org.krypton.krypton.chat.ChatService
import org.krypton.krypton.data.chat.impl.OllamaChatService
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.prompt.PromptBuilder
import org.krypton.krypton.prompt.impl.DefaultPromptBuilder
import org.krypton.krypton.rag.*
import org.krypton.krypton.retrieval.RagRetriever
import org.krypton.krypton.retrieval.RetrievalService
import org.krypton.krypton.retrieval.impl.DefaultRetrievalService
import org.krypton.krypton.web.WebSearchClient
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
        val baseChatService = OllamaChatService()
        val retrievalService: RetrievalService? = try {
            get<RetrievalService>()
        } catch (e: Exception) {
            null
        }
        val promptBuilder: PromptBuilder = get()
        val llamaClient: LlamaClient? = try {
            get<LlamaClient>()
        } catch (e: Exception) {
            null
        }
        
        if (retrievalService != null && llamaClient != null) {
            org.krypton.krypton.chat.RagChatService(
                baseChatService = baseChatService,
                retrievalService = retrievalService,
                promptBuilder = promptBuilder,
                llamaClient = llamaClient
            )
        } else {
            baseChatService
        }
    }
}

