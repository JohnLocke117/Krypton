@file:OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)

package org.krypton

import org.krypton.ui.state.RibbonButton
import org.krypton.ui.AppIconWithTooltip
import org.krypton.ui.TooltipPosition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.krypton.util.AppLogger
import org.krypton.data.repository.SettingsRepository
import org.krypton.LocalAppColors
import org.krypton.CatppuccinMochaColors
import krypton.composeapp.generated.resources.Res
import org.krypton.util.PathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Left sidebar composable providing file explorer, search, and settings panels.
 * 
 * The sidebar displays different content based on the active ribbon button:
 * - **Files**: File tree explorer with folder navigation
 * - **Search**: File search within the current vault
 * - **Settings**: Settings panel (placeholder)
 * 
 * Features:
 * - Animated show/hide with width animation
 * - Resizable width (handled by parent)
 * - Top bar with action buttons (new file, new folder, refresh, collapse)
 * - Bottom bar with folder name and settings button
 * 
 * @param state The editor state holder managing sidebar state and file operations
 * @param onFolderSelected Callback when user selects a folder via picker
 * @param theme Theme values for styling
 * @param settingsRepository Optional settings repository for recent folders
 * @param modifier Modifier to apply to the sidebar
 */
@Composable
fun LeftSidebar(
    state: org.krypton.ui.state.EditorStateHolder,
    onFolderSelected: (String?) -> Unit,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository?,
    modifier: Modifier = Modifier
) {
    val leftSidebarVisible by state.leftSidebarVisible.collectAsState()
    val leftSidebarWidth by state.leftSidebarWidth.collectAsState()
    val activeRibbonButton by state.activeRibbonButton.collectAsState()
    
    val targetWidth = if (leftSidebarVisible) leftSidebarWidth.dp else 0.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "sidebar_width"
    )

    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = leftSidebarVisible,
        modifier = modifier.width(animatedWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(animatedWidth)
                .background(CatppuccinMochaColors.Crust)
        ) {
            // Fixed Top Bar
            SidebarTopBar(
                state = state,
                activeRibbonButton = activeRibbonButton,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Divider between top bar and content area
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarSeparatorHeight),
                color = theme.BorderVariant
            )
            
            // Scrollable Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeRibbonButton) {
                    RibbonButton.Files -> {
                        FilesPanel(
                            state = state,
                            onFolderSelected = onFolderSelected,
                            theme = theme,
                            settingsRepository = settingsRepository,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    RibbonButton.Search -> {
                        SearchPanel(
                            state = state,
                            theme = theme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    RibbonButton.Settings -> {
                        SettingsPanel(modifier = Modifier.fillMaxSize())
                    }
                }
            }
            
            // Divider between content area and bottom bar
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarSeparatorHeight),
                color = theme.BorderVariant
            )
            
            // Fixed Bottom Bar
            SidebarBottomBar(
                state = state,
                onFolderSelected = onFolderSelected,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FilesPanel(
    state: org.krypton.ui.state.EditorStateHolder,
    onFolderSelected: (String?) -> Unit,
    theme: ObsidianThemeValues,
    settingsRepository: SettingsRepository?,
    modifier: Modifier = Modifier
) {
    val recentFolders = if (settingsRepository != null) {
        val settings by settingsRepository.settingsFlow.collectAsState()
        settings.app.recentFolders
    } else {
        emptyList()
    }
    
    // This will contain the FileExplorer content without the card wrapper
    FileExplorerContent(
        state = state,
        onFolderSelected = onFolderSelected,
        recentFolders = recentFolders,
        onRecentFolderSelected = { path ->
            state.changeDirectoryWithHistory(path)
        },
        theme = theme,
        modifier = modifier
    )
}

/**
 * Recursively searches for files matching the query in the given directory.
 * Case-insensitive file name search.
 */
suspend fun searchFilesByName(rootPath: String, query: String): List<String> = withContext(Dispatchers.IO) {
    if (query.isEmpty()) {
        return@withContext emptyList()
    }
    
    try {
        val fileSystem = org.koin.core.context.GlobalContext.get().get<org.krypton.data.files.FileSystem>()
        if (!fileSystem.exists(rootPath) || !fileSystem.isDirectory(rootPath)) {
            return@withContext emptyList()
        }
        
        val results = mutableListOf<String>()
        fun searchRecursive(currentPath: String) {
            try {
                val files = fileSystem.listFiles(currentPath)
                for (filePath in files) {
                    if (fileSystem.isFile(filePath)) {
                        val fileName = PathUtils.getFileName(filePath)
                        if (fileName.contains(query, ignoreCase = true)) {
                            results.add(filePath)
                        }
                    } else if (fileSystem.isDirectory(filePath)) {
                        searchRecursive(filePath)
                    }
                }
            } catch (e: Exception) {
                // Skip directories we can't access
            }
        }
        searchRecursive(rootPath)
        results
    } catch (e: Exception) {
        AppLogger.e("SearchPanel", "Failed to search files: ${e.message}", e)
        emptyList()
    }
}

@Composable
private fun SearchPanel(
    state: org.krypton.ui.state.EditorStateHolder,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val currentDirectory by state.currentDirectory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val clearButtonInteractionSource = remember { MutableInteractionSource() }
    val isClearButtonHovered by clearButtonInteractionSource.collectIsHoveredAsState()
    
    // Perform search when query changes
    LaunchedEffect(searchQuery, currentDirectory) {
        if (searchQuery.isEmpty()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        
        val rootPath = currentDirectory ?: return@LaunchedEffect
        isSearching = true
        searchResults = searchFilesByName(rootPath, searchQuery)
        isSearching = false
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CatppuccinMochaColors.Crust)
    ) {
        // Search input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CatppuccinMochaColors.Crust
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(
                                color = CatppuccinMochaColors.Surface0,
                                shape = RoundedCornerShape(0.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = theme.Border,
                                shape = RoundedCornerShape(0.dp)
                            )
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.TextSecondary,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(horizontal = 12.dp)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = theme.TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.TextPrimary)
                        )
                    }
                    
                    // Clear button - always visible
                    val appColors = LocalAppColors.current
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .hoverable(interactionSource = clearButtonInteractionSource)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = searchQuery.isNotEmpty()) { searchQuery = "" }
                            .background(
                                if (isClearButtonHovered && searchQuery.isNotEmpty()) appColors.hoverBackground else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(
                                if (searchQuery.isNotEmpty()) theme.TextSecondary else theme.TextSecondary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                
                // Results summary
                if (searchQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSearching) {
                                "Searching..."
                            } else {
                                val fileCount = searchResults.size
                                "$fileCount ${if (fileCount == 1) "file" else "files"} found"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextSecondary
                        )
                    }
                }
            }
        }
        
        // Divider
        Divider(
            modifier = Modifier.fillMaxWidth(),
            color = theme.BorderVariant,
            thickness = theme.SidebarSeparatorHeight
        )
        
        // Results list or empty state
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                currentDirectory == null -> {
                    // No folder open
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ObsidianTheme.PanelPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Open a folder to search files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.TextSecondary
                        )
                    }
                }
                searchQuery.isEmpty() -> {
                    // No search query - empty area
                    Box(modifier = Modifier.fillMaxSize())
                }
                searchResults.isEmpty() && !isSearching -> {
                    // No results
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ObsidianTheme.PanelPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No files found matching \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.TextSecondary
                        )
                    }
                }
                else -> {
                    // Results list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        items(searchResults) { filePath ->
                            SearchResultItem(
                                filePath = filePath,
                                rootPath = currentDirectory!!,
                                searchQuery = searchQuery,
                                theme = theme,
                                onClick = {
                                    state.openTab(filePath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Highlights matching text in a string (case-insensitive).
 */
private fun highlightMatch(text: String, query: String, textColor: Color): AnnotatedString {
    if (query.isEmpty()) {
        return AnnotatedString(text)
    }
    
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        
        while (true) {
            val index = lowerText.indexOf(lowerQuery, lastIndex)
            if (index == -1) break
            
            // Add text before match
            if (index > lastIndex) {
                append(text.substring(lastIndex, index))
            }
            
            // Add highlighted match
            pushStyle(
                SpanStyle(
                    color = textColor,
                    background = CatppuccinMochaColors.Surface2
                )
            )
            append(text.substring(index, index + query.length))
            pop()
            
            lastIndex = index + query.length
        }
        
        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    
    return annotatedString
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    filePath: String,
    rootPath: String,
    searchQuery: String,
    theme: ObsidianThemeValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName = PathUtils.getFileName(filePath)
    val relativePath = try {
        val relPath = PathUtils.getRelativePath(rootPath, filePath)
        // If the relative path is empty or just ".", the file is in root
        // Show "./" prefix to make it clear it's a path
        if (relPath.isEmpty() || relPath == ".") {
            "./$fileName"
        } else {
            // If the path equals the filename, it means file is in root
            // Show with "./" prefix
            if (relPath == fileName) {
                "./$fileName"
            } else {
                relPath
            }
        }
    } catch (e: Exception) {
        // If getRelativePath fails, show with "./" prefix
        "./$fileName"
    }
    
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Highlight matching parts
    val highlightedFileName = highlightMatch(fileName, searchQuery, theme.TextPrimary)
    val highlightedPath = highlightMatch(relativePath, searchQuery, theme.TextSecondary)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .clip(
                if (isHovered) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)
            )
            .background(
                if (isHovered) appColors.hoverBackground else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = theme.SidebarHorizontalPadding,
                vertical = theme.SidebarVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(theme.SidebarIconTextSpacing)
        ) {
            // File icon
            Image(
                painter = painterResource(getFileIcon(filePath)),
                contentDescription = "File",
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp), // Align icon with first line of text
                colorFilter = ColorFilter.tint(theme.TextSecondary)
            )
            
            // File name and path
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = highlightedFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Always show the relative path
                Text(
                    text = highlightedPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getFileIcon(path: String): DrawableResource {
    val extension = PathUtils.getExtension(path).lowercase()
    return when (extension) {
        "md", "txt", "markdown" -> Res.drawable.description
        else -> Res.drawable.unknown_document
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.PanelPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings panel coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SidebarTopBar(
    state: org.krypton.ui.state.EditorStateHolder,
    activeRibbonButton: RibbonButton,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    val currentDirectory by state.currentDirectory.collectAsState()
    
    // Match the height of FolderNameBar which has 8dp vertical padding
    // Total height: 8dp (top) + 24dp (icon) + 8dp (bottom) = 40dp, but we'll use padding for consistency
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.sidebarBackground
    ) {
        // Only show icons when Files panel is active
        if (activeRibbonButton == RibbonButton.Files) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // New File icon button
                    SidebarIconButton(
                        icon = Res.drawable.file_copy,
                        contentDescription = "New File",
                        onClick = { 
                            state.startCreatingNewFile()
                        },
                        enabled = currentDirectory != null,
                        theme = theme,
                        modifier = Modifier
                    )
                    
                    // New Folder icon button
                    SidebarIconButton(
                        icon = Res.drawable.folder_copy,
                        contentDescription = "New Folder",
                        onClick = { 
                            state.startCreatingNewFolder()
                        },
                        enabled = currentDirectory != null,
                        theme = theme,
                        modifier = Modifier
                    )
                    
                    // Refresh icon button
                    SidebarIconButton(
                        icon = Res.drawable.refresh,
                        contentDescription = "Refresh",
                        onClick = { 
                            state.triggerTreeRefresh()
                            AppLogger.action("FileExplorer", "ManualRefresh", "")
                        },
                        enabled = currentDirectory != null,
                        theme = theme,
                        modifier = Modifier
                    )
                    
                    // Collapse all icon button
                    SidebarIconButton(
                        icon = Res.drawable.collapse_all,
                        contentDescription = "Collapse All",
                        onClick = { 
                            state.triggerCollapseAll()
                            AppLogger.action("FileExplorer", "CollapseAll", "")
                        },
                        enabled = currentDirectory != null,
                        theme = theme,
                        modifier = Modifier
                    )
                }
            }
        } else {
            // Empty top bar for other panels (Search, Settings, etc.)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}

@Composable
private fun SidebarBottomBar(
    state: org.krypton.ui.state.EditorStateHolder,
    onFolderSelected: (String?) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val currentDirectory by state.currentDirectory.collectAsState()
    
    // FolderNameBar has its own Surface with padding, so we don't need an extra Box
    // We use the same height as top bar for consistency, but FolderNameBar will determine its own height
    if (currentDirectory != null) {
        FolderNameBar(
            folderName = PathUtils.getFileName(currentDirectory!!),
            onFolderClick = { /* Will be handled by FolderMenu */ },
            onSettingsClick = { state.openSettingsDialog() },
            onFolderSelected = onFolderSelected,
            onCloseFolder = { state.closeFolder() },
            theme = theme,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        // Empty space to maintain consistent structure when no folder is open
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(theme.SidebarSectionHeight)
                .background(appColors.sidebarBackground)
        )
    }
}

@Composable
private fun SidebarIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AppIconWithTooltip(
        tooltip = contentDescription,
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp)),
        enabled = enabled,
        position = TooltipPosition.BELOW,
        onClick = onClick
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                if (enabled) {
                    colorScheme.onSurfaceVariant
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        )
    }
}

