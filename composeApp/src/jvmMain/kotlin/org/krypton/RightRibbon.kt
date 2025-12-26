package org.krypton

import androidx.compose.foundation.Image
import org.krypton.ui.state.RightPanelType
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
import krypton.composeapp.generated.resources.chat
import krypton.composeapp.generated.resources.outline
import krypton.composeapp.generated.resources.right_panel_close
import krypton.composeapp.generated.resources.right_panel_open

@Composable
fun RightRibbon(
    state: org.krypton.ui.state.EditorStateHolder,
    modifier: Modifier = Modifier
) {
    val ribbonWidth = 40.dp
    val rightSidebarVisible by state.rightSidebarVisible.collectAsState()
    val activeRightPanel by state.activeRightPanel.collectAsState()
    
    Ribbon(
        orientation = RibbonOrientation.Vertical,
        slots = listOf(
            // Slot 0: Toggle button at the top
            {
                RibbonToggleButton(
                    iconOpen = Res.drawable.right_panel_open,
                    iconClose = Res.drawable.right_panel_close,
                    isOpen = rightSidebarVisible,
                    onClick = { state.toggleRightSidebar() }
                )
            },
            // Slot 1: Outline icon
            {
                RibbonIconButton(
                    icon = Res.drawable.outline,
                    contentDescription = "Outline",
                    isActive = activeRightPanel == RightPanelType.Outline,
                    onClick = { state.updateActiveRightPanel(RightPanelType.Outline) },
                    cardFacingEdge = CardFacingEdge.Start
                )
            },
            // Slot 2: Chat icon
            {
                RibbonIconButton(
                    icon = Res.drawable.chat,
                    contentDescription = "Chat",
                    isActive = activeRightPanel == RightPanelType.Chat,
                    onClick = { state.updateActiveRightPanel(RightPanelType.Chat) },
                    cardFacingEdge = CardFacingEdge.Start
                )
            }
        ),
        modifier = modifier,
        ribbonSize = ribbonWidth
    )
}

