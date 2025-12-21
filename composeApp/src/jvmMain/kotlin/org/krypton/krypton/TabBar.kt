package org.krypton.krypton

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path

@Composable
fun TabBar(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs
            state.tabs.forEachIndexed { index, tab ->
                TabItem(
                    tab = tab,
                    isActive = index == state.activeTabIndex,
                    onClick = { state.switchTab(index) },
                    onClose = { state.closeTab(index) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            // New Tab Button
            IconButton(
                onClick = {
                    // Open file dialog to create new file or open existing
                    openFolderDialog { selectedPath ->
                        selectedPath?.let { path ->
                            val file = path.toFile()
                            if (file.isFile) {
                                state.openTab(path)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

    Surface(
        modifier = modifier
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(onClick = onClick),
        color = if (isActive) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = if (isActive) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // File name - must be visible
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )

            // Modified indicator
            if (isModified) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Close button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        }
    }
}

