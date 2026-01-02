package org.krypton.chat.agent

import org.krypton.chat.ChatMessage

/**
 * Interface for classifying user message intents.
 * 
 * Implementations use LLM or other methods to determine the intent type
 * of a user message, which is then used by MasterAgent to route to
 * the appropriate concrete agent.
 */
interface IntentClassifier {
    /**
     * Classifies the intent of a user message.
     * 
     * @param message The user's message
     * @param history Recent conversation history
     * @param context Context about the current state (vault path, settings)
     * @return The classified intent type
     */
    suspend fun classify(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): IntentType
}

