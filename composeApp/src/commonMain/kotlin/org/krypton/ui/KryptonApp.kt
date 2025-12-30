package org.krypton.ui

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
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.RagComponents
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.krypton.util.Logger
import org.krypton.util.createLogger
import org.krypton.Settings
import org.krypton.AppThemeColors
import org.krypton.rememberAppColors
import org.krypton.LocalAppColors
import org.krypton.CatppuccinMochaColors
import org.krypton.buildColorSchemeFromSettings
import org.krypton.rememberObsidianTheme
import org.krypton.ObsidianThemeValues
import org.krypton.platform.VaultPicker
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform-agnostic root composable for Krypton app.
 * 
 * This is the main entry point for the UI on all platforms.
 * Platform-specific UI components are provided via expect/actual or
 * platform-specific source sets.
 */
@Composable
@Preview
fun KryptonApp() {
    // Font will be set in platform-specific implementations
    // For now, use default font family
    val ubuntuFontFamily = FontFamily.Default

    // Initialize logger
    val logger = remember { createLogger("App") }

    // Inject dependencies via Koin
    val koin = remember { GlobalContext.get() }
    val settingsRepository: SettingsRepository = remember { koin.get() }
    val editorStateHolder: EditorStateHolder = remember { koin.get() }
    val chatStateHolder: ChatStateHolder = remember { koin.get() }
    val searchStateHolder: SearchStateHolder = remember { koin.get() }
    val vaultPicker: VaultPicker = remember { koin.get() }
    val ragComponents: RagComponents? = remember { 
        try { 
            koin.getOrNull<RagComponents>() 
        } catch (e: Exception) { 
            null 
        } 
    }
    
    val settings: Settings by settingsRepository.settingsFlow.collectAsState()
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
                fontSize = org.krypton.config.UiDefaults.DEFAULT_TAB_FONT_SIZE.sp
            ),
            bodySmall = MaterialTheme.typography.bodySmall.copy(
                fontFamily = ubuntuFontFamily,
                fontSize = org.krypton.config.UiDefaults.DEFAULT_TAB_FONT_SIZE.sp
            ),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = ubuntuFontFamily),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = ubuntuFontFamily),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = ubuntuFontFamily)
        )
    ) {
        val coroutineScope = rememberCoroutineScope()
        
        // Auto-indexing is disabled - ingestion only happens when user explicitly enables RAG
        // This prevents automatic ingestion on file save
        LaunchedEffect(ragComponents) {
            editorStateHolder.onFileSaved = null
        }
        
        // Load last opened folder on startup
        LaunchedEffect(Unit) {
            val lastFolder = settings.app.recentFolders.firstOrNull()
            if (lastFolder != null) {
                try {
                    // Check if folder exists using FileSystem
                    val fileSystem = koin.get<org.krypton.data.files.FileSystem>()
                    val exists = withContext(Dispatchers.IO) {
                        fileSystem.exists(lastFolder) && fileSystem.isDirectory(lastFolder)
                    }
                    if (exists) {
                        editorStateHolder.changeDirectoryWithHistory(lastFolder)
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
                    .onKeyEvent { event ->
                        // Don't process keyboard shortcuts when settings dialog is open
                        if (!settingsDialogOpen) {
                            handleKeyboardShortcut(event, editorStateHolder)
                        } else {
                            false // Let text fields handle the events
                        }
                    }
            ) {
                AppContent(
                    editorStateHolder = editorStateHolder,
                    searchStateHolder = searchStateHolder,
                    chatStateHolder = chatStateHolder,
                    settings = settings,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    vaultPicker = vaultPicker,
                    leftSidebarVisible = leftSidebarVisible,
                    rightSidebarVisible = rightSidebarVisible,
                    leftSidebarWidth = leftSidebarWidth,
                    rightSidebarWidth = rightSidebarWidth,
                    activeRibbonButton = activeRibbonButton,
                    activeRightPanel = activeRightPanel,
                    coroutineScope = coroutineScope
                )
                
                AppDialogs(
                    editorStateHolder = editorStateHolder,
                    settings = settings,
                    theme = theme,
                    settingsRepository = settingsRepository,
                    ragComponents = ragComponents,
                    vaultPicker = vaultPicker,
                    coroutineScope = coroutineScope,
                    logger = logger,
                    showRecentFoldersDialog = showRecentFoldersDialog,
                    settingsDialogOpen = settingsDialogOpen
                )
            }
        }
    }
}

// Platform-specific UI components - these will be provided by expect/actual or platform source sets
@Composable
expect fun AppContent(
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
)

@Composable
expect fun AppDialogs(
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
)

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

