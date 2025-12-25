package org.krypton.krypton.chat

import org.krypton.krypton.prompt.PromptBuilder
import org.krypton.krypton.prompt.PromptContext
import org.krypton.krypton.rag.LlamaClient
import org.krypton.krypton.retrieval.RetrievalService
import org.krypton.krypton.util.IdGenerator
import org.krypton.krypton.util.TimeProvider
import org.krypton.krypton.util.createIdGenerator
import org.krypton.krypton.util.createTimeProvider
import org.krypton.krypton.util.AppLogger

/**
 * Chat service that uses retrieval-augmented generation with configurable modes.
 * 
 * Supports four retrieval modes:
 * - NONE: Plain chat without retrieval
 * - RAG: Local notes only
 * - WEB: Tavily web search only
 * - HYBRID: Both RAG and web search
 */
class RagChatService(
    private val baseChatService: ChatService,
    private val retrievalService: RetrievalService?,
    private val promptBuilder: PromptBuilder?,
    private val llamaClient: LlamaClient?,
    private val idGenerator: IdGenerator = createIdGenerator(),
    private val timeProvider: TimeProvider = createTimeProvider()
) : ChatService {
    
    override suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        retrievalMode: RetrievalMode
    ): List<ChatMessage> {
        // Create user message
        val userMsg = ChatMessage(
            id = idGenerator.generateId(),
            role = ChatRole.USER,
            content = userMessage,
            timestamp = timeProvider.currentTimeMillis()
        )
        
        // If no retrieval service or prompt builder, fall back to base chat
        if (retrievalService == null || promptBuilder == null || llamaClient == null) {
            AppLogger.i("RagChatService", "Retrieval components not available - using base chat service")
            return baseChatService.sendMessage(history, userMessage, retrievalMode)
        }
        
        // If mode is NONE, use base chat service
        if (retrievalMode == RetrievalMode.NONE) {
            AppLogger.d("RagChatService", "Mode: NONE - using base chat service")
            return baseChatService.sendMessage(history, userMessage, retrievalMode)
        }
        
        // Use retrieval service + prompt builder + LLM
        try {
            AppLogger.d("RagChatService", "Mode: $retrievalMode - using retrieval service")
            
            // Retrieve context
            val retrievalCtx = retrievalService.retrieve(userMessage, retrievalMode)
            
            // Build prompt
            val promptCtx = PromptContext(
                query = userMessage,
                retrievalMode = retrievalMode,
                localChunks = retrievalCtx.localChunks,
                webSnippets = retrievalCtx.webSnippets
            )
            val prompt = promptBuilder.build(promptCtx)
            
            // Generate answer using LLM
            val answer = llamaClient.complete(prompt)
            
            val assistantMsg = ChatMessage(
                id = idGenerator.generateId(),
                role = ChatRole.ASSISTANT,
                content = answer,
                timestamp = timeProvider.currentTimeMillis()
            )
            
            return history + userMsg + assistantMsg
        } catch (e: Exception) {
            // If retrieval/LLM fails, log error and fall back to base chat service
            AppLogger.w(
                "RagChatService",
                "Retrieval query failed, falling back to base chat service: ${e.message}",
                e
            )
            return baseChatService.sendMessage(history, userMessage, retrievalMode)
        }
    }
}

