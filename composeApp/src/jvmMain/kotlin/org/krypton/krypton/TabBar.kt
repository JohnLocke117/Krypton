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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianTheme.BackgroundElevated
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
            state.tabs.forEachIndexed { index, tab ->
                TabItem(
                    tab = tab,
                    isActive = index == state.activeTabIndex,
                    onClick = { state.switchTab(index) },
                    onClose = { state.closeTab(index) }
                )
            }

            // New Tab Button
            Box(
                modifier = Modifier
                    .size(ObsidianTheme.TabHeight)
                    .clip(RoundedCornerShape(ObsidianTheme.TabCornerRadius))
                    .background(ObsidianTheme.SurfaceContainer)
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
                    colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                )
            }
        }
    }
}

@Composable
fun TabItem(
    tab: Tab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName = tab.path.fileName.toString()
    val isModified = tab.isModified

    val backgroundColor = if (isActive) {
        ObsidianTheme.Background
    } else {
        ObsidianTheme.BackgroundElevated
    }

    val textColor = if (isActive) {
        ObsidianTheme.TextPrimary
    } else {
        ObsidianTheme.TextSecondary
    }

    Box(
        modifier = modifier
            .widthIn(min = 120.dp, max = 240.dp)
            .height(ObsidianTheme.TabHeight)
            .clip(RoundedCornerShape(ObsidianTheme.TabCornerRadius))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = ObsidianTheme.TabPadding, vertical = 8.dp)
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
                    if (isActive) ObsidianTheme.Accent else ObsidianTheme.TextTertiary
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
                fontSize = 13.sp
            )

            // Modified indicator
            if (isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ObsidianTheme.Accent)
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
                        colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                    )
                }
            }
        }
    }
}

