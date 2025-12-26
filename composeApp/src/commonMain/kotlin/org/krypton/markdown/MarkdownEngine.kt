package org.krypton.markdown

/**
 * Interface for Markdown parsing and rendering.
 * Abstracts the underlying Markdown library for easy swapping.
 */
interface MarkdownEngine {
    /**
     * Parse Markdown text into an Abstract Syntax Tree (AST).
     */
    fun parseToAst(markdown: String): MarkdownAst

    /**
     * Render Markdown text to HTML.
     */
    fun renderToHtml(markdown: String): String

    /**
     * Render Markdown text to a list of block nodes for structured rendering.
     */
    fun renderToBlocks(markdown: String): List<BlockNode>
}

/**
 * Represents the root of a Markdown AST.
 */
data class MarkdownAst(
    val blocks: List<BlockNode>
)

/**
 * Base class for Markdown block elements.
 */
sealed class BlockNode {
    data class Heading(
        val level: Int,
        val text: String,
        val inlineNodes: List<InlineNode>
    ) : BlockNode()

    data class Paragraph(
        val inlineNodes: List<InlineNode>
    ) : BlockNode()

    data class CodeBlock(
        val code: String,
        val language: String? = null
    ) : BlockNode()

    data class Blockquote(
        val blocks: List<BlockNode>
    ) : BlockNode()

    data class UnorderedList(
        val items: List<ListItem>
    ) : BlockNode()

    data class OrderedList(
        val items: List<ListItem>,
        val startNumber: Int = 1
    ) : BlockNode()

    object HorizontalRule : BlockNode()
}

/**
 * Represents a list item (used in both ordered and unordered lists).
 */
data class ListItem(
    val blocks: List<BlockNode>
)

/**
 * Base class for Markdown inline elements.
 */
sealed class InlineNode {
    data class Text(val content: String) : InlineNode()
    data class Strong(val inlineNodes: List<InlineNode>) : InlineNode()
    data class Emphasis(val inlineNodes: List<InlineNode>) : InlineNode()
    data class Code(val code: String) : InlineNode()
    data class Link(val text: String, val url: String) : InlineNode()
    data class Image(val alt: String, val url: String) : InlineNode()
}

