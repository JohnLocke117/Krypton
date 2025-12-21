package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RibbonToggleButton(
    iconOpen: DrawableResource,
    iconClose: DrawableResource,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(if (isOpen) iconClose else iconOpen),
            contentDescription = if (isOpen) "Close panel" else "Open panel",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
        )
    }
}

@Composable
internal fun RibbonIconButton(
    icon: DrawableResource,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) {
                    ObsidianTheme.Accent.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            colorFilter = if (isActive) {
                ColorFilter.tint(ObsidianTheme.Accent)
            } else {
                ColorFilter.tint(ObsidianTheme.TextSecondary)
            }
        )
    }
}

