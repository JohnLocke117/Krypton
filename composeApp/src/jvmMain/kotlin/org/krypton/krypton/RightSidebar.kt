package org.krypton.krypton

import org.krypton.krypton.ui.state.RightPanelType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.add
import krypton.composeapp.generated.resources.close
import org.krypton.krypton.markdown.BlockNode
import org.krypton.krypton.markdown.InlineNode
import org.krypton.krypton.markdown.JetBrainsMarkdownEngine
import org.krypton.krypton.chat.ChatPanel
import org.krypton.krypton.ui.state.ChatStateHolder
import org.krypton.krypton.ui.AppIconWithTooltip
import org.krypton.krypton.ui.TooltipPosition

/**
 * Recursively extract plain text from inline nodes.
 */
private fun extractTextFromInlineNodes(nodes: List<InlineNode>): String {
    return nodes.joinToString("") { node ->
        when (node) {
            is InlineNode.Text -> node.content
            is InlineNode.Strong -> extractTextFromInlineNodes(node.inlineNodes)
            is InlineNode.Emphasis -> extractTextFromInlineNodes(node.inlineNodes)
            is InlineNode.Code -> node.code
            is InlineNode.Link -> node.text
            is InlineNode.Image -> node.alt
        }
    }
}

@Composable
fun RightSidebar(
    state: org.krypton.krypton.ui.state.EditorStateHolder,
    theme: ObsidianThemeValues,
    chatStateHolder: ChatStateHolder,
    modifier: Modifier = Modifier
) {
    val rightSidebarVisible by state.rightSidebarVisible.collectAsState()
    val rightSidebarWidth by state.rightSidebarWidth.collectAsState()
    val activeRightPanel by state.activeRightPanel.collectAsState()
    
    val targetWidth = if (rightSidebarVisible) rightSidebarWidth.dp else 0.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "right_sidebar_width"
    )

    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = rightSidebarVisible,
        modifier = modifier.width(animatedWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(animatedWidth)
                .background(CatppuccinMochaColors.Crust)
        ) {
            // Fixed Top Bar
            RightSidebarTopBar(
                state = state,
                theme = theme,
                chatStateHolder = chatStateHolder,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Divider between top bar and content area
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(theme.SidebarSeparatorHeight),
                color = theme.BorderVariant
            )
            
            // Scrollable Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeRightPanel) {
                    RightPanelType.Outline -> {
                        OutlinePanel(
                            state = state,
                            theme = theme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    RightPanelType.Chat -> {
                        ChatPanel(
                            chatStateHolder = chatStateHolder,
                            editorStateHolder = state,
                            theme = theme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RightSidebarTopBar(
    state: org.krypton.krypton.ui.state.EditorStateHolder,
    theme: ObsidianThemeValues,
    chatStateHolder: ChatStateHolder,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    val activeRightPanel by state.activeRightPanel.collectAsState()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appColors.sidebarBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top bar is empty for now, will add icons later
            when (activeRightPanel) {
                RightPanelType.Outline -> {
                    // Empty for now
                }
                RightPanelType.Chat -> {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colorScheme.onSurface
                    )
                    
                    // New chat icon
                    AppIconWithTooltip(
                        tooltip = "New Chat",
                        modifier = Modifier.size(24.dp),
                        position = TooltipPosition.BELOW,
                        onClick = {
                            chatStateHolder.clearHistory()
                        }
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.add),
                            contentDescription = "New Chat",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(colorScheme.onSurface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlinePanel(
    state: org.krypton.krypton.ui.state.EditorStateHolder,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val activeDocument by state.activeDocument.collectAsState()
    val engine = remember { JetBrainsMarkdownEngine() }
    
    // Extract headings from the active document
    val currentActiveDocument = activeDocument
    val headings = remember(currentActiveDocument?.text) {
        if (currentActiveDocument?.text != null) {
            val blocks = engine.renderToBlocks(currentActiveDocument.text)
            blocks.filterIsInstance<BlockNode.Heading>()
        } else {
            emptyList()
        }
    }
    
    val appColors = LocalAppColors.current
    val colorScheme = MaterialTheme.colorScheme
    
    // Content - List of headings
    if (headings.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(theme.PanelPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No headings found",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextSecondary
            )
        }
    } else {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(theme.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            headings.forEach { heading ->
                OutlineHeadingItem(
                    heading = heading,
                    theme = theme,
                    onClick = {
                        // TODO: Scroll to heading in editor
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineHeadingItem(
    heading: BlockNode.Heading,
    theme: ObsidianThemeValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate indentation based on heading level (h1 = 0, h2 = 1, etc.)
    val indentLevel = heading.level - 1
    val indentDp = (indentLevel * 16).dp
    
    // Extract plain text from inline nodes (recursively handle nested formatting)
    val headingText = remember(heading) {
        extractTextFromInlineNodes(heading.inlineNodes)
    }
    
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(theme.SidebarItemHeight)
            .hoverable(interactionSource = interactionSource)
            .clip(
                if (isHovered) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)
            )
            .background(
                if (isHovered) appColors.hoverBackground else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(
                start = indentDp + theme.SidebarHorizontalPadding,
                end = theme.SidebarHorizontalPadding,
                top = theme.SidebarVerticalPadding,
                bottom = theme.SidebarVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headingText.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


