package org.krypton.krypton

import androidx.compose.foundation.Image
import org.krypton.krypton.ui.state.RibbonButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.folder
import krypton.composeapp.generated.resources.left_panel_close
import krypton.composeapp.generated.resources.left_panel_open
import krypton.composeapp.generated.resources.star

@Composable
fun LeftRibbon(
    state: org.krypton.krypton.ui.state.EditorStateHolder,
    modifier: Modifier = Modifier
) {
    val ribbonWidth = 40.dp
    val leftSidebarVisible by state.leftSidebarVisible.collectAsState()
    val activeRibbonButton by state.activeRibbonButton.collectAsState()
    
    Ribbon(
        orientation = RibbonOrientation.Vertical,
        slots = listOf(
            // Slot 0: Toggle button at the top
            {
                RibbonToggleButton(
                    iconOpen = Res.drawable.left_panel_open,
                    iconClose = Res.drawable.left_panel_close,
                    isOpen = leftSidebarVisible,
                    onClick = { state.toggleLeftSidebar() }
                )
            },
            // Slot 1: Files icon
            {
                RibbonIconButton(
                    icon = Res.drawable.folder,
                    contentDescription = "Files",
                    isActive = activeRibbonButton == RibbonButton.Files,
                    onClick = { state.updateActiveRibbonButton(RibbonButton.Files) },
                    cardFacingEdge = CardFacingEdge.End
                )
            },
            // Slot 2: Bookmarks icon
            {
                RibbonIconButton(
                    icon = Res.drawable.star,
                    contentDescription = "Bookmarks",
                    isActive = activeRibbonButton == RibbonButton.Bookmarks,
                    onClick = { state.updateActiveRibbonButton(RibbonButton.Bookmarks) },
                    cardFacingEdge = CardFacingEdge.End
                )
            }
        ),
        modifier = modifier,
        ribbonSize = ribbonWidth
    )
}

