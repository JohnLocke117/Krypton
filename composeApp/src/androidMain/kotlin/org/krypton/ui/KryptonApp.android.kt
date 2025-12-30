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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
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
import org.krypton.LocalAppColors
import org.krypton.ObsidianThemeValues
import org.krypton.platform.VaultPicker
import org.koin.core.context.GlobalContext

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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Krypton") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.BackgroundElevated,
                    titleContentColor = theme.TextPrimary
                ),
                actions = {
                    IconButton(onClick = { currentScreen = MobileScreen.Settings }) {
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
        }
    ) { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
                .padding(paddingValues)
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
 * Mobile notes list screen - simplified file explorer
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
    val currentDirectory by editorStateHolder.currentDirectory.collectAsState()
    val files by editorStateHolder.files.collectAsState()
    val koin = remember { GlobalContext.get() }
    val fileSystem = remember { koin.get<FileSystem>() }
    val context = LocalContext.current
    
    var isLoading by remember { mutableStateOf(false) }
    
    // Folder picker launcher using Storage Access Framework
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    // Grant persistent permission
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    
                    // Try to get file path from URI
                    val path = getPathFromUri(context, uri)
                    if (path != null) {
                        editorStateHolder.changeDirectoryWithHistory(path)
                    } else {
                        // If we can't get a file path, try to use DocumentFile
                        // For now, fallback to default vault
                        val defaultPath = vaultPicker.pickVault()
                        defaultPath?.let { editorStateHolder.changeDirectoryWithHistory(it) }
                    }
                } catch (e: Exception) {
                    // Fallback to default vault on error
                    val defaultPath = vaultPicker.pickVault()
                    defaultPath?.let { editorStateHolder.changeDirectoryWithHistory(it) }
                }
            }
        }
    }
    
    // Load vault on first launch
    LaunchedEffect(Unit) {
        if (currentDirectory == null) {
            isLoading = true
            try {
                val vaultPath = vaultPicker.pickVault()
                vaultPath?.let { path ->
                    editorStateHolder.changeDirectoryWithHistory(path)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Base)
    ) {
        // Current directory path
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BackgroundElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentDirectory ?: "No vault selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    // Launch folder picker using SAF
                    folderPickerLauncher.launch(null)
                }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open folder")
                }
            }
        }
        
        Divider(color = theme.Border)
        
        // Files list
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = theme.Accent)
            }
        } else if (files.isEmpty()) {
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
                        text = "No files",
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
                items(files) { filePath ->
                    val fileName = filePath.substringAfterLast("/")
                    val isDirectory = remember(filePath) {
                        try {
                            fileSystem.isDirectory(filePath)
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isDirectory) {
                                    coroutineScope.launch {
                                        editorStateHolder.changeDirectoryWithHistory(filePath)
                                    }
                                } else {
                                    onFileSelected(filePath)
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
                            Icon(
                                if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isDirectory) theme.Accent else theme.TextSecondary
                            )
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDirectory) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = theme.TextSecondary
                                )
                            }
                        }
                    }
                }
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

