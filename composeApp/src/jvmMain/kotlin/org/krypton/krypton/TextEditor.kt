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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Editor area
        if (activeTab != null) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                BasicTextField(
                    value = editorContent,
                    onValueChange = { newContent ->
                        editorContent = newContent
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "No file open\n\nSelect a file from the sidebar to open",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

