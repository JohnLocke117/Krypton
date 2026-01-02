package org.krypton

import org.krypton.core.domain.editor.ViewMode
import androidx.compose.foundation.Image
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
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.polymer
import kotlinx.coroutines.launch

@Composable
fun TextEditor(
    state: org.krypton.ui.state.EditorStateHolder,
    settingsRepository: org.krypton.data.repository.SettingsRepository?,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val activeTabIndex by state.activeTabIndex.collectAsState()
    val activeDocument by state.activeDocument.collectAsState()
    val settingsPath = org.krypton.data.repository.impl.JvmSettingsPersistence.getSettingsFilePath()
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
                    val parsed = org.krypton.data.repository.impl.JvmSettingsPersistence.parseSettingsFromJson(doc.text)
                    if (parsed != null) {
                        val validation = validateSettings(parsed)
                        if (validation.isValid) {
                            // Update repository and save file
                            coroutineScope.launch {
                                try {
                                    settingsRepository.update { parsed }
                                    // File will be saved by the domain layer
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
                    // Normal file save - handled by domain layer
                    state.saveActiveTab()
                }
            }
        }
    }

    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.editorBackground) // Mantle - ensures entire editor area has correct background
            .padding(top = theme.EditorPadding, start = theme.EditorPadding, end = theme.EditorPadding)
    ) {
        // Editor area
        val currentActiveDocument = activeDocument
        if (currentActiveDocument != null) {
            // Route based on view mode
            when (currentActiveDocument.viewMode) {
                ViewMode.LivePreview -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MarkdownLivePreviewEditor(
                            markdown = currentActiveDocument.text,
                            settings = settings,
                            theme = theme,
                            searchState = null, // TODO: Get from SearchStateHolder
                            onMarkdownChange = { newText ->
                                state.updateTabContent(newText)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // TODO: Add search dialog overlay using SearchStateHolder
                    }
                }
                ViewMode.Compiled -> {
                    MarkdownCompiledView(
                        markdown = currentActiveDocument.text,
                        settings = settings,
                        theme = theme,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // App logo with 50% opacity when no file is open
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.polymer),
                    contentDescription = "Krypton",
                    modifier = Modifier.size(128.dp),
                    alpha = 0.5f
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

