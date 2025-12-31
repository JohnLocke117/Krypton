
package org.krypton

import org.krypton.ui.state.SettingsCategory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SettingsDialog(
    state: org.krypton.ui.state.EditorStateHolder,
    settingsRepository: org.krypton.data.repository.SettingsRepository,
    onOpenSettingsJson: () -> Unit,
    onReindex: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    vaultPicker: org.krypton.platform.VaultPicker? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentSettings by settingsRepository.settingsFlow.collectAsState()
    val settingsDialogOpen by state.settingsDialogOpen.collectAsState()
    val selectedSettingsCategory by state.selectedSettingsCategory.collectAsState()
    var localSettings by remember { mutableStateOf(currentSettings) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val theme = rememberObsidianTheme(currentSettings)

    // Update local settings when dialog opens or current settings change externally
    LaunchedEffect(settingsDialogOpen, currentSettings) {
        if (settingsDialogOpen) {
            localSettings = currentSettings
            validationErrors = emptyList()
        }
    }
    
    // Function to apply settings (updates repository which triggers UI updates)
    suspend fun applySettings(): Boolean {
        val validation = validateSettings(localSettings)
        if (validation.isValid) {
            try {
                settingsRepository.update { localSettings }
                validationErrors = emptyList()
                return true
            } catch (e: Exception) {
                validationErrors = listOf(e.message ?: "Failed to apply settings")
                return false
            }
        } else {
            validationErrors = validation.errors
            return false
        }
    }

    if (settingsDialogOpen) {
        // Backdrop - only handle mouse clicks, not keyboard events
        // Use pointerInput instead of clickable to prevent space bar from triggering clicks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                }
                .onKeyEvent { 
                    // Don't handle keyboard events on backdrop - let them go to dialog content
                    // This prevents space bar from triggering backdrop click
                    false 
                },
            contentAlignment = Alignment.Center
        ) {
            // Dialog content - prevent clicks from propagating to backdrop
            Card(
                modifier = modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.9f)
                    .clickable(enabled = false, onClick = { /* Prevent click-through to backdrop */ })
                    .onKeyEvent { event ->
                        // Handle keyboard events - only Escape closes, all others pass through
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> {
                                    // Don't consume other keys - let text fields handle them
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.BackgroundElevated
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                color = theme.TextPrimary
                            )
                            IconButton(
                                onClick = onDismiss
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    tint = theme.TextSecondary
                                )
                            }
                        }
                    }

                    // Content - Two column layout
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left column - Categories
                        Surface(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight(),
                            color = theme.Background
                        ) {
                            SettingsCategoryList(
                                selectedCategory = selectedSettingsCategory,
                                onCategorySelected = { state.selectSettingsCategory(it) },
                                modifier = Modifier.fillMaxSize(),
                                theme = theme
                            )
                        }

                        Divider(
                            color = theme.Border,
                            modifier = Modifier.width(1.dp)
                        )

                        // Right column - Settings content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(theme.Surface)
                        ) {
                            SettingsContent(
                                category = selectedSettingsCategory,
                                settings = localSettings,
                                onSettingsChange = { localSettings = it },
                                onReindex = onReindex,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp),
                                theme = theme,
                                settingsRepository = settingsRepository,
                                vaultPicker = vaultPicker,
                                onOpenSettingsJson = onOpenSettingsJson
                            )
                        }
                    }

                    // Validation errors
                    if (validationErrors.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.BackgroundElevated
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                validationErrors.forEach { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Footer - Action buttons
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isRefreshing = true
                                            try {
                                                settingsRepository.reloadFromDisk()
                                                localSettings = settingsRepository.settingsFlow.value
                                                validationErrors = emptyList()
                                            } catch (e: Exception) {
                                                validationErrors = listOf(e.message ?: "Failed to reload settings")
                                            } finally {
                                                isRefreshing = false
                                            }
                                        }
                                    },
                                    enabled = !isRefreshing
                                ) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Refresh")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { onOpenSettingsJson() }
                            ) {
                                Text("Open Settings JSON")
                                }
                            }
                            Row {
                                // Apply button - applies settings but doesn't close
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            applySettings()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.Accent.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Text("Apply")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Save button - applies settings and closes
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = applySettings()
                                            // Only close if settings were successfully applied
                                            if (success) {
                                                onDismiss()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.Accent
                                    )
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryList(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    theme: ObsidianThemeValues
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsCategory.values().forEach { category ->
            SettingsCategoryItem(
                category = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.fillMaxWidth(),
                theme = theme
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: ObsidianThemeValues
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            theme.SelectionAccent
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                theme.Accent
            } else {
                theme.TextPrimary
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SettingsContent(
    category: SettingsCategory,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onReindex: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    theme: ObsidianThemeValues,
    settingsRepository: org.krypton.data.repository.SettingsRepository,
    vaultPicker: org.krypton.platform.VaultPicker?,
    onOpenSettingsJson: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = when (category) {
                SettingsCategory.UI -> "UI Settings"
                SettingsCategory.Colors -> "Color Settings"
                SettingsCategory.RAG -> "RAG Settings"
                else -> category.name
            },
            style = MaterialTheme.typography.headlineSmall,
            color = theme.TextPrimary
        )

        when (category) {
            SettingsCategory.General -> {
                GeneralSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    vaultPicker = vaultPicker,
                    onOpenSettingsJson = onOpenSettingsJson
                )
            }
            SettingsCategory.Editor -> {
                EditorSettingsContent(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    theme = theme
                )
            }
            SettingsCategory.Appearance -> {
                AppearanceSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    theme = theme
                )
            }
            SettingsCategory.UI -> {
                UISettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    theme = theme
                )
            }
            SettingsCategory.Colors -> {
                ColorSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    theme = theme
                )
            }
            SettingsCategory.RAG -> {
                RagSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onReindex = { onReindex?.invoke() },
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun GeneralSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues,
    settingsRepository: org.krypton.data.repository.SettingsRepository,
    vaultPicker: org.krypton.platform.VaultPicker?,
    onOpenSettingsJson: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val koin = remember { org.koin.core.context.GlobalContext.get() }
    val settingsConfigProvider = remember { 
        try {
            koin.getOrNull<org.krypton.platform.SettingsConfigProvider>()
        } catch (e: Exception) {
            null
        }
    }
    val currentSettingsFilePath = remember { 
        settingsConfigProvider?.getSettingsFilePath() ?: "settings.json"
    }
    var settingsFilePath by remember { mutableStateOf(currentSettingsFilePath) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings file path
        Text(
            text = "Settings File",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = settingsFilePath,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (vaultPicker != null) {
                        coroutineScope.launch {
                            val selectedPath: String? = vaultPicker.pickFile(
                                filter = org.krypton.platform.FileFilter("JSON Files", listOf("json"))
                            )
                            selectedPath?.let { pathString: String ->
                                val settingsConfigProvider = org.koin.core.context.GlobalContext.get().get<org.krypton.platform.SettingsConfigProvider>()
                                if (settingsConfigProvider.setSettingsFilePath(pathString)) {
                                    settingsFilePath = pathString
                                    // Reload settings from the new file
                                    settingsRepository.reloadFromDisk()
                                }
                            }
                        }
                    } else {
                        onOpenSettingsJson()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.Accent
                )
            ) {
                Text("Browse...")
            }
        }
        Text(
            text = "Select the settings.json file to use. Changes will be saved to this file.",
            style = MaterialTheme.typography.bodySmall,
            color = theme.TextTertiary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Autosave interval
        InlineTextField(
            label = "Autosave Interval (seconds)",
            value = settings.app.autosaveIntervalSeconds.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 0 && intValue <= 3600) {
                onSettingsChange(
                    settings.copy(
                                app = settings.app.copy(autosaveIntervalSeconds = intValue)
                    )
                )
                    }
                }
            },
            description = "Save changes automatically every N seconds (0-3600)",
            theme = theme
        )

        // Telemetry
        InlineBooleanField(
            label = "Telemetry",
            value = settings.app.telemetryEnabled,
            onValueChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            app = settings.app.copy(telemetryEnabled = enabled)
                        )
                    )
            },
            description = "Enable usage analytics and error reporting",
            theme = theme
            )

        // Recent folders
        Text(
            text = "Recent Folders",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        if (settings.app.recentFolders.isEmpty()) {
            Text(
                text = "No recent folders",
                style = MaterialTheme.typography.bodySmall,
                color = theme.TextSecondary
            )
        } else {
            settings.app.recentFolders.forEach { folder ->
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorSettingsContent(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InlineTextField(
            label = "Font Family",
            value = settings.editor.fontFamily,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(fontFamily = newValue)
                    )
                )
            },
            theme = theme
        )

        InlineTextField(
            label = "Font Size",
            value = settings.editor.fontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 72) {
                        onSettingsChange(
                            settings.copy(
                                editor = settings.editor.copy(fontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Font size in pixels (8-72)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Size",
            value = settings.editor.tabSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 1 && intValue <= 8) {
                        onSettingsChange(
                            settings.copy(
                                editor = settings.editor.copy(tabSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Number of spaces per tab (1-8)",
            theme = theme
        )

        InlineBooleanField(
            label = "Line Numbers",
            value = settings.editor.lineNumbers,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(lineNumbers = enabled)
                    )
                )
            },
            description = "Show line numbers in the editor",
            theme = theme
        )

        InlineBooleanField(
            label = "Word Wrap",
            value = settings.editor.wordWrap,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(wordWrap = enabled)
                    )
                )
            },
            description = "Wrap long lines in the editor",
            theme = theme
        )

        InlineFloatField(
            label = "Line Height",
            value = settings.editor.lineHeight,
            onValueChange = { newValue ->
                if (newValue >= 1.0f && newValue <= 3.0f) {
                    onSettingsChange(
                        settings.copy(
                            editor = settings.editor.copy(lineHeight = newValue)
                        )
                    )
                }
            },
            description = "Line height multiplier (1.0-3.0)",
            theme = theme
        )

        InlineTextField(
            label = "Editor Padding",
            value = settings.editor.editorPadding.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 0 && intValue <= 48) {
                        onSettingsChange(
                            settings.copy(
                                editor = settings.editor.copy(editorPadding = intValue)
                            )
                        )
                    }
                }
            },
            description = "Editor padding in pixels (0-48)",
            theme = theme
        )

        InlineTextField(
            label = "Code Block Font Size",
            value = settings.editor.codeBlockFontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 24) {
                        onSettingsChange(
                            settings.copy(
                                editor = settings.editor.copy(codeBlockFontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Code block font size in pixels (8-24)",
            theme = theme
        )

        InlineTextField(
            label = "Code Span Font Size",
            value = settings.editor.codeSpanFontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 24) {
                        onSettingsChange(
                            settings.copy(
                                editor = settings.editor.copy(codeSpanFontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Code span font size in pixels (8-24)",
            theme = theme
        )
    }
}

@Composable
private fun AppearanceSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InlineTextField(
            label = "Theme",
            value = settings.editor.theme,
            onValueChange = { newValue ->
                if (newValue.lowercase() in listOf("dark", "light")) {
                    onSettingsChange(
                        settings.copy(
                            editor = settings.editor.copy(theme = newValue.lowercase())
                        )
                    )
                }
            },
            description = "Theme name (dark or light)",
            theme = theme
        )
    }
}

@Composable
private fun UISettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InlineTextField(
            label = "Sidebar Default Width",
            value = settings.ui.sidebarDefaultWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= settings.ui.sidebarMinWidth && intValue <= settings.ui.sidebarMaxWidth) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(sidebarDefaultWidth = intValue)
                            )
                        )
                    }
                }
            },
            description = "Default sidebar width in pixels",
            theme = theme
        )

        InlineTextField(
            label = "Sidebar Min Width",
            value = settings.ui.sidebarMinWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 100 && intValue <= 500) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(sidebarMinWidth = intValue)
                            )
                        )
                    }
                }
            },
            description = "Minimum sidebar width in pixels (100-500)",
            theme = theme
        )

        InlineTextField(
            label = "Sidebar Max Width",
            value = settings.ui.sidebarMaxWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 200 && intValue <= 800) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(sidebarMaxWidth = intValue)
                            )
                        )
                    }
                }
            },
            description = "Maximum sidebar width in pixels (200-800)",
            theme = theme
        )

        InlineTextField(
            label = "Ribbon Width",
            value = settings.ui.ribbonWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 32 && intValue <= 80) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(ribbonWidth = intValue)
                            )
                        )
                    }
                }
            },
            description = "Ribbon width in pixels (32-80)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Height",
            value = settings.ui.tabHeight.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 24 && intValue <= 60) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(tabHeight = intValue)
                            )
                        )
                    }
                }
            },
            description = "Tab height in pixels (24-60)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Font Size",
            value = settings.ui.tabFontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 20) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(tabFontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Tab font size in pixels (8-20)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Padding",
            value = settings.ui.tabPadding.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 4 && intValue <= 24) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(tabPadding = intValue)
                            )
                        )
                    }
                }
            },
            description = "Tab padding in pixels (4-24)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Corner Radius",
            value = settings.ui.tabCornerRadius.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 0 && intValue <= 16) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(tabCornerRadius = intValue)
                            )
                        )
                    }
                }
            },
            description = "Tab corner radius in pixels (0-16)",
            theme = theme
        )

        InlineTextField(
            label = "Tab Label Font Size",
            value = settings.ui.tabLabelFontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 16) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(tabLabelFontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "Tab label font size in pixels (8-16)",
            theme = theme
        )

        InlineTextField(
            label = "Panel Border Width",
            value = settings.ui.panelBorderWidth.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 0 && intValue <= 4) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(panelBorderWidth = intValue)
                            )
                        )
                    }
                }
            },
            description = "Panel border width in pixels (0-4)",
            theme = theme
        )

        InlineTextField(
            label = "Panel Padding",
            value = settings.ui.panelPadding.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 0 && intValue <= 24) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(panelPadding = intValue)
                            )
                        )
                    }
                }
            },
            description = "Panel padding in pixels (0-24)",
            theme = theme
        )

        InlineTextField(
            label = "File Explorer Font Size",
            value = settings.ui.fileExplorerFontSize.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 8 && intValue <= 20) {
                        onSettingsChange(
                            settings.copy(
                                ui = settings.ui.copy(fileExplorerFontSize = intValue)
                            )
                        )
                    }
                }
            },
            description = "File explorer font size in pixels (8-20)",
            theme = theme
        )
    }
}

