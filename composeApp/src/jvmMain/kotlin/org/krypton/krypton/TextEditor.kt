package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextEditor(
    state: EditorState,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTabIndex = state.activeTabIndex
    val activeDocument = state.getActiveTab()
    
    // Auto-save with debouncing
    LaunchedEffect(activeDocument?.text, activeTabIndex) {
        activeDocument?.let { doc ->
            if (doc.isDirty && doc.path != null) {
                kotlinx.coroutines.delay(1000) // 1 second debounce
                FileManager.writeFile(doc.path, doc.text)
                state.saveActiveTab()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianTheme.Background)
            .padding(24.dp)
    ) {
        // Editor area
        if (activeDocument != null) {
            // Card wrapper for editor content
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ObsidianTheme.BackgroundElevated
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // Route based on view mode
                when (activeDocument.viewMode) {
                    ViewMode.LivePreview -> {
                        MarkdownLivePreviewEditor(
                            markdown = activeDocument.text,
                            onMarkdownChange = { newText ->
                                state.updateTabContent(newText)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ViewMode.Compiled -> {
                        MarkdownCompiledView(
                            markdown = activeDocument.text,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            // Welcome card when no file is open
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                WelcomeCard(
                    onNewFile = {
                        state.startCreatingNewFile()
                    },
                    onOpenFolder = onOpenFolder
                )
            }
        }
    }
}

