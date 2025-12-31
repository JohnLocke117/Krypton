package org.krypton.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.krypton.chat.ChatRole
import org.krypton.core.domain.editor.ViewMode
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.RagComponents
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.krypton.ui.state.UiStatus
import org.krypton.util.Logger
import org.krypton.Settings
import org.krypton.CatppuccinMochaColors
import org.krypton.LeftSidebar
import org.krypton.RightSidebar
import org.krypton.LocalAppColors
import org.krypton.ObsidianThemeValues
import org.krypton.platform.VaultPicker
import org.krypton.platform.VaultDirectory
import org.krypton.platform.NoteEntry
import org.krypton.ui.state.AndroidNotesStateHolder
import org.koin.core.context.GlobalContext
import org.krypton.ui.MainScaffold
import org.krypton.ui.LocalDrawerState

/**
 * Mobile navigation screens
 */
enum class MobileScreen {
    NotesList,
    Editor,
    Chat,
    Settings
}

// Android actual implementations for expect functions
@OptIn(ExperimentalMaterial3Api::class)
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
    var currentScreen by remember { mutableStateOf(MobileScreen.NotesList) }
    val domainState by editorStateHolder.domainState.collectAsState()
    val activeDocument by editorStateHolder.activeDocument.collectAsState()
    
    // Navigate to editor when a file is opened
    LaunchedEffect(activeDocument) {
        if (activeDocument != null && currentScreen == MobileScreen.NotesList) {
            currentScreen = MobileScreen.Editor
        }
    }
    
    // Track overlay/dialog state for back handling
    var settingsDialogOpen by remember { mutableStateOf(false) }
    val drawerState = LocalDrawerState.current
    
    // Handle back button - close drawers/dialogs before exiting
    BackHandler(enabled = drawerState?.leftDrawerState?.isOpen == true || 
                          drawerState?.rightDrawerState?.isOpen == true || 
                          settingsDialogOpen) {
        when {
            drawerState?.rightDrawerState?.isOpen == true -> {
                drawerState.scope.launch { drawerState.rightDrawerState.close() }
            }
            drawerState?.leftDrawerState?.isOpen == true -> {
                drawerState.scope.launch { drawerState.leftDrawerState.close() }
            }
            settingsDialogOpen -> {
                settingsDialogOpen = false
                editorStateHolder.closeSettingsDialog()
            }
        }
    }
    
    MainScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Krypton") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.BackgroundElevated,
                    titleContentColor = theme.TextPrimary
                ),
                navigationIcon = {
                    // Left overlay toggle (folder icon)
                    drawerState?.let { state ->
                        IconButton(onClick = state.onLeftToggle) {
                            Icon(Icons.Default.Folder, contentDescription = "Files")
                        }
                    }
                },
                actions = {
                    // Right overlay toggle (info icon) - if available
                    drawerState?.let { state ->
                        IconButton(onClick = state.onRightToggle) {
                            Icon(Icons.Default.Description, contentDescription = "Info")
                        }
                    }
                    IconButton(onClick = { 
                        settingsDialogOpen = true
                        editorStateHolder.openSettingsDialog()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = theme.BackgroundElevated
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Notes") },
                    label = { Text("Notes") },
                    selected = currentScreen == MobileScreen.NotesList,
                    onClick = { currentScreen = MobileScreen.NotesList }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                    label = { Text("Editor") },
                    selected = currentScreen == MobileScreen.Editor,
                    onClick = { currentScreen = MobileScreen.Editor }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = currentScreen == MobileScreen.Chat,
                    onClick = { currentScreen = MobileScreen.Chat }
                )
            }
        },
        leftOverlay = {
            LeftSidebar(
                state = editorStateHolder,
                onFolderSelected = { folderPath ->
                    // Handle folder selection
                    editorStateHolder.changeDirectoryWithHistory(folderPath)
                },
                theme = theme,
                settingsRepository = settingsRepository,
                modifier = Modifier.fillMaxHeight()
            )
        },
        rightOverlay = {
            RightSidebar(
                state = editorStateHolder,
                theme = theme,
                chatStateHolder = chatStateHolder,
                settings = settings,
                modifier = Modifier.fillMaxHeight()
            )
        },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CatppuccinMochaColors.Base)
            ) {
                when (currentScreen) {
                    MobileScreen.NotesList -> {
                        AndroidNotesListScreen(
                            editorStateHolder = editorStateHolder,
                            settingsRepository = settingsRepository,
                            vaultPicker = vaultPicker,
                            theme = theme,
                            onFileSelected = {
                                coroutineScope.launch {
                                    editorStateHolder.openTab(it)
                                    currentScreen = MobileScreen.Editor
                                }
                            },
                            coroutineScope = coroutineScope
                        )
                    }
                    MobileScreen.Editor -> {
                        AndroidEditorScreen(
                            editorStateHolder = editorStateHolder,
                            settingsRepository = settingsRepository,
                            theme = theme,
                            onBack = { currentScreen = MobileScreen.NotesList }
                        )
                    }
                    MobileScreen.Chat -> {
                        AndroidChatScreen(
                            chatStateHolder = chatStateHolder,
                            editorStateHolder = editorStateHolder,
                            settings = settings,
                            theme = theme
                        )
                    }
                    MobileScreen.Settings -> {
                        AndroidSettingsScreen(
                            editorStateHolder = editorStateHolder,
                            settingsRepository = settingsRepository,
                            theme = theme,
                            onBack = { currentScreen = MobileScreen.NotesList }
                        )
                    }
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
    // Error dialog for RAG failures
    val status by editorStateHolder.status.collectAsState()
    val error = (status as? org.krypton.ui.state.UiStatus.Error)?.message
    
    if (error != null) {
        AlertDialog(
            onDismissRequest = { editorStateHolder.clearError() },
            title = { Text("Error", color = theme.TextPrimary) },
            text = { Text(error, color = theme.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { editorStateHolder.clearError() }) {
                    Text("OK", color = theme.Accent)
                }
            },
            containerColor = theme.BackgroundElevated
        )
    }
    
    // Settings dialog
    if (settingsDialogOpen) {
        AndroidSettingsDialog(
            editorStateHolder = editorStateHolder,
            settingsRepository = settingsRepository,
            ragComponents = ragComponents,
            vaultPicker = vaultPicker,
            theme = theme,
            coroutineScope = coroutineScope,
            logger = logger,
            onDismiss = { editorStateHolder.closeSettingsDialog() }
        )
    }
    
    // Recent folders dialog
    if (showRecentFoldersDialog) {
        AndroidRecentFoldersDialog(
            recentFolders = settings.app.recentFolders,
            editorStateHolder = editorStateHolder,
            vaultPicker = vaultPicker,
            theme = theme,
            coroutineScope = coroutineScope,
            onDismiss = { editorStateHolder.dismissRecentFoldersDialog() }
        )
    }
    
    // Flashcards dialog
    val flashcardsUiState by editorStateHolder.flashcardsUiState.collectAsState()
    if (flashcardsUiState.isVisible) {
        AndroidFlashcardsDialog(
            flashcardsUiState = flashcardsUiState,
            editorStateHolder = editorStateHolder,
            theme = theme
        )
    }
}

/**
 * Mobile notes list screen - simplified file explorer with SAF-based navigation
 */
@Composable
private fun AndroidNotesListScreen(
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository,
    vaultPicker: VaultPicker,
    theme: ObsidianThemeValues,
    onFileSelected: (String) -> Unit,
    coroutineScope: CoroutineScope
) {
    val koin = remember { GlobalContext.get() }
    val fileSystem = remember { koin.get<FileSystem>() }
    val context = LocalContext.current
    
    // Create Android-specific notes state holder
    val notesStateHolder = remember {
        AndroidNotesStateHolder(fileSystem, coroutineScope)
    }
    
    val directoryStack by notesStateHolder.directoryStack.collectAsState()
    val entries by notesStateHolder.entries.collectAsState()
    val isLoading by notesStateHolder.isLoading.collectAsState()
    val currentDir = notesStateHolder.currentDirectory
    val canNavigateUp = notesStateHolder.canNavigateUp
    
    // Custom ActivityResultContract for SAF folder picker with proper flags
    val folderPickerContract = object : ActivityResultContract<Uri?, Uri?>() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
                // Only set initial URI if it's a valid tree URI that we already have permission for
                input?.let { uri ->
                    // Check if we have persistable permission for this URI
                    val persistedUris = context.contentResolver.persistedUriPermissions
                    if (persistedUris.any { it.uri == uri && it.isWritePermission && it.isReadPermission }) {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    }
                }
            }
        }
        
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == android.app.Activity.RESULT_OK) {
                intent?.data
            } else {
                null
            }
        }
    }
    
    // Folder picker launcher using Storage Access Framework
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = folderPickerContract
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    // Set vault root from SAF URI (this persists the URI in settings)
                    val androidVaultPicker = vaultPicker as? org.krypton.platform.AndroidVaultPicker
                    val vaultRoot = androidVaultPicker?.setVaultRootFromUri(uri)
                    
                    if (vaultRoot != null) {
                        // Initialize notes state holder with the new vault root
                        val rootDirectory = VaultDirectory(
                            uri = vaultRoot.id,
                            displayPath = vaultRoot.displayName
                        )
                        notesStateHolder.initializeWithRoot(rootDirectory)
                    } else {
                        // Fallback to default vault on error
                        val defaultVaultRoot = vaultPicker.pickVaultRoot()
                        defaultVaultRoot?.let { root ->
                            val rootDirectory = VaultDirectory(
                                uri = root.id,
                                displayPath = root.displayName
                            )
                            notesStateHolder.initializeWithRoot(rootDirectory)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to default vault on error
                    val defaultVaultRoot = vaultPicker.pickVaultRoot()
                    defaultVaultRoot?.let { root ->
                        val rootDirectory = VaultDirectory(
                            uri = root.id,
                            displayPath = root.displayName
                        )
                        notesStateHolder.initializeWithRoot(rootDirectory)
                    }
                }
            }
        }
    }
    
    // Load vault on first launch
    LaunchedEffect(Unit) {
        if (directoryStack.isEmpty()) {
            try {
                val vaultRoot = vaultPicker.pickVaultRoot()
                if (vaultRoot != null) {
                    val rootDirectory = VaultDirectory(
                        uri = vaultRoot.id,
                        displayPath = vaultRoot.displayName
                    )
                    notesStateHolder.initializeWithRoot(rootDirectory)
                }
            } catch (e: Exception) {
                // Handle error - fallback to default Documents/Vault
                try {
                    val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                    val defaultVaultDir = java.io.File(documentsDir, "Vault")
                    if (!defaultVaultDir.exists()) {
                        defaultVaultDir.mkdirs()
                    }
                    val rootDirectory = VaultDirectory(
                        uri = defaultVaultDir.absolutePath,
                        displayPath = "Vault"
                    )
                    notesStateHolder.initializeWithRoot(rootDirectory)
                } catch (fallbackError: Exception) {
                    // Last resort: use app internal storage
                    val internalVaultDir = java.io.File(context.filesDir, "vault")
                    if (!internalVaultDir.exists()) {
                        internalVaultDir.mkdirs()
                    }
                    val rootDirectory = VaultDirectory(
                        uri = internalVaultDir.absolutePath,
                        displayPath = "Default Vault"
                    )
                    notesStateHolder.initializeWithRoot(rootDirectory)
                }
            }
        }
    }
    
    // Handle vault root changes from folder picker
    LaunchedEffect(settingsRepository.settingsFlow.value.app.vaultRootUri) {
        val vaultRootUri = settingsRepository.settingsFlow.value.app.vaultRootUri
        if (vaultRootUri != null && directoryStack.isEmpty()) {
            val rootDirectory = VaultDirectory(
                uri = vaultRootUri,
                displayPath = "Vault"
            )
            notesStateHolder.initializeWithRoot(rootDirectory)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Base)
    ) {
        // Top bar with back button and path
        VaultTopBar(
            canNavigateUp = canNavigateUp,
            currentPath = currentDir?.displayPath ?: "No vault selected",
            onNavigateUp = { notesStateHolder.navigateUp() },
            onOpenFolderPicker = {
                val currentVaultUri = try {
                    val uriString = settingsRepository.settingsFlow.value.app.vaultRootUri
                    uriString?.let { Uri.parse(it) }
                } catch (e: Exception) {
                    null
                }
                folderPickerLauncher.launch(currentVaultUri)
            },
            theme = theme
        )
        
        Divider(color = theme.Border)
        
        // Entries list (folders and files)
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = theme.Accent)
            }
        } else if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = theme.TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No files or folders",
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries) { entry ->
                    NoteEntryCard(
                        entry = entry,
                        onFolderClick = {
                            if (entry is NoteEntry.Folder) {
                                notesStateHolder.navigateIntoFolder(entry)
                            }
                        },
                        onFileClick = {
                            if (entry is NoteEntry.File) {
                                onFileSelected(entry.uri)
                            }
                        },
                        theme = theme
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultTopBar(
    canNavigateUp: Boolean,
    currentPath: String,
    onNavigateUp: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    theme: ObsidianThemeValues
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.BackgroundElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                enabled = canNavigateUp,
                onClick = { if (canNavigateUp) onNavigateUp() }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canNavigateUp) theme.TextPrimary else theme.TextSecondary
                )
            }
            
            // Current path
            Text(
                text = currentPath,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Folder picker button
            IconButton(onClick = onOpenFolderPicker) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open folder")
            }
        }
    }
}

