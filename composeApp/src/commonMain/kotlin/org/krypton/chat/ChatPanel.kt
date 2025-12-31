package org.krypton.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.krypton.ObsidianThemeValues
import org.krypton.Settings
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder

/**
 * Platform-agnostic chat panel composable.
 * 
 * This is an expect declaration that must be implemented in platform-specific code.
 * 
 * @param chatStateHolder State holder for chat functionality
 * @param editorStateHolder Optional editor state holder for context
 * @param theme Theme values for styling
 * @param settings Current application settings
 * @param modifier Modifier to apply to the chat panel
 */
@Composable
expect fun ChatPanel(
    chatStateHolder: ChatStateHolder,
    editorStateHolder: EditorStateHolder?,
    theme: ObsidianThemeValues,
    settings: Settings,
    modifier: Modifier
)
