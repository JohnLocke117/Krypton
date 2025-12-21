package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.UbuntuSans_Regular

@Composable
@Preview
fun App() {
    val ubuntuFontFamily = FontFamily(
        Font(Res.font.UbuntuSans_Regular)
    )

    // Initialize settings repository
    val settingsRepository = remember { SettingsRepositoryImpl() }
    val settings by settingsRepository.settingsFlow.collectAsState()
    val colorScheme = buildColorSchemeFromSettings(settings)
    val theme = rememberObsidianTheme(settings)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = ubuntuFontFamily),
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = ubuntuFontFamily),
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = ubuntuFontFamily),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = ubuntuFontFamily),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = ubuntuFontFamily),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = ubuntuFontFamily),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = ubuntuFontFamily),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = ubuntuFontFamily),
            titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = ubuntuFontFamily),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = ubuntuFontFamily),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = ubuntuFontFamily),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = ubuntuFontFamily),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = ubuntuFontFamily),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = ubuntuFontFamily),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = ubuntuFontFamily)
        )
    ) {
        val state = rememberEditorState()
        
        // Load last opened folder on startup
        LaunchedEffect(settings.app.recentFolders) {
            if (state.currentDirectory == null && settings.app.recentFolders.isNotEmpty()) {
                val lastFolder = settings.app.recentFolders.firstOrNull()
                lastFolder?.let {
                    try {
                        val path = java.nio.file.Paths.get(it)
                        val file = path.toFile()
                        if (file.exists() && file.isDirectory) {
                            state.changeDirectoryWithHistory(path, settingsRepository)
                        }
                    } catch (e: Exception) {
                        // Invalid path, skip
                    }
                }
            }
        }
        
        // Load last opened folder on startup
        LaunchedEffect(Unit) {
            val lastFolder = settings.app.recentFolders.firstOrNull()
            if (lastFolder != null) {
                try {
                    val path = java.nio.file.Paths.get(lastFolder)
                    if (java.nio.file.Files.exists(path) && java.nio.file.Files.isDirectory(path)) {
                        state.changeDirectoryWithHistory(path, settingsRepository)
                    }
                } catch (e: Exception) {
                    // Invalid path, ignore
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.Background)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                        val isShift = keyEvent.isShiftPressed
                        
                        when {
                            // Settings
                            isCtrlOrCmd && keyEvent.key == Key.Comma -> {
                                state.openSettingsDialog()
                                true
                            }
                            // Search
                            isCtrlOrCmd && keyEvent.key == Key.F -> {
                                state.openSearchDialog(showReplace = false)
                                true
                            }
                            // Search & Replace
                            isCtrlOrCmd && keyEvent.key == Key.H -> {
                                state.openSearchDialog(showReplace = true)
                                true
                            }
                            // Find next
                            keyEvent.key == Key.F3 && !isShift -> {
                                state.findNext()
                                true
                            }
                            // Find previous
                            keyEvent.key == Key.F3 && isShift -> {
                                state.findPrevious()
                                true
                            }
                            // Close search
                            keyEvent.key == Key.Escape && state.searchState != null -> {
                                state.closeSearchDialog()
                                true
                            }
                            // Undo
                            isCtrlOrCmd && keyEvent.key == Key.Z && !isShift -> {
                                state.undo()
                                true
                            }
                            // Redo
                            (isCtrlOrCmd && keyEvent.key == Key.Z && isShift) ||
                            (isCtrlOrCmd && keyEvent.key == Key.Y) -> {
                                state.redo()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Ribbon
                LeftRibbon(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )

                // Left Sidebar
                LeftSidebar(
                    state = state,
                    onFolderSelected = {
                        openFolderDialog { selectedPath ->
                            selectedPath?.let { path ->
                                val file = path.toFile()
                                if (file.isDirectory) {
                                    state.changeDirectoryWithHistory(path, settingsRepository)
                                } else {
                                    val parentPath = file.parentFile?.toPath()
                                    if (parentPath != null) {
                                        state.changeDirectoryWithHistory(parentPath, settingsRepository)
                                    }
                                    state.openTab(path)
                                }
                            }
                        }
                    },
                    theme = theme,
                    settingsRepository = settingsRepository,
                    modifier = Modifier.fillMaxHeight()
                )

                // Center Editor Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TabBar(
                        state = state,
                        settings = settings,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextEditor(
                        state = state,
                        settingsRepository = settingsRepository,
                        onOpenFolder = {
                            openFolderDialog { selectedPath ->
                                selectedPath?.let { path ->
                                    val file = path.toFile()
                                    if (file.isDirectory) {
                                        state.changeDirectoryWithHistory(path, settingsRepository)
                                    } else {
                                        val parentPath = file.parentFile?.toPath()
                                        if (parentPath != null) {
                                            state.changeDirectoryWithHistory(parentPath, settingsRepository)
                                        }
                                        state.openTab(path)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Right Sidebar
                RightSidebar(
                    state = state,
                    theme = theme,
                    modifier = Modifier.fillMaxHeight()
                )

                // Right Ribbon
                RightRibbon(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            
            // Settings Dialog (overlay)
            SettingsDialog(
                state = state,
                settingsRepository = settingsRepository,
                onOpenSettingsJson = {
                    state.openSettingsJson()
                }
            )
            
            // Recent Folders Dialog
            if (state.showRecentFoldersDialog) {
                RecentFoldersDialog(
                    recentFolders = settings.app.recentFolders,
                    onFolderSelected = { path ->
                        state.changeDirectoryWithHistory(path, settingsRepository)
                    },
                    onOpenNewFolder = {
                        openFolderDialog { selectedPath ->
                            selectedPath?.let { path ->
                                val file = path.toFile()
                                if (file.isDirectory) {
                                    state.changeDirectoryWithHistory(path, settingsRepository)
                                } else {
                                    val parentPath = file.parentFile?.toPath()
                                    if (parentPath != null) {
                                        state.changeDirectoryWithHistory(parentPath, settingsRepository)
                                    }
                                    state.openTab(path)
                                }
                            }
                        }
                    },
                    onDismiss = {
                        state.dismissRecentFoldersDialog()
                    },
                    theme = theme
                )
            }
        }
    }
}

fun openFolderDialog(onResult: (java.nio.file.Path?) -> Unit) {
    val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
    fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
    fileChooser.dialogTitle = "Select Folder or File"
    
    val result = fileChooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val selectedFile = fileChooser.selectedFile
        onResult(selectedFile.toPath())
    } else {
        onResult(null)
    }
}