@Composable
private fun NoteEntryCard(
    entry: NoteEntry,
    onFolderClick: () -> Unit,
    onFileClick: () -> Unit,
    theme: ObsidianThemeValues
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (entry) {
                    is NoteEntry.Folder -> onFolderClick()
                    is NoteEntry.File -> onFileClick()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = theme.Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on entry type
            Icon(
                imageVector = when (entry) {
                    is NoteEntry.Folder -> Icons.Default.Folder
                    is NoteEntry.File -> Icons.Default.Description
                },
                contentDescription = null,
                tint = when (entry) {
                    is NoteEntry.Folder -> theme.Accent
                    is NoteEntry.File -> theme.TextPrimary
                },
                modifier = Modifier.size(24.dp)
            )
            
            // Entry name
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            // Chevron for folders
            if (entry is NoteEntry.Folder) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = theme.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Mobile editor screen
 */
@Composable
private fun AndroidEditorScreen(
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository,
    theme: ObsidianThemeValues,
    onBack: () -> Unit
) {
    val activeDocument by editorStateHolder.activeDocument.collectAsState()
    val settings by settingsRepository.settingsFlow.collectAsState()
    val appColors = LocalAppColors.current
    
    if (activeDocument == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No file open",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextSecondary
                )
                Button(onClick = onBack) {
                    Text("Back to Notes")
                }
            }
        }
        return
    }
    
    // Use local variable to avoid smart cast issues - we know it's not null here
    val doc = activeDocument!!
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.editorBackground)
    ) {
        // File name bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BackgroundElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.path?.substringAfterLast("/") ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        }
        
        Divider(color = theme.Border)
        
        // Editor content
        when (doc.viewMode) {
            ViewMode.LivePreview -> {
                AndroidMarkdownEditor(
                    markdown = doc.text,
                    settings = settings,
                    theme = theme,
                    onMarkdownChange = { newText ->
                        editorStateHolder.updateTabContent(newText)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            ViewMode.Compiled -> {
                // For compiled view, show read-only rendered markdown
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Compiled view not yet implemented for Android",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Simple markdown editor for Android
 */
@Composable
private fun AndroidMarkdownEditor(
    markdown: String,
    settings: Settings,
    theme: ObsidianThemeValues,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editorContent by remember(markdown) { mutableStateOf(markdown) }
    val scrollState = rememberScrollState()
    
    val fontFamily = remember(settings.editor.fontFamily) {
        when (settings.editor.fontFamily.lowercase()) {
            "monospace", "jetbrains mono" -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    }
    
    LaunchedEffect(markdown) {
        if (editorContent != markdown) {
            editorContent = markdown
        }
    }
    
    LaunchedEffect(editorContent) {
        if (editorContent != markdown) {
            onMarkdownChange(editorContent)
        }
    }
    
    BasicTextField(
        value = editorContent,
        onValueChange = { editorContent = it },
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        textStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = settings.editor.fontSize.sp,
            color = theme.TextPrimary,
            lineHeight = (settings.editor.fontSize.sp * settings.editor.lineHeight)
        )
    )
}

/**
 * Mobile chat screen
 */
@Composable
private fun AndroidChatScreen(
    chatStateHolder: ChatStateHolder,
    editorStateHolder: EditorStateHolder?,
    settings: Settings,
    theme: ObsidianThemeValues
) {
    val messages by chatStateHolder.messages.collectAsState()
    val isLoading by chatStateHolder.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Base)
    ) {
        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.role == ChatRole.USER) {
                            theme.Accent.copy(alpha = 0.2f)
                        } else {
                            theme.Surface
                        }
                    )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = theme.Accent
                    )
                }
            }
        }
        
        Divider(color = theme.Border)
        
        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BackgroundElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...", color = theme.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.TextPrimary,
                        unfocusedTextColor = theme.TextPrimary,
                        focusedBorderColor = theme.Accent,
                        unfocusedBorderColor = theme.Border
                    ),
                    singleLine = false,
                    maxLines = 4
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            coroutineScope.launch {
                                chatStateHolder.sendMessage(inputText)
                                inputText = ""
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

/**
 * Mobile settings screen
 */
@Composable
private fun AndroidSettingsScreen(
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository,
    theme: ObsidianThemeValues,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Base)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = theme.TextPrimary
        )
        
        Button(
            onClick = { editorStateHolder.openSettingsDialog() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Settings Dialog")
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Android settings dialog (full screen)
 */
@Composable
private fun AndroidSettingsDialog(
    editorStateHolder: EditorStateHolder,
    settingsRepository: SettingsRepository,
    ragComponents: RagComponents?,
    vaultPicker: VaultPicker,
    theme: ObsidianThemeValues,
    coroutineScope: CoroutineScope,
    logger: Logger,
    onDismiss: () -> Unit
) {
    // For v1, show a simple dialog. Full settings can be implemented later
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = theme.TextPrimary) },
        text = {
            Text(
                text = "Full settings dialog coming soon. Use desktop version for full settings.",
                color = theme.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = theme.Accent)
            }
        },
        containerColor = theme.BackgroundElevated
    )
}

