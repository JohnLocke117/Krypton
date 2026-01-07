package org.krypton.di

import org.krypton.chat.ChatService
import org.krypton.chat.agent.CreateNoteAgent
import org.krypton.chat.agent.CreateNoteAgentImpl
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.chat.agent.SearchNoteAgentImpl
import org.krypton.chat.agent.SummarizeNoteAgent
import org.krypton.chat.agent.SummarizeNoteAgentImpl
import org.krypton.chat.agent.FlashcardAgent
import org.krypton.chat.agent.FlashcardAgentImpl
import org.krypton.chat.agent.StudyAgent
import org.krypton.chat.agent.StudyAgentImpl
import org.krypton.chat.agent.IntentClassifier
import org.krypton.chat.agent.LlmIntentClassifier
import org.krypton.chat.agent.MasterAgent
import org.krypton.chat.agent.MasterAgentImpl
import org.koin.core.qualifier.named
import org.krypton.chat.conversation.ConversationMemoryPolicy
import org.krypton.chat.conversation.ConversationMemoryProvider
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.data.chat.ConversationMemoryProviderImpl
import org.krypton.data.chat.impl.JvmConversationPersistence
import org.krypton.data.chat.impl.OllamaChatService
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
import org.koin.dsl.module

/**
 * Chat dependency injection module.
 * 
 * Provides chat service with optional RAG and web search support.
 */
val chatModule = module {
    // ConversationRepository (JVM implementation)
    single<ConversationRepository> {
        JvmConversationPersistence()
    }
    
    // ConversationMemoryPolicy (Desktop: generous limits for Llama 128k)
    single<ConversationMemoryPolicy> {
        ConversationMemoryPolicy(
            maxMessages = 50,
            maxChars = 16_000,
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
    
    // IntentClassifier for agent routing (uses separate LlamaClient for agent routing)
    factory<IntentClassifier> {
        val agentRoutingLlmClient: LlamaClient = get(qualifier = named("AgentRouting"))
        LlmIntentClassifier(llmClient = agentRoutingLlmClient)
    }
    
    // CreateNoteAgent (execution-only, no intent matching)
    // All dependencies (LlamaClient, FileSystem, SettingsRepository) are always available
    // Using factory to get fresh LlamaClient when provider changes
    factory<CreateNoteAgent> {
        CreateNoteAgentImpl(
            llamaClient = get(),
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // SearchNoteAgent (execution-only, no intent matching)
    single<SearchNoteAgent> {
        val ragRetriever: RagRetriever? = try {
            get<RagRetriever>()
        } catch (e: Exception) {
            null
        }
        SearchNoteAgentImpl(
            ragRetriever = ragRetriever,
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // SummarizeNoteAgent (execution-only, no intent matching)
    // Using factory to get fresh LlamaClient when provider changes
    factory<SummarizeNoteAgent> {
        val ragRetriever: RagRetriever? = try {
            get<RagRetriever>()
        } catch (e: Exception) {
            null
        }
        SummarizeNoteAgentImpl(
            llamaClient = get(),
            ragRetriever = ragRetriever,
            fileSystem = get(),
            settingsRepository = get()
        )
    }
    
    // FlashcardAgent (execution-only, no intent matching)
    factory<FlashcardAgent> {
        FlashcardAgentImpl(
            flashcardService = get(),
            fileSystem = get()
        )
    }
    
    // StudyAgent (execution-only, no intent matching)
    single<StudyAgent> {
        StudyAgentImpl(
            studyPlanner = get(),
            studyRunner = get(),
            studyGoalRepository = get(),
            sessionRepository = get(),
            cacheRepository = get(),
            searchNoteAgent = get(),
            timeProvider = get(),
            persistence = get()
        )
    }
    
    // MasterAgent (the only ChatAgent, routes to concrete agents based on intent)
    single<MasterAgent> {
        MasterAgentImpl(
            classifier = get(),
            createNoteAgent = get(),
            searchNoteAgent = get(),
            summarizeNoteAgent = get(),
            flashcardAgent = get(),
            studyAgent = get()
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
        
        // Only MasterAgent is exposed as ChatAgent (it handles routing internally)
        val agents = try {
            listOf(get<MasterAgent>())
        } catch (e: Exception) {
            // MasterAgent should always be available, but handle gracefully
            null
        }
        
        // OllamaChatService now handles retrieval internally and conversation management
        OllamaChatService(
            llamaClient = llamaClient,
            promptBuilder = promptBuilder,
            retrievalService = retrievalService,
            settingsRepository = settingsRepository,
            conversationRepository = get(),
            memoryProvider = get(),
            agents = agents,
            llamaClientFactory = { get<LlamaClient>() } // Factory to get new LlamaClient with current settings
        )
    }
}

