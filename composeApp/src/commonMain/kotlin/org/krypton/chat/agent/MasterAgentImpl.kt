package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.util.AppLogger

/**
 * Implementation of MasterAgent that routes messages to concrete agents based on intent classification.
 * 
 * Makes a single LLM call via IntentClassifier to determine intent, then delegates to
 * exactly one concrete agent or returns null to fall back to normal chat.
 */
class MasterAgentImpl(
    private val classifier: IntentClassifier,
    private val createNoteAgent: CreateNoteAgent,
    private val searchNoteAgent: SearchNoteAgent,
    private val summarizeNoteAgent: SummarizeNoteAgent
) : MasterAgent {

    override suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult? {
        try {
            // Single LLM call for intent classification
            val intent = classifier.classify(message, chatHistory, context)
            
            AppLogger.i("MasterAgent", "Classified intent: $intent for message: \"$message\"")
            
            // Route to appropriate concrete agent based on intent
            val result: AgentResult? = when (intent) {
                IntentType.CREATE_NOTE -> {
                    try {
                        createNoteAgent.execute(message, chatHistory, context)
                    } catch (e: Exception) {
                        AppLogger.e("MasterAgent", "CreateNoteAgent execution failed", e)
                        null // Fall back to normal chat
                    }
                }
                
                IntentType.SEARCH_NOTES -> {
                    try {
                        searchNoteAgent.execute(message, chatHistory, context)
                    } catch (e: Exception) {
                        AppLogger.e("MasterAgent", "SearchNoteAgent execution failed", e)
                        null // Fall back to normal chat
                    }
                }
                
                IntentType.SUMMARIZE_NOTE -> {
                    try {
                        summarizeNoteAgent.execute(message, chatHistory, context)
                    } catch (e: Exception) {
                        AppLogger.e("MasterAgent", "SummarizeNoteAgent execution failed", e)
                        null // Fall back to normal chat
                    }
                }
                
                IntentType.NORMAL_CHAT,
                IntentType.UNKNOWN -> {
                    // Fall back to normal RAG/chat flow
                    null
                }
            }
            return result
        } catch (e: Exception) {
            AppLogger.e("MasterAgent", "Intent classification failed, falling back to normal chat", e)
            return null // Fall back to normal chat on any error
        }
    }
}

