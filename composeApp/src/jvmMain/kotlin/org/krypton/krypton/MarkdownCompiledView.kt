package org.krypton.krypton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.krypton.krypton.markdown.*

/**
 * Read-only compiled view of Markdown content.
 * Renders the Markdown as fully formatted, non-editable content.
 */
@Composable
fun MarkdownCompiledView(
    markdown: String,
    settings: Settings,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    val engine = remember { JetBrainsMarkdownEngine() }
    val blocks = remember(markdown) {
        engine.renderToBlocks(markdown)
    }
    
    val scrollState = rememberScrollState()
    val appColors = LocalAppColors.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.editorBackground) // Mantle for compiled view background
            .padding(top = theme.EditorPadding, start = theme.EditorPadding, end = theme.EditorPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        blocks.forEach { block ->
            RenderBlock(block, settings, theme)
        }
    }
}

@Composable
private fun RenderBlock(block: BlockNode, settings: Settings, theme: ObsidianThemeValues) {
    when (block) {
        is BlockNode.Heading -> {
            val typography = when (block.level) {
                1 -> MaterialTheme.typography.displayMedium
                2 -> MaterialTheme.typography.displaySmall
                3 -> MaterialTheme.typography.headlineMedium
                4 -> MaterialTheme.typography.headlineSmall
                5 -> MaterialTheme.typography.titleLarge
                6 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
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
                    .padding(vertical = 8.dp)
            )
        }
        
        is BlockNode.Paragraph -> {
            Text(
                text = buildAnnotatedStringFromInlineNodes(block.inlineNodes, settings, theme),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        is BlockNode.CodeBlock -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.CodeBlockBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = settings.editor.codeBlockFontSize.sp
                    ),
                    color = theme.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
        
        is BlockNode.Blockquote -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(
                        width = 4.dp,
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    block.blocks.forEach { childBlock ->
                        RenderBlock(childBlock, settings, theme)
                    }
                }
            }
        }
        
        is BlockNode.UnorderedList -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderBlock(childBlock, settings, theme)
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
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderBlock(childBlock, settings, theme)
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
                    .padding(vertical = 16.dp),
                color = theme.Border
            )
        }
    }
}

@Composable
private fun RenderInlineNodes(nodes: List<InlineNode>, settings: Settings, theme: ObsidianThemeValues) {
    nodes.forEach { node ->
        when (node) {
            is InlineNode.Text -> {
                Text(
                    text = node.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is InlineNode.Strong -> {
                Text(
                    text = buildAnnotatedStringFromInlineNodes(node.inlineNodes, settings, theme),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = theme.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is InlineNode.Emphasis -> {
                Text(
                    text = buildAnnotatedStringFromInlineNodes(node.inlineNodes, settings, theme),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = theme.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is InlineNode.Code -> {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    color = theme.CodeSpanBackground
                ) {
                    Text(
                        text = node.code,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = settings.editor.codeSpanFontSize.sp
                        ),
                        color = theme.TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            is InlineNode.Link -> {
                Text(
                    text = node.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = theme.LinkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is InlineNode.Image -> {
                Text(
                    text = "[Image: ${node.alt}]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildAnnotatedStringFromInlineNodes(
    nodes: List<InlineNode>,
    settings: Settings,
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
                    // Add space before code span if previous content doesn't end with space
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
                    // Add space after code span if next content doesn't start with space
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

