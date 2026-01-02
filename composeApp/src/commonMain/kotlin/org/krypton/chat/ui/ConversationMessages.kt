package org.krypton.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.krypton.chat.ChatMessage
import org.krypton.chat.ui.ChatMessageList
import org.krypton.ObsidianThemeValues
import org.krypton.Settings

/**
 * Composable for displaying messages from a conversation.
 * 
 * Reuses the existing ChatMessageList component.
 * 
 * @param messages List of messages to display
 * @param isLoading Whether messages are currently loading
 * @param theme Theme values for styling
 * @param settings Application settings
 * @param modifier Modifier for the composable
 */
@Composable
fun ConversationMessages(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    theme: ObsidianThemeValues,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    ChatMessageList(
        messages = messages,
        isLoading = isLoading,
        theme = theme,
        settings = settings,
        messageMetadata = emptyMap(), // Conversation messages don't have metadata
        modifier = modifier
    )
}

