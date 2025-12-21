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
    modifier: Modifier = Modifier
) {
    val activeTabIndex = state.activeTabIndex
    val activeTab = state.getActiveTab()
    val scrollState = rememberScrollState()
    
    // Track content separately to ensure reactivity
    var editorContent by remember(activeTabIndex) { 
        mutableStateOf(activeTab?.content ?: "") 
    }
    
    // Update editor content when tab changes
    LaunchedEffect(activeTabIndex) {
        editorContent = activeTab?.content ?: ""
        scrollState.scrollTo(0)
    }
    
    // Auto-save with debouncing
    LaunchedEffect(editorContent, activeTabIndex) {
        activeTab?.let { tab ->
            if (tab.content != editorContent) {
                tab.content = editorContent
                tab.isModified = true
            }
            if (tab.isModified) {
                kotlinx.coroutines.delay(1000) // 1 second debounce
                FileManager.writeFile(tab.path, tab.content)
                tab.isModified = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianTheme.Background)
    ) {
        // Editor area
        if (activeTab != null) {
            BasicTextField(
                value = editorContent,
                onValueChange = { newContent ->
                    editorContent = newContent
                },
                modifier = Modifier
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
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No file open\n\nSelect a file from the sidebar to open",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

