package org.krypton.krypton

import org.krypton.krypton.ui.state.SettingsCategory
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
    state: org.krypton.krypton.ui.state.EditorStateHolder,
    settingsRepository: org.krypton.krypton.data.repository.SettingsRepository,
    onOpenSettingsJson: () -> Unit,
    onReindex: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentSettings by settingsRepository.settingsFlow.collectAsState()
    val settingsDialogOpen by state.settingsDialogOpen.collectAsState()
    val selectedSettingsCategory by state.selectedSettingsCategory.collectAsState()
    var localSettings by remember { mutableStateOf(currentSettings) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    val theme = rememberObsidianTheme(currentSettings)

    // Update local settings when dialog opens or current settings change externally
    LaunchedEffect(settingsDialogOpen, currentSettings) {
        if (settingsDialogOpen) {
            localSettings = currentSettings
            validationErrors = emptyList()
        }
    }

    if (settingsDialogOpen) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
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
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = onDismiss
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = MaterialTheme.colorScheme.background
                        ) {
                            SettingsCategoryList(
                                selectedCategory = selectedSettingsCategory,
                                onCategorySelected = { state.selectSettingsCategory(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Divider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(1.dp)
                        )

                        // Right column - Settings content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            SettingsContent(
                                category = selectedSettingsCategory,
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
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                    onClick = onDismiss
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
                                                    onDismiss()
                                                } catch (e: Exception) {
                                                    validationErrors = listOf(e.message ?: "Failed to save settings")
                                                }
                                            }
                                        } else {
                                            validationErrors = validation.errors
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
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
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
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
            color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Save changes automatically every ${settings.app.autosaveIntervalSeconds} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.app.autosaveIntervalSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enable usage analytics and error reporting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        Divider(color = MaterialTheme.colorScheme.outline)

        // Recent folders
        Text(
            text = "Recent Folders",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (settings.app.recentFolders.isEmpty()) {
            Text(
                text = "No recent folders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            settings.app.recentFolders.forEach { folder ->
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.fontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.editor.fontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.tabSize} spaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.editor.tabSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Show line numbers in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Wrap long lines in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.lineHeight}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = String.format("%.1f", settings.editor.lineHeight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.editorPadding}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.editor.editorPadding}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.codeBlockFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.editor.codeBlockFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.editor.codeSpanFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.editor.codeSpanFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurface
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
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
            color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.ui.sidebarDefaultWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.ui.sidebarDefaultWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

        // Tab settings
        Text(
            text = "Tab Settings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.ui.tabHeight}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.ui.tabHeight}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

        Divider(color = MaterialTheme.colorScheme.outline)

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${settings.ui.tabFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${settings.ui.tabFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Color values are stored as hex strings (e.g., #202020)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
