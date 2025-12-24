package org.krypton.krypton.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * MarkdownEngine implementation using JetBrains' markdown library.
 * Converts JetBrains' AST to the project's BlockNode/InlineNode structure.
 */
class JetBrainsMarkdownEngine : MarkdownEngine {
    
    private val flavour = CommonMarkFlavourDescriptor()
    private val parser = MarkdownParser(flavour)
    
    override fun parseToAst(markdown: String): MarkdownAst {
        val parsedTree = parser.buildMarkdownTreeFromString(markdown)
        val blocks = convertToBlocks(parsedTree.children, markdown)
        return MarkdownAst(blocks)
    }
    
    override fun renderToHtml(markdown: String): String {
        val parsedTree = parser.buildMarkdownTreeFromString(markdown)
        return HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
    }
    
    override fun renderToBlocks(markdown: String): List<BlockNode> {
        return parseToAst(markdown).blocks
    }
    
    private fun convertToBlocks(nodes: List<ASTNode>, markdown: String): List<BlockNode> {
        val blocks = mutableListOf<BlockNode>()
        
        for (node in nodes) {
            when (val nodeType = node.type) {
                MarkdownElementTypes.ATX_1,
                MarkdownElementTypes.ATX_2,
                MarkdownElementTypes.ATX_3,
                MarkdownElementTypes.ATX_4,
                MarkdownElementTypes.ATX_5,
                MarkdownElementTypes.ATX_6 -> {
                    val level = when (nodeType) {
                        MarkdownElementTypes.ATX_1 -> 1
                        MarkdownElementTypes.ATX_2 -> 2
                        MarkdownElementTypes.ATX_3 -> 3
                        MarkdownElementTypes.ATX_4 -> 4
                        MarkdownElementTypes.ATX_5 -> 5
                        MarkdownElementTypes.ATX_6 -> 6
                        else -> 1
                    }
                    // Extract heading text (skip the # markers)
                    val headingText = extractHeadingText(node, markdown)
                    val inlineNodes = parseInlineNodes(node.children, markdown)
                    blocks.add(BlockNode.Heading(level, headingText, inlineNodes))
                }
                
                MarkdownElementTypes.SETEXT_1,
                MarkdownElementTypes.SETEXT_2 -> {
                    val level = if (nodeType == MarkdownElementTypes.SETEXT_1) 1 else 2
                    // Setext headings have the text on the first line
                    val headingText = extractSetextHeadingText(node, markdown)
                    val inlineNodes = parseInlineNodes(node.children, markdown)
                    blocks.add(BlockNode.Heading(level, headingText, inlineNodes))
                }
                
                MarkdownElementTypes.CODE_BLOCK -> {
                    val (code, language) = extractCodeBlock(node, markdown)
                    blocks.add(BlockNode.CodeBlock(code, language))
                }
                
                MarkdownElementTypes.BLOCK_QUOTE -> {
                    val quoteBlocks = convertToBlocks(node.children, markdown)
                    blocks.add(BlockNode.Blockquote(quoteBlocks))
                }
                
                MarkdownElementTypes.UNORDERED_LIST -> {
                    val items = extractListItems(node, markdown)
                    blocks.add(BlockNode.UnorderedList(items))
                }
                
                MarkdownElementTypes.ORDERED_LIST -> {
                    val (items, startNumber) = extractOrderedListItems(node, markdown)
                    blocks.add(BlockNode.OrderedList(items, startNumber))
                }
                
                MarkdownElementTypes.PARAGRAPH -> {
                    val inlineNodes = parseInlineNodes(node.children, markdown)
                    blocks.add(BlockNode.Paragraph(inlineNodes))
                }
                
                // Horizontal rule - check by pattern since constant name may vary
                else -> {
                    if (isHorizontalRule(node, markdown)) {
                        blocks.add(BlockNode.HorizontalRule)
                    } else {
                        // For unknown types, try to process children if any
                        if (node.children.isNotEmpty()) {
                            blocks.addAll(convertToBlocks(node.children, markdown))
                        }
                    }
                }
                
            }
        }
        
        return blocks
    }
    
    private fun parseInlineNodes(nodes: List<ASTNode>, markdown: String): List<InlineNode> {
        val inlineNodes = mutableListOf<InlineNode>()
        
        for (node in nodes) {
            when (node.type) {
                MarkdownElementTypes.EMPH -> {
                    val innerNodes = parseInlineNodes(node.children, markdown)
                    inlineNodes.add(InlineNode.Emphasis(innerNodes))
                }
                MarkdownElementTypes.STRONG -> {
                    val innerNodes = parseInlineNodes(node.children, markdown)
                    inlineNodes.add(InlineNode.Strong(innerNodes))
                }
                MarkdownElementTypes.CODE_SPAN -> {
                    val code = extractCodeSpan(node, markdown)
                    inlineNodes.add(InlineNode.Code(code))
                }
                MarkdownElementTypes.INLINE_LINK -> {
                    val (text, url) = extractLink(node, markdown)
                    inlineNodes.add(InlineNode.Link(text, url))
                }
                MarkdownElementTypes.IMAGE -> {
                    val (alt, url) = extractImage(node, markdown)
                    inlineNodes.add(InlineNode.Image(alt, url))
                }
                // Text nodes - handle by checking if it's a leaf node with text content
                else -> {
                    // Check if this is a text-like node (no children, has text content)
                    if (node.children.isEmpty()) {
                        val text = markdown.substring(node.startOffset, node.endOffset)
                        // Allow whitespace-only nodes to preserve spaces after punctuation
                        // Only filter out empty strings and pure markdown syntax
                        if (text.isNotEmpty() && !isMarkdownSyntax(text)) {
                            inlineNodes.add(InlineNode.Text(text))
                        }
                    } else {
                        // Recurse into children
                        inlineNodes.addAll(parseInlineNodes(node.children, markdown))
                    }
                }
            }
        }
        
        return if (inlineNodes.isEmpty()) {
            // If no nodes were parsed, return the raw text
            val fullText = nodes.joinToString("") { 
                markdown.substring(it.startOffset, it.endOffset) 
            }
            if (fullText.isNotEmpty()) {
                listOf(InlineNode.Text(fullText))
            } else {
                emptyList()
            }
        } else {
            inlineNodes
        }
    }
    
