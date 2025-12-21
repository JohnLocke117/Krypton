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
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorContent by remember(markdown) { mutableStateOf(markdown) }
    val scrollState = rememberScrollState()
    
    // Update editor content when markdown prop changes
    LaunchedEffect(markdown) {
        if (editorContent != markdown) {
            editorContent = markdown
        }
    }
    
    // Update parent when content changes
    LaunchedEffect(editorContent) {
        if (editorContent != markdown) {
            onMarkdownChange(editorContent)
        }
    }
    
    BasicTextField(
        value = editorContent,
        onValueChange = { newText ->
            editorContent = newText
        },
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.EditorPadding)
            .verticalScroll(scrollState),
        textStyle = TextStyle(
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            fontSize = 15.sp,
            color = ObsidianTheme.TextPrimary,
            lineHeight = (15.sp * ObsidianTheme.EditorLineHeight)
        )
    )
}


