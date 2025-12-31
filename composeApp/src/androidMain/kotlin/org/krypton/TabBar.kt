@file:OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)

package org.krypton

import org.krypton.core.domain.editor.MarkdownDocument
import org.krypton.core.domain.editor.ViewMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import org.krypton.util.PathUtils

/**
 * Tab bar composable for displaying and managing open document tabs.
 * 
 * Shows all open documents as tabs with:
 * - File name display
 * - Modified indicator (dot) for unsaved files
 * - Close button on each tab
 * - View mode toggle icon (Live Preview / Compiled)
 * 
 * @param state The editor state holder managing documents and tabs
 * @param settings Current application settings (for font size)
 * @param theme Theme values for styling
 * @param modifier Modifier to apply to the tab bar
 */
@Composable
fun TabBar(
    state: org.krypton.ui.state.EditorStateHolder,
    settings: Settings,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val documents by state.documents.collectAsState()
    val activeTabIndex by state.activeTabIndex.collectAsState()
    
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    
    val activeDoc = if (activeTabIndex >= 0 && activeTabIndex < documents.size) {
        documents[activeTabIndex]
    } else {
        null
    }
    
    val currentViewMode = activeDoc?.viewMode ?: ViewMode.LivePreview
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.editorBackground // Mantle for tab bar (matches editor area)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Tabs
                    documents.forEachIndexed { index, doc ->
                        TabItem(
                            doc = doc,
                            isActive = index == activeTabIndex,
                            settings = settings,
                            theme = theme,
                            onClick = { state.switchTab(index) },
                            onClose = { state.closeTab(index) }
                        )
                    }
                }
                
                // View mode icon at the end of tabs line (only show when a tab is active)
                if (activeDoc != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(theme.TabHeight)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(theme.TabHeight)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { state.toggleViewMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(
                                    when (currentViewMode) {
                                        ViewMode.LivePreview -> Res.drawable.edit_document
                                        ViewMode.Compiled -> Res.drawable.read_only
                                    }
                                ),
                                contentDescription = when (currentViewMode) {
                                    ViewMode.LivePreview -> "Live Preview"
                                    ViewMode.Compiled -> "Compiled"
                                },
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
            
            // Divider below tabs
            Divider(
                modifier = Modifier.fillMaxWidth(),
                color = theme.BorderVariant
            )
        }
    }
}

/**
 * Individual tab item composable within the tab bar.
 * 
 * @param doc The document this tab represents
 * @param isActive Whether this tab is currently active/selected
 * @param settings Current application settings
 * @param theme Theme values for styling
 * @param onClick Callback when the tab is clicked
 * @param onClose Callback when the close button is clicked
 * @param modifier Modifier to apply to the tab item
 */
@Composable
fun TabItem(
    doc: MarkdownDocument,
    isActive: Boolean,
    settings: Settings,
    theme: ObsidianThemeValues,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    val fileName = doc.path?.let { PathUtils.getFileName(it) } ?: "Untitled"
    val isModified = doc.isDirty
    
    // Hover state for inactive tabs
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = if (isActive) {
        appColors.editorBackground // Mantle for active tab (matches editor)
    } else if (isHovered) {
        CatppuccinMochaColors.Mantle // Mantle for hovered inactive tabs (lighter than Crust)
    } else {
        CatppuccinMochaColors.Crust // Crust for inactive tabs
    }

    val textColor = if (isActive) {
        colorScheme.onSurface // Text for active tab
    } else {
        colorScheme.onSurfaceVariant // Subtext1 for inactive tabs
    }

    Box(
        modifier = modifier
            .widthIn(min = 120.dp, max = 240.dp)
            .height(theme.TabHeight)
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.TabPadding, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // File icon
            Image(
                painter = painterResource(Res.drawable.description),
                contentDescription = "File",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(
                    if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            )

            // File name
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                fontSize = settings.ui.tabFontSize.sp
            )

            // Modified indicator
            if (isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorScheme.primary)
                )
            }

            // Close button (show on all tabs)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.close),
                    contentDescription = "Close",
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

