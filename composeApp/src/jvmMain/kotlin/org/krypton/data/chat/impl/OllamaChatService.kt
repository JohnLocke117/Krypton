package org.krypton.data.chat.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.chat.ChatService
import org.krypton.chat.ChatMessage
import org.krypton.chat.ChatResponse
import org.krypton.chat.ChatResponseMetadata
import org.krypton.chat.ChatRole
import org.krypton.chat.ChatSource
import org.krypton.chat.ChatException
import org.krypton.chat.RetrievalMode
import org.krypton.chat.SourceType
import org.krypton.prompt.PromptBuilder
import org.krypton.prompt.PromptContext
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
 * Handles message sending, retrieval (if enabled), and LLM generation.
 * History management is handled by the caller (ChatStateHolder).
 */
class OllamaChatService(
    private val llamaClient: LlamaClient,
    private val promptBuilder: PromptBuilder,
    private val retrievalService: RetrievalService?,
    private val settingsRepository: SettingsRepository,
    private val createNoteAgent: ChatAgent? = null,
    private val idGenerator: IdGenerator = createIdGenerator(),
    private val timeProvider: TimeProvider = createTimeProvider()
) : ChatService {

    override suspend fun sendMessage(
        message: String,
        mode: RetrievalMode,
        threadId: String?,
        vaultPath: String?
    ): ChatResponse = withContext(Dispatchers.IO) {
        AppLogger.action("Chat", "MessageSent", "mode=$mode, length=${message.length}")
        
        try {
            // Check agents before normal flow
            val agentResult = createNoteAgent?.let { agent ->
                try {
                    val context = AgentContext(
                        currentVaultPath = vaultPath,
                        settings = settingsRepository.settingsFlow.value
                    )
                    // For MVP, pass empty history (can enhance later)
                    agent.tryHandle(message, emptyList(), context)
                } catch (e: Exception) {
                    AppLogger.e("Chat", "Agent error (continuing with normal flow): ${e.message}", e)
                    null
                }
            }
            
            // If agent handled the message, convert result to ChatResponse
            if (agentResult != null) {
                return@withContext when (agentResult) {
                    is AgentResult.NoteCreated -> {
                        val responseText = buildString {
                            appendLine("Created a new note:")
                            appendLine()
                            appendLine("- **Title:** ${agentResult.title}")
                            appendLine("- **File:** `${agentResult.filePath}`")
                            appendLine()
                            appendLine("**Preview:**")
                            appendLine("```markdown")
                            appendLine(agentResult.preview)
                            appendLine("```")
                        }
                        
                        val assistantMessage = ChatMessage(
                            id = idGenerator.generateId(),
                            role = ChatRole.ASSISTANT,
                            content = responseText,
                            timestamp = timeProvider.currentTimeMillis()
                        )
                        
                        ChatResponse(
                            message = assistantMessage,
                            retrievalMode = mode,
                            metadata = ChatResponseMetadata(
                                sources = emptyList(),
                                additionalInfo = mapOf("agent" to "CreateNoteAgent", "action" to "note_created")
                            )
                        )
                    }
                }
            }
            
            // Retrieve context if mode requires it
            val retrievalCtx = if (mode != RetrievalMode.NONE && retrievalService != null) {
                retrievalService.retrieve(message, mode)
            } else {
                null
            }
            
            // Build prompt context
            val promptCtx = PromptContext(
                query = message,
                retrievalMode = mode,
                localChunks = retrievalCtx?.localChunks ?: emptyList(),
                webSnippets = retrievalCtx?.webSnippets ?: emptyList()
            )
            
            // Build prompt using PromptBuilder
            val prompt = promptBuilder.buildPrompt(promptCtx)
            
            // Generate response using LlamaClient
            val responseText = llamaClient.complete(prompt).trim()
            
            if (responseText.isEmpty()) {
                throw ChatException("LLM returned an empty response")
            }
            
            // Create assistant message
            val assistantMessage = ChatMessage(
                id = idGenerator.generateId(),
                role = ChatRole.ASSISTANT,
                content = responseText,
                timestamp = timeProvider.currentTimeMillis()
            )
            
            // Build metadata with sources
            val sources = buildList {
                retrievalCtx?.localChunks?.forEach { chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val identifier = sectionTitle ?: filePath.substringAfterLast('/').substringBeforeLast('.')
                    add(ChatSource(
                        type = SourceType.RAG,
                        identifier = identifier,
                        location = filePath
                    ))
                }
                retrievalCtx?.webSnippets?.forEach { snippet ->
                    add(ChatSource(
                        type = SourceType.WEB,
                        identifier = snippet.title,
                        location = snippet.url
                    ))
                }
            }
            
            val metadata = ChatResponseMetadata(
                sources = sources,
                additionalInfo = emptyMap()
            )
            
            AppLogger.i("Chat", "ResponseReceived: length=${responseText.length}, mode=$mode, sources=${sources.size}")
            
            ChatResponse(
                message = assistantMessage,
                retrievalMode = mode,
                metadata = metadata
            )
        } catch (e: ChatException) {
            AppLogger.e("Chat", "ChatError: ${e.message}", e)
            // Check if error is related to model name
            val errorMessage = e.message?.lowercase() ?: ""
            if (errorMessage.contains("model") || errorMessage.contains("not found") || 
                errorMessage.contains("invalid") || errorMessage.contains("404")) {
                val ragSettings = settingsRepository.settingsFlow.value.rag
                throw ChatException("Error occurred, please check model name. Generator model: ${ragSettings.llamaModel}, Embedding model: ${ragSettings.embeddingModel}", e)
            }
            throw e
        } catch (e: RagException) {
            AppLogger.e("Chat", "RAG error during chat: ${e.message}", e)
            val errorMessage = e.message?.lowercase() ?: ""
            if (errorMessage.contains("model") || errorMessage.contains("not found") || 
                errorMessage.contains("invalid") || errorMessage.contains("404")) {
                val ragSettings = settingsRepository.settingsFlow.value.rag
                throw ChatException("Error occurred, please check model name. Generator model: ${ragSettings.llamaModel}, Embedding model: ${ragSettings.embeddingModel}", e)
            }
            throw ChatException("RAG failed: ${e.message}", e)
        } catch (e: IOException) {
            AppLogger.e("Chat", "Network error during chat: ${e.message}", e)
            throw ChatException("Network error during chat", e)
        } catch (e: Exception) {
            AppLogger.e("Chat", "Unexpected chat error: ${e.message}", e)
            val errorMessage = e.message?.lowercase() ?: ""
            if (errorMessage.contains("model") || errorMessage.contains("not found") || 
                errorMessage.contains("invalid") || errorMessage.contains("404")) {
                val ragSettings = settingsRepository.settingsFlow.value.rag
                throw ChatException("Error occurred, please check model name. Generator model: ${ragSettings.llamaModel}, Embedding model: ${ragSettings.embeddingModel}", e)
            }
            throw ChatException("Unexpected chat error: ${e.message}", e)
        }
    }
}
