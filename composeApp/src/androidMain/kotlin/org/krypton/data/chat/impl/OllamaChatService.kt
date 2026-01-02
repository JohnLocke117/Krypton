package org.krypton.data.chat.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.chat.ChatService
import org.krypton.chat.ChatMessage as OldChatMessage
import org.krypton.chat.ChatResult
import org.krypton.chat.ChatRole
import org.krypton.chat.ChatException
import org.krypton.chat.RetrievalMode
import org.krypton.chat.conversation.*
import org.krypton.chat.conversation.ChatMessage as ConversationChatMessage
import org.krypton.prompt.PromptBuilder
import org.krypton.rag.LlamaClient
import org.krypton.retrieval.RetrievalService
import org.krypton.data.repository.SettingsRepository
import org.krypton.util.IdGenerator
import org.krypton.util.TimeProvider
import org.krypton.util.createIdGenerator
import org.krypton.util.createTimeProvider
import org.krypton.util.AppLogger
import org.krypton.rag.RagException
import org.krypton.chat.agent.ChatAgent
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import java.io.IOException

/**
 * Chat service implementation using Ollama LLM with optional RAG support.
 * 
 * NOTE: This is a legacy implementation. Android uses GeminiChatService instead.
 * This class is kept for compatibility but should not be used.
 * 
 * Handles message sending, retrieval (if enabled), and LLM generation.
 * Manages conversation persistence and bounded memory.
 */
