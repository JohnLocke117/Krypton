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
import kotlinx.coroutines.launch

@Composable
fun TextEditor(
    state: EditorState,
    settingsRepository: SettingsRepository?,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val activeTabIndex = state.activeTabIndex
    val activeDocument = state.getActiveTab()
    val settingsPath = SettingsPersistence.getSettingsFilePath()
    var showErrorSnackbar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val settings = settingsRepository?.settingsFlow?.collectAsState()?.value ?: Settings()
    val theme = rememberObsidianTheme(settings)
    
    // Auto-save with debouncing - use settings interval
    LaunchedEffect(activeDocument?.text, activeTabIndex, settings.app.autosaveIntervalSeconds) {
        activeDocument?.let { doc ->
            if (doc.isDirty && doc.path != null) {
                val delayMs = settings.app.autosaveIntervalSeconds * 1000L
                kotlinx.coroutines.delay(delayMs.coerceAtLeast(1000)) // Minimum 1 second
                
                // Special handling for settings.json
                if (doc.path == settingsPath && settingsRepository != null) {
                    // Parse and validate JSON from document text
                    val parsed = SettingsPersistence.parseSettingsFromJson(doc.text)
                    if (parsed != null) {
                        val validation = validateSettings(parsed)
                        if (validation.isValid) {
                            // Update repository and save file
                            coroutineScope.launch {
                                try {
                                    settingsRepository.update { parsed }
                                    FileManager.writeFile(doc.path, doc.text)
                                    state.saveActiveTab()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to update settings"
                                    showErrorSnackbar = true
                                }
                            }
                        } else {
                            // Validation failed, show error but don't save
                            errorMessage = "Settings validation failed: ${validation.errors.joinToString(", ")}"
                            showErrorSnackbar = true
                            // Don't call saveActiveTab() - keep the document dirty
                            return@LaunchedEffect
                        }
                    } else {
                        // JSON parsing failed
                        errorMessage = "Invalid JSON format. Please check your syntax."
                        showErrorSnackbar = true
                        // Don't save invalid JSON
                        return@LaunchedEffect
                    }
                } else {
                    // Normal file save
                    FileManager.writeFile(doc.path, doc.text)
                    state.saveActiveTab()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.Background)
            .padding(theme.EditorPadding)
    ) {
        // Editor area
        if (activeDocument != null) {
            // Card wrapper for editor content
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.BackgroundElevated
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // Route based on view mode
                when (activeDocument.viewMode) {
                    ViewMode.LivePreview -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MarkdownLivePreviewEditor(
                                markdown = activeDocument.text,
                                settings = settings,
                                theme = theme,
                                searchState = state.searchState,
                                onMarkdownChange = { newText ->
                                    state.updateTabContent(newText)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Search dialog overlay
                            state.searchState?.let { searchState ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    SearchDialog(
                                        state = state,
                                        theme = theme,
                                        onSearchUpdate = { newState ->
                                            state.updateSearchState { newState }
                                        },
                                        onReplace = { newText ->
                                            state.updateTabContent(newText)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    ViewMode.Compiled -> {
                        MarkdownCompiledView(
                            markdown = activeDocument.text,
                            settings = settings,
                            theme = theme,
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

    // Error snackbar
    if (showErrorSnackbar) {
        LaunchedEffect(showErrorSnackbar) {
            kotlinx.coroutines.delay(5000) // Auto-dismiss after 5 seconds
            showErrorSnackbar = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.fillMaxWidth(0.8f),
                action = {
                    TextButton(onClick = { showErrorSnackbar = false }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
    }
}