@Composable
private fun ColorSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Color values are stored as hex strings (e.g., #202020)",
            style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
        )
        
        // Background colors
        Text(
            text = "Background Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Background", settings.colors.background, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(background = newValue)))
        }, theme)
        ColorField("Background Elevated", settings.colors.backgroundElevated, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(backgroundElevated = newValue)))
        }, theme)
        ColorField("Background Hover", settings.colors.backgroundHover, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(backgroundHover = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Text colors
        Text(
            text = "Text Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Text Primary", settings.colors.textPrimary, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(textPrimary = newValue)))
        }, theme)
        ColorField("Text Secondary", settings.colors.textSecondary, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(textSecondary = newValue)))
        }, theme)
        ColorField("Text Tertiary", settings.colors.textTertiary, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(textTertiary = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Accent colors
        Text(
            text = "Accent Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Accent", settings.colors.accent, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(accent = newValue)))
        }, theme)
        ColorField("Accent Hover", settings.colors.accentHover, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(accentHover = newValue)))
        }, theme)
        ColorField("Accent Pressed", settings.colors.accentPressed, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(accentPressed = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Border colors
        Text(
            text = "Border Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Border", settings.colors.border, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(border = newValue)))
        }, theme)
        ColorField("Border Variant", settings.colors.borderVariant, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(borderVariant = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Surface colors
        Text(
            text = "Surface Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Surface", settings.colors.surface, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(surface = newValue)))
        }, theme)
        ColorField("Surface Variant", settings.colors.surfaceVariant, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(surfaceVariant = newValue)))
        }, theme)
        ColorField("Surface Container", settings.colors.surfaceContainer, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(surfaceContainer = newValue)))
        }, theme)
        ColorField("Selection", settings.colors.selection, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(selection = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Code colors
        Text(
            text = "Code Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Code Block Background", settings.colors.codeBlockBackground, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(codeBlockBackground = newValue)))
        }, theme)
        ColorField("Code Block Border", settings.colors.codeBlockBorder, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(codeBlockBorder = newValue)))
        }, theme)
        ColorField("Code Span Background", settings.colors.codeSpanBackground, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(codeSpanBackground = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Link colors
        Text(
            text = "Link Colors",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )
        
        ColorField("Link Color", settings.colors.linkColor, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(linkColor = newValue)))
        }, theme)
        ColorField("Link Hover", settings.colors.linkHover, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(linkHover = newValue)))
        }, theme)
        
        Divider(color = theme.Border)
        
        // Blockquote
        ColorField("Blockquote Background", settings.colors.blockquoteBackground, { newValue ->
            onSettingsChange(settings.copy(colors = settings.colors.copy(blockquoteBackground = newValue)))
        }, theme)
    }
}

@Composable
private fun InlineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

@Composable
private fun InlineBooleanField(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                when (newValue.lowercase()) {
                    "true" -> onValueChange(true)
                    "false" -> onValueChange(false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

@Composable
private fun InlineFloatField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                newValue.toFloatOrNull()?.let { floatValue ->
                    onValueChange(floatValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

@Composable
private fun ColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.TextPrimary,
                    unfocusedTextColor = theme.TextPrimary,
                    focusedBorderColor = theme.Accent,
                    unfocusedBorderColor = theme.Border
                )
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(parseHexColor(value))
            )
        }
    }
}

