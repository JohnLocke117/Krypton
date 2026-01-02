package org.krypton.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.globe
import krypton.composeapp.generated.resources.search
import krypton.composeapp.generated.resources.network
import krypton.composeapp.generated.resources.database_search
import krypton.composeapp.generated.resources.database_upload
import krypton.composeapp.generated.resources.arrow_split
import krypton.composeapp.generated.resources.star
import krypton.composeapp.generated.resources.refresh
import krypton.composeapp.generated.resources.copy
import krypton.composeapp.generated.resources.rag
import krypton.composeapp.generated.resources.leaderboard

@Composable
actual fun AppIcon(
    type: AppIconType,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color?
) {
    val drawable: DrawableResource = when (type) {
        AppIconType.Language -> Res.drawable.globe
        AppIconType.Search -> Res.drawable.search
        AppIconType.Cloud -> Res.drawable.network
        AppIconType.Storage -> Res.drawable.database_upload
        AppIconType.CallSplit -> Res.drawable.arrow_split
        AppIconType.Star -> Res.drawable.star
        AppIconType.Refresh -> Res.drawable.database_search
        AppIconType.ContentCopy -> Res.drawable.copy
        AppIconType.Rag -> Res.drawable.rag
        AppIconType.Leaderboard -> Res.drawable.leaderboard
        AppIconType.DatabaseSearch -> Res.drawable.database_search
        AppIconType.DatabaseUpload -> Res.drawable.database_upload
    }
    
    Image(
        painter = painterResource(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = tint?.let { ColorFilter.tint(it) }
    )
}

