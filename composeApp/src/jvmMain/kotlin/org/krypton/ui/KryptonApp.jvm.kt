package org.krypton.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.RagComponents
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.krypton.util.Logger
import org.krypton.Settings
import org.krypton.CatppuccinMochaColors
import org.krypton.platform.VaultPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.krypton.*
import org.krypton.ui.MainScaffold

@Composable
actual fun AppContent(
    editorStateHolder: EditorStateHolder,
    searchStateHolder: SearchStateHolder,
    chatStateHolder: ChatStateHolder,
    settings: Settings,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository,
    vaultPicker: VaultPicker,
    leftSidebarVisible: Boolean,
    rightSidebarVisible: Boolean,
    leftSidebarWidth: Double,
    rightSidebarWidth: Double,
    activeRibbonButton: org.krypton.ui.state.RibbonButton,
    activeRightPanel: org.krypton.ui.state.RightPanelType,
    coroutineScope: CoroutineScope
) {
    val density = LocalDensity.current
    val editorDomainState by editorStateHolder.domainState.collectAsState()
    
    MainScaffold(
        topBar = {
            TopRibbon()
        },
        bottomBar = {
            BottomRibbon()
        },
        leftOverlay = {
            LeftRibbon(
                state = editorStateHolder,
                modifier = Modifier.fillMaxHeight()
            )
        },
        rightOverlay = {
            RightRibbon(
                state = editorStateHolder,
                modifier = Modifier.fillMaxHeight()
            )
        },
        content = {
            // Workspace Card - wraps the three panes
            Surface(
                modifier = Modifier
                    .fillMaxSize()
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
                            coroutineScope.launch {
                                val selectedPath = vaultPicker.pickVault()
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
                                editorStateHolder.updateLeftSidebarWidth(newWidth)
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
                                coroutineScope.launch {
                                    val selectedPath = vaultPicker.pickVault()
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
                                editorStateHolder.updateRightSidebarWidth(newWidth)
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
                        settings = settings,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    )
}

@Composable
actual fun AppDialogs(
    editorStateHolder: EditorStateHolder,
    settings: Settings,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository,
    ragComponents: RagComponents?,
    vaultPicker: VaultPicker,
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
            vaultPicker = vaultPicker,
            onReindex = {
                ragComponents?.indexer?.let { indexer ->
                    coroutineScope.launch {
                        try {
                            indexer.indexVault("")
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
                coroutineScope.launch {
                    val vaultRoot = vaultPicker.pickVaultRoot()
                    val selectedPath = vaultRoot?.id
                    handleFolderSelection(selectedPath, editorStateHolder, settingsRepository)
                }
            },
            onDismiss = {
                editorStateHolder.dismissRecentFoldersDialog()
            },
            theme = theme
        )
    }
    
    // Flashcards Dialog
    val flashcardsUiState by editorStateHolder.flashcardsUiState.collectAsState()
    FlashcardsScreen(
        state = flashcardsUiState,
        onNext = { editorStateHolder.nextCard() },
        onPrev = { editorStateHolder.prevCard() },
        onToggleAnswer = { editorStateHolder.toggleAnswer() },
        onClose = { editorStateHolder.closeFlashcards() },
        theme = theme
    )
}

private fun handleFolderSelection(
    selectedPath: String?,
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository
) {
    selectedPath?.let { path ->
        val fileSystem = org.koin.core.context.GlobalContext.get().get<org.krypton.data.files.FileSystem>()
        val isDirectory = fileSystem.isDirectory(path)
        if (isDirectory) {
            editorStateHolder.changeDirectoryWithHistory(path)
            // Set current vault ID to the selected directory (assuming it's a vault root)
            // This allows vault-specific settings to be loaded
            settingsRepository.setCurrentVaultId(path)
        } else {
            // Extract parent directory from path string
            val lastSeparator = path.lastIndexOf('/')
            val parentPath = if (lastSeparator > 0) path.substring(0, lastSeparator) else null
            if (parentPath != null) {
                editorStateHolder.changeDirectoryWithHistory(parentPath)
                // Set current vault ID to parent directory
                settingsRepository.setCurrentVaultId(parentPath)
            }
            editorStateHolder.openTab(path)
        }
    }
}

