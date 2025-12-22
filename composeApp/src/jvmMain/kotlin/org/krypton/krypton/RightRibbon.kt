package org.krypton.krypton

import androidx.compose.foundation.Image
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
import krypton.composeapp.generated.resources.right_panel_close
import krypton.composeapp.generated.resources.right_panel_open
import krypton.composeapp.generated.resources.settings

@Composable
fun RightRibbon(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(ObsidianTheme.RibbonWidth)
            .background(ObsidianTheme.BackgroundElevated),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Toggle button at the top
        RibbonToggleButton(
            iconOpen = Res.drawable.right_panel_open,
            iconClose = Res.drawable.right_panel_close,
            isOpen = state.rightSidebarVisible,
            onClick = { state.toggleRightSidebar() },
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Static ribbon buttons
        RibbonIconButton(
            icon = Res.drawable.chat,
            contentDescription = "Chat",
            isActive = state.activeRightPanel == RightPanelType.Chat,
            onClick = { state.updateActiveRightPanel(RightPanelType.Chat) }
        )
        
        RibbonIconButton(
            icon = Res.drawable.settings,
            contentDescription = "Settings",
            isActive = false,
            onClick = { /* TODO: Open settings */ }
        )
    }
}

