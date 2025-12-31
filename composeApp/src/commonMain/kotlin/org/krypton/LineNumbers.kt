package org.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.krypton.Settings
import org.krypton.LocalAppColors

/**
 * Displays line numbers alongside the editor.
 * Synchronizes scrolling with the editor content.
 */
@Composable
fun LineNumbers(
    text: String,
    settings: Settings,
    theme: ObsidianThemeValues,
    editorScrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    if (!settings.editor.lineNumbers) {
        return
    }

    val lines = remember(text) {
        if (text.isEmpty()) {
            listOf(1)
        } else {
            val lineCount = text.split('\n').size
            (1..lineCount).toList()
        }
    }

    val lineNumberWidth = remember(lines.size) {
        // Calculate width based on number of digits
        val digits = lines.size.toString().length
        (digits * 8 + 16).dp // ~8dp per digit + padding
    }

    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .width(lineNumberWidth)
            .fillMaxHeight()
            .background(appColors.editorBackground) // Mantle for line numbers (matches editor)
            .verticalScroll(editorScrollState) // Sync with editor scroll
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = theme.EditorPadding.value.toInt().dp)
        ) {
            lines.forEach { lineNumber ->
                Text(
                    text = lineNumber.toString(),
                    style = TextStyle(
                        fontSize = settings.editor.fontSize.sp,
                        color = colorScheme.onSurfaceVariant, // Secondary text color for line numbers
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((settings.editor.fontSize * settings.editor.lineHeight).dp)
                )
            }
        }
    }
}

