package org.krypton.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
actual fun AppIcon(
    type: AppIconType,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color?
) {
    val imageVector: ImageVector = when (type) {
        AppIconType.Language -> Icons.Filled.Language
        AppIconType.Search -> Icons.Filled.Search
        AppIconType.Cloud -> Icons.Filled.Cloud
        AppIconType.Storage -> Icons.Filled.Storage
        AppIconType.CallSplit -> Icons.Filled.CallSplit
        AppIconType.Star -> Icons.Filled.Star
        AppIconType.Refresh -> Icons.Filled.Refresh
        AppIconType.ContentCopy -> Icons.Filled.ContentCopy
        AppIconType.Rag -> Icons.Filled.AlternateEmail // RAG uses alternate email icon
        AppIconType.Leaderboard -> Icons.Filled.BarChart // Leaderboard icon (bar chart is closest to leaderboard)
        AppIconType.DatabaseSearch -> Icons.Filled.Storage // Database search uses storage icon
        AppIconType.DatabaseUpload -> Icons.Filled.Upload // Database upload uses upload icon
    }
    
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: Color.Unspecified
    )
}

