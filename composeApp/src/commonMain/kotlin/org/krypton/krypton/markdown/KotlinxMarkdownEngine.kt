package org.krypton.krypton.markdown

/**
 * Simple MarkdownEngine implementation using regex-based parsing.
 * This is a basic implementation that can be enhanced later with a full Markdown library.
 */
class KotlinxMarkdownEngine : MarkdownEngine {
    
    override fun parseToAst(markdown: String): MarkdownAst {
        val blocks = parseMarkdownToBlocks(markdown)
        return MarkdownAst(blocks)
    }

    override fun renderToHtml(markdown: String): String {
        val ast = parseToAst(markdown)
        return buildHtmlFromAst(ast)
    }

    override fun renderToBlocks(markdown: String): List<BlockNode> {
        return parseToAst(markdown).blocks
    }

    private fun parseMarkdownToBlocks(markdown: String): List<BlockNode> {
        val blocks = mutableListOf<BlockNode>()
        val lines = markdown.lines()
        var i = 0
        
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                // Headings
                line.matches(Regex("^#{1,6}\\s+.*")) -> {
                    val level = line.takeWhile { it == '#' }.length
                    val text = line.substringAfter('#').trim()
                    val inlineNodes = parseInlineMarkdown(text)
                    blocks.add(BlockNode.Heading(level, text, inlineNodes))
                    i++
                }
                // Code blocks
                line.trim().startsWith("```") -> {
                    val language = line.trim().substringAfter("```").trim().takeIf { it.isNotEmpty() }
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    blocks.add(BlockNode.CodeBlock(codeLines.joinToString("\n"), language))
                    i++
                }
                // Blockquotes
                line.trim().startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines.add(lines[i].trim().substringAfter(">").trim())
                        i++
                    }
                    val quoteText = quoteLines.joinToString("\n")
                    val quoteBlocks = parseMarkdownToBlocks(quoteText)
                    blocks.add(BlockNode.Blockquote(quoteBlocks))
                }
                // Unordered lists
                line.matches(Regex("^[-*+]\\s+.*")) -> {
                    val items = mutableListOf<ListItem>()
                    while (i < lines.size && lines[i].matches(Regex("^[-*+]\\s+.*"))) {
                        val itemText = lines[i].substringAfter(" ").substringAfter(" ")
                        val itemBlocks = parseMarkdownToBlocks(itemText)
                        items.add(ListItem(itemBlocks))
                        i++
                    }
                    blocks.add(BlockNode.UnorderedList(items))
                }
                // Ordered lists
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val items = mutableListOf<ListItem>()
                    val startNumber = line.substringBefore(".").toIntOrNull() ?: 1
                    while (i < lines.size && lines[i].matches(Regex("^\\d+\\.\\s+.*"))) {
                        val itemText = lines[i].substringAfter(".").trim()
                        val itemBlocks = parseMarkdownToBlocks(itemText)
                        items.add(ListItem(itemBlocks))
                        i++
                    }
                    blocks.add(BlockNode.OrderedList(items, startNumber))
                }
                // Horizontal rule
                line.matches(Regex("^[-*_]{3,}$")) -> {
                    blocks.add(BlockNode.HorizontalRule)
                    i++
                }
                // Paragraphs
                line.isNotBlank() -> {
                    val paragraphLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank() && 
                           !lines[i].matches(Regex("^#{1,6}\\s+.*")) &&
                           !lines[i].trim().startsWith("```") &&
                           !lines[i].trim().startsWith(">") &&
                           !lines[i].matches(Regex("^[-*+]\\s+.*")) &&
                           !lines[i].matches(Regex("^\\d+\\.\\s+.*")) &&
                           !lines[i].matches(Regex("^[-*_]{3,}$"))) {
                        paragraphLines.add(lines[i])
                        i++
                    }
                    val paragraphText = paragraphLines.joinToString("\n")
                    val inlineNodes = parseInlineMarkdown(paragraphText)
                    blocks.add(BlockNode.Paragraph(inlineNodes))
                }
                else -> {
                    i++
                }
            }
        }
        
        return blocks
    }

    private fun parseInlineMarkdown(text: String): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        var i = 0
        
        while (i < text.length) {
            // Check for bold **text**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = findClosingMarker(text, i + 2, "**")
                if (end > 0) {
                    val content = text.substring(i + 2, end)
                    val innerNodes = parseInlineMarkdown(content)
                    nodes.add(InlineNode.Strong(innerNodes))
                    i = end + 2
                    continue
                }
            }
            
            // Check for italic *text* or _text_
            if (text[i] == '*' || text[i] == '_') {
                val marker = text[i].toString()
                val end = findClosingMarker(text, i + 1, marker, allowSameChar = true)
                if (end > 0 && end > i + 1 && !isBoldMarker(text, i)) {
                    val content = text.substring(i + 1, end)
                    val innerNodes = parseInlineMarkdown(content)
                    nodes.add(InlineNode.Emphasis(innerNodes))
                    i = end + 1
                    continue
                }
            }
            
            // Check for code span `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    val code = text.substring(i + 1, end)
                    nodes.add(InlineNode.Code(code))
                    i = end + 1
                    continue
                }
            }
            
            // Check for links [text](url)
            if (text[i] == '[') {
                val linkEnd = text.indexOf(']', i + 1)
                if (linkEnd > 0 && linkEnd + 1 < text.length && text[linkEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', linkEnd + 2)
                    if (urlEnd > 0) {
                        val linkText = text.substring(i + 1, linkEnd)
                        val url = text.substring(linkEnd + 2, urlEnd)
                        nodes.add(InlineNode.Link(linkText, url))
                        i = urlEnd + 1
                        continue
                    }
                }
            }
            
            // Regular text
            val textStart = i
            while (i < text.length && 
                   text[i] != '*' && 
                   text[i] != '_' && 
                   text[i] != '`' && 
                   text[i] != '[') {
                i++
            }
            if (i > textStart) {
                val content = text.substring(textStart, i)
                if (content.isNotEmpty()) {
                    nodes.add(InlineNode.Text(content))
                }
            }
        }
        
        return if (nodes.isEmpty()) listOf(InlineNode.Text(text)) else nodes
    }

    private fun findClosingMarker(
        text: String,
        start: Int,
        marker: String,
        allowSameChar: Boolean = false
    ): Int {
        if (marker.length == 1) {
            for (i in start until text.length) {
                if (text[i] == marker[0]) {
                    if (!allowSameChar && i + 1 < text.length && text[i + 1] == marker[0]) {
                        continue
                    }
                    return i
                }
            }
        } else {
            for (i in start until text.length - marker.length + 1) {
                if (text.substring(i, i + marker.length) == marker) {
                    return i
                }
            }
        }
        return -1
    }

    private fun isBoldMarker(text: String, pos: Int): Boolean {
        return pos + 1 < text.length && text[pos] == '*' && text[pos + 1] == '*'
    }

    private fun buildHtmlFromAst(ast: MarkdownAst): String {
        val html = StringBuilder()
        ast.blocks.forEach { block ->
            when (block) {
                is BlockNode.Heading -> {
                    html.append("<h${block.level}>")
                    html.append(renderInlineNodes(block.inlineNodes))
                    html.append("</h${block.level}>\n")
                }
                is BlockNode.Paragraph -> {
                    html.append("<p>")
                    html.append(renderInlineNodes(block.inlineNodes))
                    html.append("</p>\n")
                }
                is BlockNode.CodeBlock -> {
                    html.append("<pre><code")
                    if (block.language != null) {
                        html.append(" class=\"language-${block.language}\"")
                    }
                    html.append(">")
                    html.append(escapeHtml(block.code))
                    html.append("</code></pre>\n")
                }
                is BlockNode.Blockquote -> {
                    html.append("<blockquote>\n")
                    block.blocks.forEach { b ->
                        html.append(buildHtmlFromAst(MarkdownAst(listOf(b))))
                    }
                    html.append("</blockquote>\n")
                }
                is BlockNode.UnorderedList -> {
                    html.append("<ul>\n")
                    block.items.forEach { item ->
                        html.append("<li>")
                        item.blocks.forEach { b ->
                            html.append(buildHtmlFromAst(MarkdownAst(listOf(b))))
                        }
                        html.append("</li>\n")
                    }
                    html.append("</ul>\n")
                }
                is BlockNode.OrderedList -> {
                    html.append("<ol start=\"${block.startNumber}\">\n")
                    block.items.forEach { item ->
                        html.append("<li>")
                        item.blocks.forEach { b ->
                            html.append(buildHtmlFromAst(MarkdownAst(listOf(b))))
                        }
                        html.append("</li>\n")
                    }
                    html.append("</ol>\n")
                }
                is BlockNode.HorizontalRule -> {
                    html.append("<hr>\n")
                }
            }
        }
        return html.toString()
    }

    private fun renderInlineNodes(nodes: List<InlineNode>): String {
        return nodes.joinToString("") { node ->
            when (node) {
                is InlineNode.Text -> escapeHtml(node.content)
                is InlineNode.Strong -> "<strong>${renderInlineNodes(node.inlineNodes)}</strong>"
                is InlineNode.Emphasis -> "<em>${renderInlineNodes(node.inlineNodes)}</em>"
                is InlineNode.Code -> "<code>${escapeHtml(node.code)}</code>"
                is InlineNode.Link -> "<a href=\"${escapeHtml(node.url)}\">${escapeHtml(node.text)}</a>"
                is InlineNode.Image -> "<img alt=\"${escapeHtml(node.alt)}\" src=\"${escapeHtml(node.url)}\">"
            }
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
