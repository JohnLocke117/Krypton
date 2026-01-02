package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger

/**
 * LLM-based intent classifier that uses a language model to determine user intent.
 * 
 * Makes a single LLM call to classify the intent, then parses the response
 * to determine which agent (if any) should handle the message.
 */
class LlmIntentClassifier(
    private val llmClient: LlamaClient
) : IntentClassifier {

    override suspend fun classify(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): IntentType {
        try {
            // Use recent history (last 4 messages) for context
            val recentHistory = history.takeLast(4)

            val systemPrompt = """
                You are an intent classifier for a markdown note-taking app called Krypton.
                You must output exactly one of these labels:
                - CREATE_NOTE
                - SEARCH_NOTES
                - SUMMARIZE_NOTE
                - NORMAL_CHAT

                CREATE_NOTE: user wants to create a new note or draft content for a new note.
                SEARCH_NOTES: user wants to search, find, or list notes.
                SUMMARIZE_NOTE: user wants a summary of the current note or a set of notes on a topic.
                NORMAL_CHAT: any other conversation, including questions and general chat.

                Respond with only the intent label, no explanation, no punctuation.
            """.trimIndent()

            val historyText = buildString {
                for (msg in recentHistory) {
                    appendLine("${msg.role}: ${msg.content}")
                }
            }

            val prompt = """
                $systemPrompt

                Conversation so far:
                $historyText

                User message:
                $message
            """.trimIndent()

            AppLogger.d("LlmIntentClassifier", "Classifying intent for message: \"$message\"")
            val raw = llmClient.complete(prompt)
            val token = raw.trim().uppercase()

            val intent = when (token) {
                "CREATE_NOTE" -> IntentType.CREATE_NOTE
                "SEARCH_NOTES" -> IntentType.SEARCH_NOTES
                "SUMMARIZE_NOTE" -> IntentType.SUMMARIZE_NOTE
                "NORMAL_CHAT" -> IntentType.NORMAL_CHAT
                else -> {
                    AppLogger.w("LlmIntentClassifier", "Unknown intent token: \"$token\", falling back to UNKNOWN")
                    IntentType.UNKNOWN
                }
            }

            AppLogger.d("LlmIntentClassifier", "Classified intent: $intent")
            return intent
        } catch (e: Exception) {
            AppLogger.e("LlmIntentClassifier", "Error classifying intent", e)
            return IntentType.UNKNOWN
        }
    }
}

