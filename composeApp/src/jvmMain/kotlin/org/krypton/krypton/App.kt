package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.rag.RagComponents
import org.krypton.krypton.ui.state.ChatStateHolder
import org.krypton.krypton.ui.state.EditorStateHolder
import org.krypton.krypton.ui.state.SearchStateHolder
import org.krypton.krypton.util.Logger
import org.krypton.krypton.util.createLogger
import org.koin.core.context.GlobalContext
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

    // Inject dependencies via Koin
    val koin = remember { GlobalContext.get() }
    val settingsRepository: SettingsRepository = remember { koin.get() }
    val editorStateHolder: EditorStateHolder = remember { koin.get() }
    val chatStateHolder: ChatStateHolder = remember { koin.get() }
    val searchStateHolder: SearchStateHolder = remember { koin.get() }
    val ragComponents: RagComponents? = remember { 
        try { 
            koin.getOrNull<RagComponents>() 
        } catch (e: Exception) { 
            null 
        } 
    }
    
    val settings by settingsRepository.settingsFlow.collectAsState()
    val colorScheme = buildColorSchemeFromSettings(settings)
    val theme = rememberObsidianTheme(settings)
    
    // Create theme colors and app colors
    val themeColors = remember(settings) { AppThemeColors(settings) }
    val appColors = rememberAppColors(themeColors)
    
    // Observe state from state holders
    val editorDomainState by editorStateHolder.domainState.collectAsState()
    val leftSidebarVisible by editorStateHolder.leftSidebarVisible.collectAsState()
    val rightSidebarVisible by editorStateHolder.rightSidebarVisible.collectAsState()
    val leftSidebarWidth by editorStateHolder.leftSidebarWidth.collectAsState()
    val rightSidebarWidth by editorStateHolder.rightSidebarWidth.collectAsState()
    val activeRibbonButton by editorStateHolder.activeRibbonButton.collectAsState()
    val activeRightPanel by editorStateHolder.activeRightPanel.collectAsState()
    val settingsDialogOpen by editorStateHolder.settingsDialogOpen.collectAsState()
    val showRecentFoldersDialog by editorStateHolder.showRecentFoldersDialog.collectAsState()

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
        
        // Set up auto-indexing callback
        LaunchedEffect(ragComponents) {
            ragComponents?.indexer?.let { indexer ->
                editorStateHolder.onFileSaved = { filePath ->
                    coroutineScope.launch {
                        try {
                            // Get relative path from notes root
                            val notesRoot = editorDomainState.currentDirectory
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
                editorStateHolder.onFileSaved = null
            }
        }
        
        // Load last opened folder on startup
        LaunchedEffect(Unit) {
            val lastFolder = settings.app.recentFolders.firstOrNull()
            if (lastFolder != null) {
                try {
                    val path = java.nio.file.Paths.get(lastFolder)
                    if (java.nio.file.Files.exists(path) && java.nio.file.Files.isDirectory(path)) {
                        editorStateHolder.changeDirectoryWithHistory(path)
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
                    .background(CatppuccinMochaColors.Base)
                    .onKeyEvent { handleKeyboardShortcut(it, editorStateHolder) }
            ) {
                AppContent(
                    editorStateHolder = editorStateHolder,
                    searchStateHolder = searchStateHolder,
                    chatStateHolder = chatStateHolder,
                    settings = settings,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    leftSidebarVisible = leftSidebarVisible,
                    rightSidebarVisible = rightSidebarVisible,
                    leftSidebarWidth = leftSidebarWidth,
                    rightSidebarWidth = rightSidebarWidth,
                    activeRibbonButton = activeRibbonButton,
                    activeRightPanel = activeRightPanel
                )
                
                AppDialogs(
                    editorStateHolder = editorStateHolder,
                    settings = settings,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    ragComponents = ragComponents,
                    coroutineScope = coroutineScope,
                    logger = logger,
                    showRecentFoldersDialog = showRecentFoldersDialog,
                    settingsDialogOpen = settingsDialogOpen
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    editorStateHolder: EditorStateHolder,
    searchStateHolder: SearchStateHolder,
    chatStateHolder: ChatStateHolder,
    settings: Settings,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository,
    leftSidebarVisible: Boolean,
    rightSidebarVisible: Boolean,
    leftSidebarWidth: Double,
    rightSidebarWidth: Double,
    activeRibbonButton: org.krypton.krypton.ui.state.RibbonButton,
    activeRightPanel: org.krypton.krypton.ui.state.RightPanelType
) {
    val density = LocalDensity.current
    val editorDomainState by editorStateHolder.domainState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Ribbon
        TopRibbon()
        
        // Middle Row: Left Ribbon, Workspace Card, Right Ribbon
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left Ribbon
            LeftRibbon(
                state = editorStateHolder,
                modifier = Modifier.fillMaxHeight()
            )

            // Workspace Card - wraps the three panes
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, theme.BorderVariant, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Sidebar
                    LeftSidebar(
                        state = editorStateHolder,
                        onFolderSelected = {
                            openFolderDialog { selectedPath ->
                                handleFolderSelection(selectedPath, editorStateHolder, settingsRepository)
                            }
                        },
                        theme = theme,
                        settingsRepository = settingsRepository,
                        modifier = Modifier.fillMaxHeight()
                    )

                    // Left Resizable Splitter
                    if (leftSidebarVisible) {
                        var dragStartWidth by remember(leftSidebarWidth) { mutableStateOf(leftSidebarWidth) }
                        var totalDragDp by remember { mutableStateOf(0.0) }
                        
                        ResizableSplitter(
                            onDrag = { deltaPx ->
                                val deltaDp = with(density) { deltaPx.toDp().value }
                                totalDragDp += deltaDp
                                val newWidth = dragStartWidth + totalDragDp
                                editorStateHolder.updateLeftSidebarWidth(
                                    newWidth,
                                    minWidth = settings.ui.sidebarMinWidth.toDouble(),
                                    maxWidth = settings.ui.sidebarMaxWidth.toDouble()
                                )
                            },
                            theme = theme,
                            onDragStart = {
                                dragStartWidth = leftSidebarWidth
                                totalDragDp = 0.0
                            },
                            onDragEnd = {
                                totalDragDp = 0.0
                            }
                        )
                    }

                    // Center Editor Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(CatppuccinMochaColors.Base)
                    ) {
                        TabBar(
                            state = editorStateHolder,
                            settings = settings,
                            theme = theme,
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextEditor(
                            state = editorStateHolder,
                            settingsRepository = settingsRepository,
                            onOpenFolder = {
                                openFolderDialog { selectedPath ->
                                    handleFolderSelection(selectedPath, editorStateHolder, settingsRepository)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Right Resizable Splitter
                    if (rightSidebarVisible) {
                        var dragStartWidth by remember(rightSidebarWidth) { mutableStateOf(rightSidebarWidth) }
                        var totalDragDp by remember { mutableStateOf(0.0) }
                        
                        ResizableSplitter(
                            onDrag = { deltaPx ->
                                val deltaDp = with(density) { deltaPx.toDp().value }
                                totalDragDp += deltaDp
                                // For right panel, dragging left (negative delta) increases width
                                val newWidth = dragStartWidth - totalDragDp
                                editorStateHolder.updateRightSidebarWidth(
                                    newWidth,
                                    minWidth = settings.ui.sidebarMinWidth.toDouble(),
                                    maxWidth = settings.ui.sidebarMaxWidth.toDouble()
                                )
                            },
                            theme = theme,
                            onDragStart = {
                                dragStartWidth = rightSidebarWidth
                                totalDragDp = 0.0
                            },
                            onDragEnd = {
                                totalDragDp = 0.0
                            }
                        )
                    }

                    // Right Sidebar
                    RightSidebar(
                        state = editorStateHolder,
                        theme = theme,
                        chatStateHolder = chatStateHolder,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
            
            // Right Ribbon
            RightRibbon(
                state = editorStateHolder,
                modifier = Modifier.fillMaxHeight()
            )
        }
        
        // Bottom Ribbon
        BottomRibbon()
    }
}

@Composable
private fun AppDialogs(
    editorStateHolder: EditorStateHolder,
    settings: Settings,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository,
    ragComponents: RagComponents?,
    coroutineScope: CoroutineScope,
    logger: Logger,
    showRecentFoldersDialog: Boolean,
    settingsDialogOpen: Boolean
) {
    // Settings Dialog
    if (settingsDialogOpen) {
        SettingsDialog(
            state = editorStateHolder,
            settingsRepository = settingsRepository,
            onOpenSettingsJson = {
                editorStateHolder.openSettingsJson()
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
            },
            onDismiss = {
                editorStateHolder.closeSettingsDialog()
            }
        )
    }
    
    // Recent Folders Dialog
    if (showRecentFoldersDialog) {
        RecentFoldersDialog(
            recentFolders = settings.app.recentFolders,
            onFolderSelected = { path ->
                editorStateHolder.changeDirectoryWithHistory(path)
            },
            onOpenNewFolder = {
                openFolderDialog { selectedPath ->
                    handleFolderSelection(selectedPath, editorStateHolder, settingsRepository)
                }
            },
            onDismiss = {
                editorStateHolder.dismissRecentFoldersDialog()
            },
            theme = theme
        )
    }
}

private fun handleKeyboardShortcut(
    keyEvent: KeyEvent,
    editorStateHolder: EditorStateHolder
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }
    
    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    val isShift = keyEvent.isShiftPressed
    
    return when {
        // Settings
        isCtrlOrCmd && keyEvent.key == Key.Comma -> {
            editorStateHolder.openSettingsDialog()
            true
        }
        // Search
        isCtrlOrCmd && keyEvent.key == Key.F -> {
            editorStateHolder.openSearchDialog(showReplace = false)
            true
        }
        // Search & Replace
        isCtrlOrCmd && keyEvent.key == Key.H -> {
            editorStateHolder.openSearchDialog(showReplace = true)
            true
        }
        // Find next
        keyEvent.key == Key.F3 && !isShift -> {
            editorStateHolder.findNext()
            true
        }
        // Find previous
        keyEvent.key == Key.F3 && isShift -> {
            editorStateHolder.findPrevious()
            true
        }
        // Close search
        keyEvent.key == Key.Escape -> {
            editorStateHolder.closeSearchDialog()
            true
        }
        // Undo
        isCtrlOrCmd && keyEvent.key == Key.Z && !isShift -> {
            editorStateHolder.undo()
            true
        }
        // Redo
        (isCtrlOrCmd && keyEvent.key == Key.Z && isShift) ||
        (isCtrlOrCmd && keyEvent.key == Key.Y) -> {
            editorStateHolder.redo()
            true
        }
        else -> false
    }
}

private fun handleFolderSelection(
    selectedPath: java.nio.file.Path?,
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository
) {
    selectedPath?.let { path ->
        val file = path.toFile()
        if (file.isDirectory) {
            editorStateHolder.changeDirectoryWithHistory(path)
        } else {
            val parentPath = file.parentFile?.toPath()
            if (parentPath != null) {
                editorStateHolder.changeDirectoryWithHistory(parentPath)
            }
            editorStateHolder.openTab(path)
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
