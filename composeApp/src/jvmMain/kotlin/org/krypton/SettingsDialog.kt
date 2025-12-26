package org.krypton

import org.krypton.ui.state.SettingsCategory
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close

@Composable
fun SettingsDialog(
    state: org.krypton.ui.state.EditorStateHolder,
    settingsRepository: org.krypton.data.repository.SettingsRepository,
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
    var isRefreshing by remember { mutableStateOf(false) }
    val theme = rememberObsidianTheme(currentSettings)

    // Update local settings when dialog opens or current settings change externally
    LaunchedEffect(settingsDialogOpen, currentSettings) {
        if (settingsDialogOpen) {
            localSettings = currentSettings
            validationErrors = emptyList()
        }
    }
    
    // Persist settings immediately when changed (with debouncing)
    LaunchedEffect(localSettings) {
        if (settingsDialogOpen && localSettings != currentSettings) {
            val validation = validateSettings(localSettings)
            if (validation.isValid) {
                delay(300) // Debounce 300ms
                if (localSettings != currentSettings) { // Check again after delay
                    try {
                        settingsRepository.update { localSettings }
                    } catch (e: Exception) {
                        validationErrors = listOf(e.message ?: "Failed to save settings")
                    }
                }
            }
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
                                Image(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(theme.TextSecondary)
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
                                theme = theme
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
                                TextButton(
                                    onClick = onDismiss
                                ) {
                                    Text("Close")
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
                                        containerColor = theme.Accent
                                    )
                                ) {
                                    Text("Apply & Close")
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
    theme: ObsidianThemeValues
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
                    theme = theme
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
            SettingsCategory.Keybindings -> {
                KeybindingsSettings(theme = theme)
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
    theme: ObsidianThemeValues
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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Save changes automatically every ${settings.app.autosaveIntervalSeconds} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.app.autosaveIntervalSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Enable usage analytics and error reporting",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
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

        Divider(color = theme.Border)

        // Recent folders
        Text(
            text = "Recent Folders",
            style = MaterialTheme.typography.bodyLarge,
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
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.fontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.fontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.tabSize} spaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.tabSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Show line numbers in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Wrap long lines in the editor",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.lineHeight}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = String.format("%.1f", settings.editor.lineHeight),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.editorPadding}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.editorPadding}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.codeBlockFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.codeBlockFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.editor.codeSpanFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.editor.codeSpanFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = theme.TextPrimary
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
                    focusedTextColor = theme.TextPrimary,
                    unfocusedTextColor = theme.TextPrimary,
                    focusedBorderColor = theme.Accent,
                    unfocusedBorderColor = theme.Border
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
    onSettingsChange: (Settings) -> Unit,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sidebar widths
        Text(
            text = "Sidebar Widths",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.sidebarDefaultWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.sidebarDefaultWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

        // Sidebar min width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sidebar Min Width",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.sidebarMinWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.sidebarMinWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.sidebarMinWidth.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(sidebarMinWidth = newValue.toInt())
                    )
                )
            },
            valueRange = 100f..500f,
            steps = 39, // 10px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Sidebar max width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sidebar Max Width",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.sidebarMaxWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.sidebarMaxWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.sidebarMaxWidth.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(sidebarMaxWidth = newValue.toInt())
                    )
                )
            },
            valueRange = 200f..800f,
            steps = 59, // 10px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Ribbon width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ribbon Width",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.ribbonWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.ribbonWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.ribbonWidth.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(ribbonWidth = newValue.toInt())
                    )
                )
            },
            valueRange = 32f..80f,
            steps = 47, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Tab settings
        Text(
            text = "Tab Settings",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabHeight}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabHeight}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

        // Tab padding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Padding",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabPadding}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabPadding}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.tabPadding.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(tabPadding = newValue.toInt())
                    )
                )
            },
            valueRange = 4f..24f,
            steps = 19, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Tab corner radius
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Corner Radius",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabCornerRadius}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabCornerRadius}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.tabCornerRadius.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(tabCornerRadius = newValue.toInt())
                    )
                )
            },
            valueRange = 0f..16f,
            steps = 15, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Tab label font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab Label Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.tabLabelFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.tabLabelFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.tabLabelFontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(tabLabelFontSize = newValue.toInt())
                    )
                )
            },
            valueRange = 8f..16f,
            steps = 7, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Panel settings
        Text(
            text = "Panel Settings",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )

        // Panel border width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Panel Border Width",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.panelBorderWidth}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.panelBorderWidth}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.panelBorderWidth.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(panelBorderWidth = newValue.toInt())
                    )
                )
            },
            valueRange = 0f..4f,
            steps = 3, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Panel padding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Panel Padding",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.panelPadding}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.panelPadding}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.panelPadding.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(panelPadding = newValue.toInt())
                    )
                )
            },
            valueRange = 0f..24f,
            steps = 23, // 1px increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // File explorer font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "File Explorer Font Size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "${settings.ui.fileExplorerFontSize}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.ui.fileExplorerFontSize}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.ui.fileExplorerFontSize.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        ui = settings.ui.copy(fileExplorerFontSize = newValue.toInt())
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
private fun ColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    theme: ObsidianThemeValues
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(parseHexColor(value))
        )
    }
}

@Composable
private fun KeybindingsSettings(
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Keybindings settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextSecondary
        )
    }
}
