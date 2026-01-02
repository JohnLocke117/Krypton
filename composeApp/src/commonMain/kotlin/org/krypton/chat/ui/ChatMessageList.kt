package org.krypton.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import org.krypton.ui.AppIcon
import org.krypton.ui.AppIconType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.krypton.ObsidianThemeValues
import org.krypton.chat.ChatMessage
import org.krypton.chat.ChatResponseMetadata
import org.krypton.chat.ChatRole
import org.krypton.chat.ChatSource
import org.krypton.chat.SourceType
import org.krypton.markdown.*
import org.krypton.util.copyToClipboard

/**
 * Displays the list of chat messages with scrolling.
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    theme: ObsidianThemeValues,
    settings: org.krypton.Settings,
    messageMetadata: Map<String, ChatResponseMetadata> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = theme.PanelPadding + 4.dp, vertical = theme.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        messages.forEachIndexed { index, message ->
            val metadata = messageMetadata[message.id]
            ChatMessageItem(
                message = message,
                metadata = metadata,
                theme = theme,
                settings = settings,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Add divider and spacing after assistant messages
            if (message.role == ChatRole.ASSISTANT) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.BorderVariant,
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Loading indicator
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = theme.Accent,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/**
 * Displays a single chat message item.
 */
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    metadata: ChatResponseMetadata?,
    theme: ObsidianThemeValues,
    settings: org.krypton.Settings,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    
    if (isUser) {
        // User message: displayed in a colored box, full width
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = theme.Accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary
            )
        }
    } else {
        // Assistant message: render markdown with copy button and sources
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            // Markdown content wrapped in SelectionContainer for text selection
            SelectionContainer {
                InlineMarkdownRenderer(
                    markdown = message.content,
                    settings = settings,
                    theme = theme,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Display sources if available
            metadata?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
                Spacer(modifier = Modifier.height(12.dp))
                SourcesSection(
                    sources = sources,
                    theme = theme
                )
            }
            
            // Copy All button at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CopyAllButton(
                    textToCopy = message.content,
                    theme = theme
                )
            }
        }
    }
}

/**
 * Inline markdown renderer for chat messages.
 * Simplified version that works inline without fillMaxSize().
 */
@Composable
private fun InlineMarkdownRenderer(
    markdown: String,
    settings: org.krypton.Settings,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val engine = remember { JetBrainsMarkdownEngine() }
    val blocks = remember(markdown) {
        engine.renderToBlocks(markdown)
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            RenderChatBlock(block, settings, theme)
        }
    }
}

@Composable
private fun RenderChatBlock(block: BlockNode, settings: org.krypton.Settings, theme: ObsidianThemeValues) {
    when (block) {
        is BlockNode.Heading -> {
            val typography = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                3 -> MaterialTheme.typography.titleLarge
                4 -> MaterialTheme.typography.titleMedium
                5 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.bodyLarge
            }
            
            Text(
                text = if (block.inlineNodes.isNotEmpty()) {
                    buildAnnotatedStringFromInlineNodes(block.inlineNodes, settings, theme)
                } else {
                    AnnotatedString(block.text)
                },
                style = typography.copy(
                    color = theme.HeadingColor,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        
        is BlockNode.Paragraph -> {
            Text(
                text = buildAnnotatedStringFromInlineNodes(block.inlineNodes, settings, theme),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        is BlockNode.CodeBlock -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.CodeBlockBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = settings.editor.codeBlockFontSize.sp
                    ),
                    color = theme.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
        
        is BlockNode.Blockquote -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 3.dp,
                        color = theme.BlockquoteBorder,
                        shape = RoundedCornerShape(4.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = theme.BlockquoteBackground
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.blocks.forEach { childBlock ->
                        RenderChatBlock(childBlock, settings, theme)
                    }
                }
            }
        }
        
        is BlockNode.UnorderedList -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                block.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "• ",
                            color = theme.TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderChatBlock(childBlock, settings, theme)
                            }
                        }
                    }
                }
            }
        }
        
        is BlockNode.OrderedList -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                block.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "${block.startNumber + index}. ",
                            color = theme.TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderChatBlock(childBlock, settings, theme)
                            }
                        }
                    }
                }
            }
        }
        
        is BlockNode.HorizontalRule -> {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = theme.Border
            )
        }
    }
}

private fun buildAnnotatedStringFromInlineNodes(
    nodes: List<InlineNode>,
    settings: org.krypton.Settings,
    theme: ObsidianThemeValues
): AnnotatedString {
    return buildAnnotatedString {
        var lastChar: Char? = null
        
        nodes.forEachIndexed { index, node ->
            when (node) {
                is InlineNode.Text -> {
                    append(node.content)
                    lastChar = node.content.lastOrNull()
                }
                is InlineNode.Strong -> {
                    val innerText = buildAnnotatedStringFromInlineNodes(node.inlineNodes, settings, theme).text
                    pushStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(innerText)
                    pop()
                    lastChar = innerText.lastOrNull()
                }
                is InlineNode.Emphasis -> {
                    val innerText = buildAnnotatedStringFromInlineNodes(node.inlineNodes, settings, theme).text
                    pushStyle(
                        style = SpanStyle(
                            fontStyle = FontStyle.Italic
                        )
                    )
                    append(innerText)
                    pop()
                    lastChar = innerText.lastOrNull()
                }
                is InlineNode.Code -> {
                    if (lastChar != null && lastChar != ' ') {
                        append(" ")
                    }
                    pushStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = settings.editor.codeSpanFontSize.sp,
                            background = theme.CodeSpanBackground
                        )
                    )
                    append(node.code)
                    pop()
                    val nextNode = nodes.getOrNull(index + 1)
                    val nextStartsWithSpace = when (nextNode) {
                        is InlineNode.Text -> nextNode.content.firstOrNull() == ' '
                        else -> false
                    }
                    if (!nextStartsWithSpace) {
                        append(" ")
                    }
                    lastChar = ' '
                }
                is InlineNode.Link -> {
                    pushStyle(
                        style = SpanStyle(
                            color = theme.LinkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(node.text)
                    pop()
                    lastChar = node.text.lastOrNull()
                }
                is InlineNode.Image -> {
                    append("[Image: ${node.alt}]")
                    lastChar = ']'
                }
            }
        }
    }
}

/**
 * Copy All button component.
 */
@Composable
private fun CopyAllButton(
    textToCopy: String,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = modifier
            .clickable {
                copyToClipboard(textToCopy)
            }
            .padding(4.dp)
    ) {
        AppIcon(
            type = AppIconType.ContentCopy,
            contentDescription = "Copy All",
            modifier = Modifier.size(16.dp),
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Displays sources section for a chat message.
 */
@Composable
private fun SourcesSection(
    sources: List<ChatSource>,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = theme.CodeBlockBackground.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleSmall,
                color = theme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            sources.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• ",
                        color = theme.TextSecondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.identifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextPrimary
                        )
                        if (!source.location.isNullOrEmpty()) {
                            Text(
                                text = source.location!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

