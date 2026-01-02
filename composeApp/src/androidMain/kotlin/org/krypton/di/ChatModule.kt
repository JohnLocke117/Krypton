package org.krypton.di

import org.krypton.chat.ChatService
import org.krypton.chat.agent.CreateNoteAgent
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.chat.agent.SummarizeNoteAgent
import org.krypton.chat.conversation.ConversationMemoryPolicy
import org.krypton.chat.conversation.ConversationMemoryProvider
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.data.chat.ConversationMemoryProviderImpl
import org.krypton.data.chat.impl.AndroidConversationPersistence
import org.krypton.data.chat.impl.GeminiChatService
import org.krypton.data.flashcard.impl.FlashcardServiceImpl
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.prompt.PromptBuilder
import org.krypton.prompt.impl.DefaultPromptBuilder
import org.krypton.rag.*
import org.krypton.retrieval.RagRetriever
import org.krypton.retrieval.RetrievalService
import org.krypton.retrieval.impl.DefaultRetrievalService
import org.krypton.web.WebSearchClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Chat dependency injection module for Android.
 * 
 * Provides chat service with optional RAG and web search support.
 */
val chatModule = module {
    // ConversationRepository (Android implementation)
    single<ConversationRepository> {
        AndroidConversationPersistence(
            context = androidContext()
        )
    }
    
    // ConversationMemoryPolicy (Android: conservative limits for Gemini 2.5 Flash)
    single<ConversationMemoryPolicy> {
        ConversationMemoryPolicy(
            maxMessages = 15,
            maxChars = 6_000,
        )
    }
    
    // ConversationMemoryProvider
    single<ConversationMemoryProvider> {
        ConversationMemoryProviderImpl(
            repository = get(),
            policy = get()
        )
    }
    
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
                    reranker = reranker,
                    settingsRepository = settingsRepository
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
    
    // CreateNoteAgent (for note creation functionality)
    // All dependencies (LlamaClient, FileSystem, SettingsRepository) are always available
    // Using factory to get fresh LlamaClient when provider changes
    factory<CreateNoteAgent> {
        CreateNoteAgent(
            llamaClient = get(),
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // SearchNoteAgent (for note search functionality)
    single<SearchNoteAgent> {
        val ragRetriever: RagRetriever? = try {
            get<RagRetriever>()
        } catch (e: Exception) {
            null
        }
        SearchNoteAgent(
            ragRetriever = ragRetriever,
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // SummarizeNoteAgent (for note summarization functionality)
    // Using factory to get fresh LlamaClient when provider changes
    factory<SummarizeNoteAgent> {
        val ragRetriever: RagRetriever? = try {
            get<RagRetriever>()
        } catch (e: Exception) {
            null
        }
        SummarizeNoteAgent(
            llamaClient = get(),
            ragRetriever = ragRetriever,
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // FlashcardService (for flashcard generation functionality)
    // Using factory to get fresh LlamaClient when provider changes
    factory<FlashcardService> {
        FlashcardServiceImpl(
            fileSystem = get(),
            llamaClient = get()
        )
    }
    
    // ChatService
    // Using factory to get fresh LlamaClient when provider changes
    factory<ChatService> {
        val llamaClient: LlamaClient = get()
        val promptBuilder: PromptBuilder = get()
        val retrievalService: RetrievalService? = try {
            get<RetrievalService>()
        } catch (e: Exception) {
            null
        }
        val settingsRepository: SettingsRepository = get()
        
        // Collect all available agents
        val agents = buildList<org.krypton.chat.agent.ChatAgent> {
            try {
                add(get<CreateNoteAgent>())
            } catch (e: Exception) {
                // CreateNoteAgent should always be available, but handle gracefully
            }
            try {
                add(get<SearchNoteAgent>())
            } catch (e: Exception) {
                // SearchNoteAgent may not be available if dependencies are missing
            }
            try {
                add(get<SummarizeNoteAgent>())
            } catch (e: Exception) {
                // SummarizeNoteAgent may not be available if dependencies are missing
            }
        }
        
        // GeminiChatService handles retrieval internally and conversation management
        GeminiChatService(
            llamaClient = llamaClient, // Actually GeminiClient, but implements LlamaClient
            promptBuilder = promptBuilder,
            retrievalService = retrievalService,
            settingsRepository = settingsRepository,
            conversationRepository = get(),
            memoryProvider = get(),
            agents = if (agents.isNotEmpty()) agents else null
        )
    }
}