/**
 * Android recent folders dialog
 */
@Composable
private fun AndroidRecentFoldersDialog(
    recentFolders: List<String>,
    editorStateHolder: EditorStateHolder,
    vaultPicker: VaultPicker,
    theme: ObsidianThemeValues,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent Folders", color = theme.TextPrimary) },
        text = {
            Column {
                if (recentFolders.isEmpty()) {
                    Text("No recent folders", color = theme.TextSecondary)
                } else {
                    recentFolders.forEach { folder ->
                        TextButton(
                            onClick = {
                                editorStateHolder.changeDirectoryWithHistory(folder)
                                onDismiss()
                            }
                        ) {
                            Text(folder, color = theme.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.Accent)
            }
        },
        containerColor = theme.BackgroundElevated
    )
}

/**
 * Android flashcards dialog
 */
@Composable
private fun AndroidFlashcardsDialog(
    flashcardsUiState: EditorStateHolder.FlashcardsUiState,
    editorStateHolder: EditorStateHolder,
    theme: ObsidianThemeValues
) {
    val currentCard = flashcardsUiState.cards.getOrNull(flashcardsUiState.currentIndex)
    
    AlertDialog(
        onDismissRequest = { editorStateHolder.closeFlashcards() },
        title = { Text("Flashcards", color = theme.TextPrimary) },
        text = {
            Column {
                Text(
                    text = currentCard?.question ?: "No card",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                if (flashcardsUiState.isAnswerVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentCard?.answer ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { editorStateHolder.prevCard() }) {
                    Text("Prev", color = theme.Accent)
                }
                TextButton(onClick = { editorStateHolder.toggleAnswer() }) {
                    Text("Show Answer", color = theme.Accent)
                }
                TextButton(onClick = { editorStateHolder.nextCard() }) {
                    Text("Next", color = theme.Accent)
                }
                TextButton(onClick = { editorStateHolder.closeFlashcards() }) {
                    Text("Close", color = theme.Accent)
                }
            }
        },
        containerColor = theme.BackgroundElevated
    )
}

/**
 * Helper function to extract file path from URI.
 * Works for file:// URIs and content:// URIs for primary external storage.
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        when (uri.scheme) {
            "file" -> {
                // Direct file URI
                uri.path
            }
            "content" -> {
                // Try to extract path from document ID for primary storage
                val docId = DocumentsContract.getTreeDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    // Primary external storage
                    "/storage/emulated/0/${docId.substringAfter("primary:")}"
                } else if (docId.startsWith("home:")) {
                    // Home directory
                    "/storage/emulated/0/${docId.substringAfter("home:")}"
                } else {
                    // For other storage providers, we can't reliably get a file path
                    // Return null to fall back to default vault
                    null
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

