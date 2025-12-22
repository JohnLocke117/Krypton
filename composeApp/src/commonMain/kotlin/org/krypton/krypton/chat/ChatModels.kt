package org.krypton.krypton.chat

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

