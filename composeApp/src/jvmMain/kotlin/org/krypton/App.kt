package org.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.UbuntuSans_Regular
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.RagComponents
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.krypton.util.Logger
import org.krypton.Settings
import org.krypton.CatppuccinMochaColors
import org.krypton.platform.VaultPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.*

private fun handleKeyboardShortcut(
    keyEvent: KeyEvent,
    editorStateHolder: EditorStateHolder
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }
    
    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    val isShift = keyEvent.isShiftPressed
    
    return when {
        // Settings
        isCtrlOrCmd && keyEvent.key == Key.Comma -> {
            editorStateHolder.openSettingsDialog()
            true
        }
        // Search
        isCtrlOrCmd && keyEvent.key == Key.F -> {
            editorStateHolder.openSearchDialog(showReplace = false)
            true
        }
        // Search & Replace
        isCtrlOrCmd && keyEvent.key == Key.H -> {
            editorStateHolder.openSearchDialog(showReplace = true)
            true
        }
        // Find next
        keyEvent.key == Key.F3 && !isShift -> {
            editorStateHolder.findNext()
            true
        }
        // Find previous
        keyEvent.key == Key.F3 && isShift -> {
            editorStateHolder.findPrevious()
            true
        }
        // Close search
        keyEvent.key == Key.Escape -> {
            editorStateHolder.closeSearchDialog()
            true
        }
        // Undo
        isCtrlOrCmd && keyEvent.key == Key.Z && !isShift -> {
            editorStateHolder.undo()
            true
        }
        // Redo
        (isCtrlOrCmd && keyEvent.key == Key.Z && isShift) ||
        (isCtrlOrCmd && keyEvent.key == Key.Y) -> {
            editorStateHolder.redo()
            true
        }
        else -> false
    }
}

private fun handleFolderSelection(
    selectedPath: String?,
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository
) {
    selectedPath?.let { path ->
        val fileSystem = org.koin.core.context.GlobalContext.get().get<org.krypton.data.files.FileSystem>()
        val isDirectory = fileSystem.isDirectory(path)
        if (isDirectory) {
            editorStateHolder.changeDirectoryWithHistory(path)
            // Set current vault ID to the selected directory (assuming it's a vault root)
            // This allows vault-specific settings to be loaded
            settingsRepository.setCurrentVaultId(path)
        } else {
            // Extract parent directory from path string
            val lastSeparator = path.lastIndexOf('/')
            val parentPath = if (lastSeparator > 0) path.substring(0, lastSeparator) else null
            if (parentPath != null) {
                editorStateHolder.changeDirectoryWithHistory(parentPath)
                // Set current vault ID to parent directory
                settingsRepository.setCurrentVaultId(parentPath)
            }
            editorStateHolder.openTab(path)
        }
    }
}