class OllamaChatService(
    private val llamaClient: LlamaClient,
    private val promptBuilder: PromptBuilder,
    private val retrievalService: RetrievalService?,
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val memoryProvider: ConversationMemoryProvider,
    private val agents: List<ChatAgent>? = null,
    private val idGenerator: IdGenerator = createIdGenerator(),
    private val timeProvider: TimeProvider = createTimeProvider()
) : ChatService {

    override suspend fun sendMessage(
        vaultId: String,
        conversationId: ConversationId?,
        userMessage: String,
        retrievalMode: RetrievalMode,
        currentNotePath: String?
    ): ChatResult = withContext(Dispatchers.IO) {
        AppLogger.action("Chat", "MessageSent", "mode=$retrievalMode, length=${userMessage.length}")
        
        try {
            // Create or get conversation
            val convId = conversationId ?: conversationRepository.createConversation(
                vaultId = vaultId,
                initialUserMessage = userMessage,
                retrievalMode = retrievalMode.name
            )
            
            // Load bounded conversation history
            val conversationHistory = memoryProvider.buildContextMessages(convId)
            
            // Convert to old ChatMessage format for agents and prompt builder
            val oldFormatHistory: List<org.krypton.chat.ChatMessage> = conversationHistory.map { msg ->
                org.krypton.chat.ChatMessage(
                    id = msg.id.value,
                    role = when (msg.author) {
                        MessageAuthor.USER -> ChatRole.USER
                        MessageAuthor.ASSISTANT -> ChatRole.ASSISTANT
                        MessageAuthor.SYSTEM -> ChatRole.SYSTEM
                    },
                    content = msg.text,
                    timestamp = msg.createdAt
                )
            }
            
            // Check agents before normal flow
            var agentError: String? = null
            val agentResult = agents?.firstNotNullOfOrNull { agent ->
                try {
                    AppLogger.i("Chat", "═══════════════════════════════════════════════════════════")
                    AppLogger.i("Chat", "Agent called: ${agent::class.simpleName}")
                    AppLogger.i("Chat", "  Message: $userMessage")
                    AppLogger.i("Chat", "  Vault: $vaultId")
                    AppLogger.i("Chat", "  Note: ${currentNotePath ?: "none"}")
                    AppLogger.i("Chat", "═══════════════════════════════════════════════════════════")
                    
                    val context = AgentContext(
                        currentVaultPath = vaultId,
                        settings = settingsRepository.settingsFlow.value,
                        currentNotePath = currentNotePath
                    )
                    // Pass conversation history to agents
                    val result = agent.tryHandle(userMessage, oldFormatHistory, context)
                    if (result != null) {
                        AppLogger.i("Chat", "Agent ${agent::class.simpleName} handled the message successfully")
                    }
                    result
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Agent execution failed"
                    AppLogger.e("Chat", "Agent ${agent::class.simpleName} error (continuing with normal flow): $errorMsg", e)
                    agentError = "${agent::class.simpleName}: $errorMsg"
                    null
                }
            }
            
            // If agent handled the message, convert result to ChatResult
            if (agentResult != null) {
                val now = System.currentTimeMillis()
                val responseText = when (agentResult) {
                    is AgentResult.NoteCreated -> {
                        buildString {
                            appendLine("Created a new note:")
                            appendLine()
                            appendLine("- **Title:** ${agentResult.title}")
                            appendLine("- **File:** `${agentResult.filePath}`")
                            if (agentResult.preview.isNotBlank()) {
                                appendLine()
                                appendLine("**Preview:**")
                                appendLine()
                                appendLine(agentResult.preview)
                            }
                        }
                    }
                    is AgentResult.NotesFound -> {
                        buildString {
                            agentResult.results.forEach { match ->
                                appendLine("- [${match.title}](${match.filePath})")
                            }
                        }
                    }
                    is AgentResult.NoteSummarized -> {
                        buildString {
                            appendLine("**Summary: ${agentResult.title}**")
                            appendLine()
                            appendLine(agentResult.summary)
                            if (agentResult.sourceFiles.isNotEmpty()) {
                                appendLine()
                                appendLine("**Sources:**")
                                agentResult.sourceFiles.forEach { filePath ->
                                    appendLine("- `$filePath`")
                                }
                            }
                        }
                    }
                }
                
                // Save user message
                val userMsg = ConversationChatMessage(
                    id = MessageId(idGenerator.generateId()),
                    conversationId = convId,
                    author = MessageAuthor.USER,
                    text = userMessage,
                    createdAt = now
                )
                conversationRepository.appendMessage(userMsg)
                
                // Save assistant message
                val assistantMsg = ConversationChatMessage(
                    id = MessageId(idGenerator.generateId()),
                    conversationId = convId,
                    author = MessageAuthor.ASSISTANT,
                    text = responseText,
                    createdAt = now
                )
                conversationRepository.appendMessage(assistantMsg)
                
                // Update conversation summary
                conversationRepository.updateConversationSummary(
                    conversationId = convId,
                    lastMessagePreview = responseText.take(100),
                    updatedAt = now
                )
                
                return@withContext ChatResult(
                    conversationId = convId,
                    assistantMessage = assistantMsg,
                    agentError = agentError
                )
            }
            
            // Retrieve context if mode requires it
            val retrievalCtx = if (retrievalMode != RetrievalMode.NONE && retrievalService != null) {
                retrievalService.retrieve(userMessage, retrievalMode)
            } else {
                null
            }
            
            // Build prompt with conversation history
            val prompt = promptBuilder.buildPrompt(
                systemInstructions = "",
                conversationHistory = oldFormatHistory,
                localChunks = retrievalCtx?.localChunks ?: emptyList(),
                webSnippets = retrievalCtx?.webSnippets ?: emptyList(),
                userMessage = userMessage
            )
            
            // Generate response using LlamaClient
            val responseText = llamaClient.complete(prompt).trim()
            
            if (responseText.isEmpty()) {
                throw ChatException("LLM returned an empty response")
            }
            
            val now = System.currentTimeMillis()
            
            // Save user message
            val userMsg = ConversationChatMessage(
                id = MessageId(idGenerator.generateId()),
                conversationId = convId,
                author = MessageAuthor.USER,
                text = userMessage,
                createdAt = now
            )
            conversationRepository.appendMessage(userMsg)
            
            // Save assistant message
            val assistantMsg = ConversationChatMessage(
                id = MessageId(idGenerator.generateId()),
                conversationId = convId,
                author = MessageAuthor.ASSISTANT,
                text = responseText,
                createdAt = now
            )
            conversationRepository.appendMessage(assistantMsg)
            
            // Update conversation summary
            conversationRepository.updateConversationSummary(
                conversationId = convId,
                lastMessagePreview = responseText.take(100),
                updatedAt = now
            )
            
            ChatResult(
                conversationId = convId,
                assistantMessage = assistantMsg,
                agentError = agentError
            )
        } catch (e: ChatException) {
            AppLogger.e("Chat", "ChatError: ${e.message}", e)
            throw e
        } catch (e: RagException) {
            AppLogger.e("Chat", "RAG error during chat: ${e.message}", e)
            throw ChatException("RAG failed: ${e.message}", e)
        } catch (e: IOException) {
            AppLogger.e("Chat", "Network error during chat: ${e.message}", e)
            throw ChatException("Network error during chat", e)
        } catch (e: Exception) {
            AppLogger.e("Chat", "Unexpected chat error: ${e.message}", e)
            throw ChatException("Unexpected chat error: ${e.message}", e)
        }
    }
}
