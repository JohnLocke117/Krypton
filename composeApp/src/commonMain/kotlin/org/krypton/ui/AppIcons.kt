package org.krypton.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * Platform-agnostic icon provider for Compose Multiplatform.
 * 
 * Desktop: Uses drawable resources (SVG)
 * Android: Uses Material Icons
 */
enum class AppIconType {
    Language, Search, Cloud, Storage, CallSplit, Star, Refresh, ContentCopy,
    Rag, Leaderboard, DatabaseSearch, DatabaseUpload
}

@Composable
expect fun AppIcon(
    type: AppIconType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null
)

