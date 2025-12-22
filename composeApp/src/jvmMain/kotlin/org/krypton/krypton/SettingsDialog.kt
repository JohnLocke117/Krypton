package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close

@Composable
fun SettingsDialog(
    state: EditorState,
    settingsRepository: SettingsRepository,
    onOpenSettingsJson: () -> Unit,
    onReindex: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentSettings by settingsRepository.settingsFlow.collectAsState()
    var localSettings by remember { mutableStateOf(currentSettings) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    val theme = rememberObsidianTheme(currentSettings)

    // Update local settings when dialog opens or current settings change externally
    LaunchedEffect(state.settingsDialogOpen, currentSettings) {
        if (state.settingsDialogOpen) {
            localSettings = currentSettings
            validationErrors = emptyList()
        }
    }

    if (state.settingsDialogOpen) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = { state.closeSettingsDialog() }),
            contentAlignment = Alignment.Center
        ) {
            // Dialog content
            Card(
                modifier = modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.9f),
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
                        color = ObsidianTheme.SurfaceContainer
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
                                color = ObsidianTheme.TextPrimary
                            )
                            IconButton(
                                onClick = { state.closeSettingsDialog() }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
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
                            color = ObsidianTheme.Background
                        ) {
                            SettingsCategoryList(
                                selectedCategory = state.selectedSettingsCategory,
                                onCategorySelected = { state.selectSettingsCategory(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Divider(
                            color = ObsidianTheme.Border,
                            modifier = Modifier.width(1.dp)
                        )

                        // Right column - Settings content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(ObsidianTheme.BackgroundElevated)
                        ) {
                            SettingsContent(
                                category = state.selectedSettingsCategory,
                                settings = localSettings,
                                onSettingsChange = { localSettings = it },
                                onReindex = onReindex,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp)
                            )
                        }
                    }

                    // Validation errors
                    if (validationErrors.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                validationErrors.forEach { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Footer - Action buttons
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ObsidianTheme.SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onOpenSettingsJson() }
                            ) {
                                Text("Open Settings JSON")
                            }
                            Row {
                                TextButton(
                                    onClick = { state.closeSettingsDialog() }
                                ) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val validation = validateSettings(localSettings)
                                        if (validation.isValid) {
                                            coroutineScope.launch {
                                                try {
                                                    settingsRepository.update { localSettings }
                                                    state.closeSettingsDialog()
                                                } catch (e: Exception) {
                                                    validationErrors = listOf(e.message ?: "Failed to save settings")
                                                }
                                            }
                                        } else {
                                            validationErrors = validation.errors
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ObsidianTheme.Accent
                                    )
                                ) {
                                    Text("Apply")
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
    modifier: Modifier = Modifier
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            ObsidianTheme.SelectionAccent
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                ObsidianTheme.Accent
            } else {
                ObsidianTheme.TextPrimary
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
    modifier: Modifier = Modifier
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
            color = ObsidianTheme.TextPrimary
        )

        when (category) {
            SettingsCategory.General -> {
                GeneralSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
            SettingsCategory.Editor -> {
                EditorSettingsContent(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
            SettingsCategory.Appearance -> {
                AppearanceSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
            SettingsCategory.UI -> {
                UISettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
            SettingsCategory.Colors -> {
                ColorSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
            }
            SettingsCategory.Keybindings -> {
                KeybindingsSettings()
            }
            SettingsCategory.RAG -> {
                RagSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onReindex = { onReindex?.invoke() }
                )
            }
        }
    }
}

@Composable
private fun GeneralSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Autosave interval
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Autosave Interval",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Save changes automatically every ${settings.app.autosaveIntervalSeconds} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.app.autosaveIntervalSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.app.autosaveIntervalSeconds.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        app = settings.app.copy(autosaveIntervalSeconds = newValue.toInt())
                    )
                )
            },
            valueRange = 0f..3600f,
            steps = 59, // 60 second increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Telemetry",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Enable usage analytics and error reporting",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Switch(
                checked = settings.app.telemetryEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            app = settings.app.copy(telemetryEnabled = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = ObsidianTheme.Border)

        // Recent folders
        Text(
            text = "Recent Folders",
            style = MaterialTheme.typography.bodyLarge,
            color = ObsidianTheme.TextPrimary
        )
        if (settings.app.recentFolders.isEmpty()) {
            Text(
                text = "No recent folders",
                style = MaterialTheme.typography.bodySmall,
                color = ObsidianTheme.TextSecondary
            )
        } else {
            settings.app.recentFolders.forEach { folder ->
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ObsidianTheme.TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorSettingsContent(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Font family
        OutlinedTextField(
            value = settings.editor.fontFamily,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(fontFamily = newValue)
                    )
                )
            },
            label = { Text("Font Family") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ObsidianTheme.TextPrimary,
                unfocusedTextColor = ObsidianTheme.TextPrimary,
                focusedBorderColor = ObsidianTheme.Accent,
                unfocusedBorderColor = ObsidianTheme.Border
            )
        )

        // Font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.fontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.fontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.fontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(fontSize = newValue.toInt())
                    )
                )
            },
            valueRange = 8f..72f,
            steps = 63, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Tab size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.tabSize} spaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.tabSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.tabSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(tabSize = newValue.toInt())
                    )
                )
            },
            valueRange = 1f..8f,
            steps = 6, // 1 space increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Line numbers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Line Numbers",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Show line numbers in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Switch(
                checked = settings.editor.lineNumbers,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            editor = settings.editor.copy(lineNumbers = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = ObsidianTheme.Border)

        // Word wrap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Word Wrap",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Wrap long lines in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Switch(
                checked = settings.editor.wordWrap,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            editor = settings.editor.copy(wordWrap = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = ObsidianTheme.Border)

        // Line height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Line Height",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.lineHeight}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = String.format("%.1f", settings.editor.lineHeight),
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.lineHeight,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(lineHeight = newValue)
                    )
                )
            },
            valueRange = 1.0f..3.0f,
            steps = 19, // 0.1 increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Editor padding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Editor Padding",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.editorPadding}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.editorPadding}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.editorPadding.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(editorPadding = newValue.toInt())
                    )
                )
            },
            valueRange = 0f..48f,
            steps = 47, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Code block font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Code Block Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.codeBlockFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.codeBlockFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.codeBlockFontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(codeBlockFontSize = newValue.toInt())
                    )
                )
            },
            valueRange = 8f..24f,
            steps = 15, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Code span font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Code Span Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.editor.codeSpanFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.codeSpanFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.editor.codeSpanFontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        editor = settings.editor.copy(codeSpanFontSize = newValue.toInt())
                    )
                )
            },
            valueRange = 8f..24f,
            steps = 15, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = ObsidianTheme.TextPrimary
        )
        var expanded by remember { mutableStateOf(false) }
        val themes = listOf("dark", "light")
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = settings.editor.theme,
                onValueChange = {},
                readOnly = true,
                label = { Text("Theme") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ObsidianTheme.TextPrimary,
                    unfocusedTextColor = ObsidianTheme.TextPrimary,
                    focusedBorderColor = ObsidianTheme.Accent,
                    unfocusedBorderColor = ObsidianTheme.Border
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                themes.forEach { theme ->
                    DropdownMenuItem(
                        text = { Text(theme.replaceFirstChar { it.uppercaseChar() }) },
                        onClick = {
                            onSettingsChange(
                                settings.copy(
                                    editor = settings.editor.copy(theme = theme)
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UISettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sidebar widths
        Text(
            text = "Sidebar Widths",
            style = MaterialTheme.typography.titleMedium,
            color = ObsidianTheme.TextPrimary
        )
        
        // Sidebar default width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sidebar Default Width",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.ui.sidebarDefaultWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.sidebarDefaultWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.sidebarDefaultWidth.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(sidebarDefaultWidth = newValue.toInt())
                    )
                )
            },
            valueRange = settings.ui.sidebarMinWidth.toFloat()..settings.ui.sidebarMaxWidth.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Tab settings
        Text(
            text = "Tab Settings",
            style = MaterialTheme.typography.titleMedium,
            color = ObsidianTheme.TextPrimary
        )

        // Tab height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Height",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabHeight}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabHeight}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.tabHeight.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(tabHeight = newValue.toInt())
                    )
                )
            },
            valueRange = 24f..60f,
            steps = 35, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // Tab font sizes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.tabFontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(tabFontSize = newValue.toInt())
                    )
                )
            },
            valueRange = 8f..20f,
            steps = 11, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Color customization coming soon. Edit colors directly in settings.json for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
        Text(
            text = "Color values are stored as hex strings (e.g., #202020)",
            style = MaterialTheme.typography.bodySmall,
            color = ObsidianTheme.TextTertiary
        )
    }
}

@Composable
private fun KeybindingsSettings() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Keybindings settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
    }
}
