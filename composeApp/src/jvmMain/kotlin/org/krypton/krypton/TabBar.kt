package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import krypton.composeapp.generated.resources.add
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.description
import java.nio.file.Path

@Composable
fun TabBar(
    state: EditorState,
    settings: Settings,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.editorBackground // Mantle for tab bar (matches editor area)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tabs
            state.documents.forEachIndexed { index, doc ->
                TabItem(
                    doc = doc,
                    isActive = index == state.activeTabIndex,
                    settings = settings,
                    theme = theme,
                    onClick = { state.switchTab(index) },
                    onClose = { state.closeTab(index) }
                )
            }

            // View Mode Toggle (only show when a tab is active)
            if (state.activeTabIndex >= 0 && state.activeTabIndex < state.documents.size) {
                val activeDoc = state.documents[state.activeTabIndex]
                Box(
                    modifier = Modifier
                        .height(theme.TabHeight)
                        .clip(RoundedCornerShape(theme.TabCornerRadius))
                        .background(theme.SurfaceContainer)
                        .clickable { state.toggleViewMode() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (activeDoc.viewMode) {
                            ViewMode.LivePreview -> "Live"
                            ViewMode.Compiled -> "Compiled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = settings.ui.tabLabelFontSize.sp
                    )
                }
            }

            // New Tab Button
            Box(
                modifier = Modifier
                    .size(theme.TabHeight)
                    .clip(RoundedCornerShape(theme.TabCornerRadius))
                    .background(theme.SurfaceContainer)
                    .clickable {
                        openFolderDialog { selectedPath ->
                            selectedPath?.let { path ->
                                val file = path.toFile()
                                if (file.isFile) {
                                    state.openTab(path)
                                }
                            }
                        }
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.add),
                    contentDescription = "New Tab",
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

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
    val fileName = doc.path?.fileName?.toString() ?: "Untitled"
    val isModified = doc.isDirty

    val backgroundColor = if (isActive) {
        appColors.editorBackground // Mantle for active tab (matches editor)
    } else {
        colorScheme.surfaceVariant // Surface0 for inactive tabs
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
            .clip(RoundedCornerShape(theme.TabCornerRadius))
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

            // Close button (show when active or when there are multiple tabs)
            if (isActive) {
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
}

