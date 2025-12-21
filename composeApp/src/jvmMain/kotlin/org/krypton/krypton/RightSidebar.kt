package org.krypton.krypton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close

@Composable
fun RightSidebar(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    val targetWidth = if (state.rightSidebarVisible) state.rightSidebarWidth else 0.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "right_sidebar_width"
    )

    AnimatedVisibility(
        visible = state.rightSidebarVisible,
        modifier = modifier.width(animatedWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(animatedWidth)
                .background(ObsidianTheme.BackgroundElevated)
                .border(ObsidianTheme.PanelBorderWidth, ObsidianTheme.Border, RoundedCornerShape(0.dp))
        ) {
            OutlinePanel(
                onClose = { state.toggleRightSidebar() },
                modifier = Modifier.weight(1f)
            )
            
            Divider(
                color = ObsidianTheme.Border,
                thickness = ObsidianTheme.PanelBorderWidth
            )
            
            BacklinksPanel(
                onClose = { state.toggleRightSidebar() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OutlinePanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ObsidianTheme.SurfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Outline",
                    style = MaterialTheme.typography.titleSmall,
                    color = ObsidianTheme.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                    )
                }
            }
        }
        
        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ObsidianTheme.PanelPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Outline panel\n(Coming soon)",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextSecondary
            )
        }
    }
}

@Composable
private fun BacklinksPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ObsidianTheme.SurfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Backlinks",
                    style = MaterialTheme.typography.titleSmall,
                    color = ObsidianTheme.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                    )
                }
            }
        }
        
        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ObsidianTheme.PanelPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Backlinks panel\n(Coming soon)",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextSecondary
            )
        }
    }
}

