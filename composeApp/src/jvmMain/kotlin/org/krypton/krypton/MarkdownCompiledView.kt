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
    modifier: Modifier = Modifier
) {
    val engine = remember { KotlinxMarkdownEngine() }
    val blocks = remember(markdown) {
        engine.renderToBlocks(markdown)
    }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ObsidianTheme.EditorPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        blocks.forEach { block ->
            RenderBlock(block)
        }
    }
}

@Composable
private fun RenderBlock(block: BlockNode) {
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
                text = block.text,
                style = typography.copy(
                    color = ObsidianTheme.HeadingColor,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        is BlockNode.Paragraph -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                RenderInlineNodes(block.inlineNodes)
            }
        }
        
        is BlockNode.CodeBlock -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ObsidianTheme.CodeBlockBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    color = ObsidianTheme.TextPrimary,
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
                        color = ObsidianTheme.BlockquoteBorder,
                        shape = RoundedCornerShape(4.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = ObsidianTheme.BlockquoteBackground
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
                        RenderBlock(childBlock)
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
                            color = ObsidianTheme.TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderBlock(childBlock)
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
                            color = ObsidianTheme.TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.blocks.forEach { childBlock ->
                                RenderBlock(childBlock)
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
                color = ObsidianTheme.Border
            )
        }
    }
}

@Composable
private fun RenderInlineNodes(nodes: List<InlineNode>) {
    nodes.forEach { node ->
        when (node) {
            is InlineNode.Text -> {
                Text(
                    text = node.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
            }
            is InlineNode.Strong -> {
                Text(
                    text = node.inlineNodes.joinToString("") { 
                        when (it) {
                            is InlineNode.Text -> it.content
                            else -> ""
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = ObsidianTheme.TextPrimary
                )
            }
            is InlineNode.Emphasis -> {
                Text(
                    text = node.inlineNodes.joinToString("") {
                        when (it) {
                            is InlineNode.Text -> it.content
                            else -> ""
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = ObsidianTheme.TextPrimary
                )
            }
            is InlineNode.Code -> {
                Surface(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = ObsidianTheme.CodeSpanBackground
                ) {
                    Text(
                        text = node.code,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        color = ObsidianTheme.TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            is InlineNode.Link -> {
                Text(
                    text = node.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = ObsidianTheme.LinkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
            }
            is InlineNode.Image -> {
                Text(
                    text = "[Image: ${node.alt}]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ObsidianTheme.TextSecondary
                )
            }
        }
    }
}