    private fun extractHeadingText(node: ASTNode, markdown: String): String {
        // Extract text from all children, removing # markers
        val text = node.getTextInNode(markdown).toString()
        return text.replace(Regex("^#+\\s*"), "").trim()
    }
    
    private fun extractSetextHeadingText(node: ASTNode, markdown: String): String {
        // Setext headings: find the paragraph content (first line)
        for (child in node.children) {
            if (child.type == MarkdownElementTypes.PARAGRAPH) {
                return extractParagraphText(child, markdown)
            }
        }
        // Fallback
        val text = node.getTextInNode(markdown).toString()
        return text.lines().firstOrNull()?.trim() ?: ""
    }
    
    private fun extractParagraphText(node: ASTNode, markdown: String): String {
        val text = node.getTextInNode(markdown).toString()
        return text.trim()
    }
    
    private fun extractCodeBlock(node: ASTNode, markdown: String): Pair<String, String?> {
        var language: String? = null
        val codeLines = mutableListOf<String>()
        
        val fullText = node.getTextInNode(markdown).toString()
        val lines = fullText.lines()
        
        // Check if it's a fenced code block (starts with ```)
        if (lines.isNotEmpty() && lines[0].trim().startsWith("```")) {
            // Extract language from first line
            val firstLine = lines[0].trim()
            val langPart = firstLine.removePrefix("```").trim()
            if (langPart.isNotEmpty()) {
                language = langPart
            }
            
            // Extract code content (skip first and last lines which are fences)
            if (lines.size >= 2) {
                codeLines.addAll(lines.drop(1).dropLast(1))
            }
        } else {
            // Indented code block - extract all content
            codeLines.addAll(lines)
        }
        
        val code = codeLines.joinToString("\n")
        return Pair(code, language)
    }
    
    private fun extractCodeSpan(node: ASTNode, markdown: String): String {
        val text = markdown.substring(node.startOffset, node.endOffset)
        // Remove backticks
        return text.removePrefix("`").removeSuffix("`")
    }
    
    private fun extractLink(node: ASTNode, markdown: String): Pair<String, String> {
        var linkText = ""
        var url = ""
        
        for (child in node.children) {
            when (child.type) {
                MarkdownElementTypes.LINK_TEXT -> {
                    linkText = markdown.substring(child.startOffset, child.endOffset)
                        .removePrefix("[")
                        .removeSuffix("]")
                }
                MarkdownElementTypes.LINK_DESTINATION -> {
                    url = markdown.substring(child.startOffset, child.endOffset)
                        .removePrefix("(")
                        .removeSuffix(")")
                }
            }
        }
        
        return Pair(linkText, url)
    }
    
    private fun extractImage(node: ASTNode, markdown: String): Pair<String, String> {
        var alt = ""
        var url = ""
        
        for (child in node.children) {
            when (child.type) {
                MarkdownElementTypes.LINK_TEXT -> {
                    alt = markdown.substring(child.startOffset, child.endOffset)
                        .removePrefix("[")
                        .removeSuffix("]")
                }
                MarkdownElementTypes.LINK_DESTINATION -> {
                    url = markdown.substring(child.startOffset, child.endOffset)
                        .removePrefix("(")
                        .removeSuffix(")")
                }
            }
        }
        
        return Pair(alt, url)
    }
    
    private fun extractListItems(node: ASTNode, markdown: String): List<ListItem> {
        val items = mutableListOf<ListItem>()
        
        for (child in node.children) {
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val itemBlocks = convertToBlocks(child.children, markdown)
                items.add(ListItem(itemBlocks))
            }
        }
        
        return items
    }
    
    private fun extractOrderedListItems(node: ASTNode, markdown: String): Pair<List<ListItem>, Int> {
        val items = mutableListOf<ListItem>()
        var startNumber = 1
        
        for (child in node.children) {
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val itemBlocks = convertToBlocks(child.children, markdown)
                items.add(ListItem(itemBlocks))
                
                // Try to extract start number from first item
                if (items.size == 1) {
                    val itemText = markdown.substring(child.startOffset, child.endOffset)
                    val match = Regex("^(\\d+)\\.").find(itemText)
                    match?.let { 
                        startNumber = it.groupValues[1].toIntOrNull() ?: 1 
                    }
                }
            }
        }
        
        return Pair(items, startNumber)
    }
    
    private fun isHorizontalRule(node: ASTNode, markdown: String): Boolean {
        val text = markdown.substring(node.startOffset, node.endOffset).trim()
        // Horizontal rules are 3+ dashes, asterisks, or underscores on a single line
        return text.matches(Regex("^[-*_]{3,}$"))
    }
    
    private fun isMarkdownSyntax(text: String): Boolean {
        // Check if text looks like pure markdown syntax (only markers, no letters/numbers)
        val trimmed = text.trim()
        return trimmed.isEmpty() || 
               (trimmed.matches(Regex("^[#*_`\\[\\]()>-]+$")) && 
                !trimmed.any { it.isLetterOrDigit() })
    }
}

