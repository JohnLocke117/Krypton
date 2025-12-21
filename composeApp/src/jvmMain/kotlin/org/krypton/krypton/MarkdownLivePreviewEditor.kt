package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live Preview editor for Markdown.
 * Editable text field with inline Markdown styling (simpler approach - shows markers but styles text).
 */
@Composable
fun MarkdownLivePreviewEditor(
    markdown: String,
    settings: Settings,
    theme: ObsidianThemeValues,
    searchState: SearchState?,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorContent by remember(markdown) { mutableStateOf(markdown) }
    val scrollState = rememberScrollState()
    var lastHistoryPush by remember { mutableStateOf(markdown) }
    
    // Get font family from settings
    val fontFamily = remember(settings.editor.fontFamily) {
        when (settings.editor.fontFamily.lowercase()) {
            "monospace", "jetbrains mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
            else -> androidx.compose.ui.text.font.FontFamily.Default
        }
    }
    
    // Note: Search highlighting in the editor text itself would require a custom text field
    // For now, search functionality works via the search dialog with match navigation
    // The search dialog shows match count and allows navigation between matches
    
    // Update editor content when markdown prop changes (e.g., from undo/redo)
    LaunchedEffect(markdown) {
        if (editorContent != markdown) {
            editorContent = markdown
            lastHistoryPush = markdown
        }
    }
    
    // Update parent when content changes, with debouncing for history
    LaunchedEffect(editorContent) {
        if (editorContent != markdown) {
            onMarkdownChange(editorContent)
            
            // Debounce history pushes - only push if content has changed significantly
            // or after a delay to avoid too many history entries
            kotlinx.coroutines.delay(500) // 500ms debounce
            if (editorContent == markdown) {
                // Content was updated externally (undo/redo), don't push
                return@LaunchedEffect
            }
            // The actual history push happens in EditorState.updateTabContent
        }
    }
    
    // Scroll to current match when it changes
    LaunchedEffect(searchState?.currentMatchIndex) {
        searchState?.let { state ->
            if (state.currentMatchIndex >= 0 && state.currentMatchIndex < state.matches.size) {
                val match = state.matches[state.currentMatchIndex]
                // Calculate approximate scroll position (this is a simplified approach)
                // In a real implementation, you'd need to measure text layout
                val lineNumber = editorContent.substring(0, match.first).split('\n').size - 1
                val lineHeight = (settings.editor.fontSize * settings.editor.lineHeight).dp.value
                val targetScroll = (lineNumber * lineHeight).toInt()
                scrollState.animateScrollTo(targetScroll)
            }
        }
    }
    
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        // Line numbers
        if (settings.editor.lineNumbers) {
            LineNumbers(
                text = editorContent,
                settings = settings,
                theme = theme,
                editorScrollState = scrollState,
                modifier = Modifier.fillMaxHeight()
            )
            // Divider between line numbers and editor
            VerticalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = theme.Border
            )
        }
        
        // Editor
        BasicTextField(
            value = editorContent,
            onValueChange = { newText ->
                editorContent = newText
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(theme.EditorPadding)
                .verticalScroll(scrollState),
            textStyle = TextStyle(
                fontFamily = fontFamily,
                fontSize = settings.editor.fontSize.sp,
                color = theme.TextPrimary,
                lineHeight = (settings.editor.fontSize.sp * settings.editor.lineHeight)
            )
        )
    }
}


