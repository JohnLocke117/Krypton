package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.Settings

/**
 * Interface for chat agents that can handle specific user intents.
 * 
 * Agents are checked before the normal RAG/chat flow. If an agent
 * can handle a message, it returns an [AgentResult]; otherwise it returns null.
 */
interface ChatAgent {
    /**
     * Attempts to handle a user message.
     * 
     * @param message The user's message
     * @param chatHistory The conversation history (may be empty for MVP)
     * @param context Context about the current state (vault path, settings)
     * @return An [AgentResult] if the agent handled the message, null otherwise
     */
    suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult?
}

/**
 * Context provided to agents for handling messages.
 * 
 * @param currentVaultPath The path of the currently opened vault/folder, or null if none is open
 * @param settings Current application settings
 */
data class AgentContext(
    val currentVaultPath: String?,
    val settings: Settings
)

/**
 * Result of an agent handling a message.
 * 
 * Different agent result types represent different actions taken by agents.
 */
sealed class AgentResult {
    /**
     * Indicates that a note was successfully created.
     * 
     * @param filePath The full path to the created file
     * @param title The title of the note (extracted topic or first heading)
     * @param preview A short preview of the note content (first paragraph or first N characters)
     */
    data class NoteCreated(
        val filePath: String,
        val title: String,
        val preview: String
    ) : AgentResult()
}

