package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.UbuntuSans_Regular
import org.krypton.krypton.chat.ChatService
import org.krypton.krypton.chat.ChatServiceFactory
import org.krypton.krypton.rag.*
import org.krypton.krypton.util.Logger
import org.krypton.krypton.util.createLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val ubuntuFontFamily = FontFamily(
        Font(Res.font.UbuntuSans_Regular)
    )

    // Initialize logger
    val logger = remember { createLogger("App") }

    // Initialize settings repository
    val settingsRepository = remember { SettingsRepositoryImpl() }
    val settings by settingsRepository.settingsFlow.collectAsState()
    val colorScheme = buildColorSchemeFromSettings(settings)
    val theme = rememberObsidianTheme(settings)
    
    // Create theme colors and app colors
    val themeColors = remember(settings) { AppThemeColors(settings) }
    val appColors = rememberAppColors(themeColors)

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
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = ubuntuFontFamily,
                fontSize = AppTypography.BaseFontSize.sp
            ),
            bodySmall = MaterialTheme.typography.bodySmall.copy(
                fontFamily = ubuntuFontFamily,
                fontSize = AppTypography.BaseFontSize.sp
            ),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = ubuntuFontFamily),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = ubuntuFontFamily),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = ubuntuFontFamily)
        )
    ) {
        val coroutineScope = rememberCoroutineScope()
        val state = rememberEditorState(coroutineScope)
        
        // Initialize RAG components
        val ragComponents = remember(settings.rag, state.currentDirectory) {
            RagComponentProvider.createRagComponents(
                ragSettings = settings.rag,
                notesRoot = state.currentDirectory?.toString()
            )
        }
        
        // Create chat service (with optional RAG support)
        val chatService = remember(ragComponents, settings.rag) {
            ChatServiceFactory.createChatService(
                ragComponents = ragComponents,
                ragSettings = settings.rag
            )
        }
        
        // Set up auto-indexing callback
        LaunchedEffect(ragComponents) {
            ragComponents?.indexer?.let { indexer ->
                state.onFileSaved = { filePath ->
                    coroutineScope.launch {
                        try {
                            // Get relative path from notes root
                            val notesRoot = state.currentDirectory?.toString()
                            val relativePath = if (notesRoot != null && filePath.startsWith(notesRoot)) {
                                filePath.substring(notesRoot.length + 1).replace('\\', '/')
                            } else {
                                filePath
                            }
                            indexer.indexFile(relativePath)
                        } catch (e: Exception) {
                            // Log error but don't block save
                            logger.error("Auto-indexing failed for $filePath: ${e.message}", e)
                        }
                    }
                }
            } ?: run {
                state.onFileSaved = null
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

        CompositionLocalProvider(LocalAppColors provides appColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .onKeyEvent { handleKeyboardShortcut(it, state) }
            ) {
                AppContent(
                    state = state,
                    settings = settings,
                    theme = theme,
                    chatService = chatService,
                    settingsRepository = settingsRepository
                )
                
                AppDialogs(
                    state = state,
                    settings = settings,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    ragComponents = ragComponents,
                    coroutineScope = coroutineScope,
                    logger = logger
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    state: EditorState,
    settings: Settings,
    theme: ObsidianThemeValues,
    chatService: ChatService,
    settingsRepository: SettingsRepository
) {
    val density = LocalDensity.current
    
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
                    handleFolderSelection(selectedPath, state, settingsRepository)
                }
            },
            theme = theme,
            settingsRepository = settingsRepository,
            modifier = Modifier.fillMaxHeight()
        )

        // Left Resizable Splitter
        if (state.leftSidebarVisible) {
            ResizableSplitter(
                onDrag = { deltaPx ->
                    val deltaDp = with(density) { deltaPx.toDp() }
                    val newWidth = state.leftSidebarWidth + deltaDp
                    state.updateLeftSidebarWidth(
                        newWidth,
                        minWidth = settings.ui.sidebarMinWidth.dp,
                        maxWidth = settings.ui.sidebarMaxWidth.dp
                    )
                },
                theme = theme
            )
        }

        // Center Editor Area
        val appColors = LocalAppColors.current
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(appColors.editorBackground) // Mantle for editor area
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
                        handleFolderSelection(selectedPath, state, settingsRepository)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Right Resizable Splitter
        if (state.rightSidebarVisible) {
            ResizableSplitter(
                onDrag = { deltaPx ->
                    val deltaDp = with(density) { deltaPx.toDp() }
                    val newWidth = state.rightSidebarWidth - deltaDp
                    state.updateRightSidebarWidth(
                        newWidth,
                        minWidth = settings.ui.sidebarMinWidth.dp,
                        maxWidth = settings.ui.sidebarMaxWidth.dp
                    )
                },
                theme = theme
            )
        }

        // Right Sidebar
        RightSidebar(
            state = state,
            theme = theme,
            chatService = chatService,
            modifier = Modifier.fillMaxHeight()
        )

        // Right Ribbon
        RightRibbon(
            state = state,
            modifier = Modifier.fillMaxHeight()
        )
    }
}

@Composable
private fun AppDialogs(
    state: EditorState,
    settings: Settings,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository,
    ragComponents: RagComponents?,
    coroutineScope: CoroutineScope,
    logger: Logger
) {
    // Settings Dialog
    SettingsDialog(
        state = state,
        settingsRepository = settingsRepository,
        onOpenSettingsJson = {
            state.openSettingsJson()
        },
        onReindex = {
            ragComponents?.indexer?.let { indexer ->
                coroutineScope.launch {
                    try {
                        indexer.fullReindex()
                    } catch (e: Exception) {
                        logger.error("Reindex failed: ${e.message}", e)
                    }
                }
            }
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
                    handleFolderSelection(selectedPath, state, settingsRepository)
                }
            },
            onDismiss = {
                state.dismissRecentFoldersDialog()
            },
            theme = theme
        )
    }
}

private fun handleKeyboardShortcut(
    keyEvent: KeyEvent,
    state: EditorState
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }
    
    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    val isShift = keyEvent.isShiftPressed
    
    return when {
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
}

private fun handleFolderSelection(
    selectedPath: java.nio.file.Path?,
    state: EditorState,
    settingsRepository: SettingsRepository
) {
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
