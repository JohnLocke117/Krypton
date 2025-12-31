package org.krypton

import androidx.compose.ui.text.TextRange

/**
 * Utility functions for applying Markdown formatting to text selections.
 * These functions wrap selected text with appropriate Markdown syntax.
 */

/**
 * Format selected text as a heading.
 */
fun formatAsHeading(text: String, selection: TextRange, level: Int): Pair<String, TextRange> {
    val level = level.coerceIn(1, 6)
    val hashes = "#".repeat(level)
    
    val start = selection.min
    val end = selection.max
    
    // Check if line already starts with heading markers
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    val lineText = text.substring(lineStart, minOf(end, text.length))
    
    val newText = if (lineText.trimStart().startsWith("#")) {
        // Replace existing heading
        val existingHashes = lineText.takeWhile { it == '#' }.length
        val afterHashes = lineText.dropWhile { it == '#' }.trimStart()
        val before = text.substring(0, lineStart)
        val after = text.substring(lineStart + existingHashes + afterHashes.length.coerceAtMost(lineText.length))
        "$before$hashes $afterHashes$after"
    } else {
        // Add heading markers
        val before = text.substring(0, lineStart)
        val selected = text.substring(start, end)
        val after = text.substring(end)
        "$before$hashes $selected$after"
    }
    
    val newSelection = TextRange(start + hashes.length + 1, end + hashes.length + 1)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as bold.
 */
fun formatAsBold(text: String, selection: TextRange): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    
    // Check if already bold
    if (start >= 2 && end + 2 <= text.length) {
        val before = text.substring(start - 2, start)
        val after = text.substring(end, end + 2)
        if (before == "**" && after == "**") {
            // Remove bold
            val newText = text.substring(0, start - 2) + selected + text.substring(end + 2)
            return Pair(newText, TextRange(start - 2, end - 2))
        }
    }
    
    // Add bold markers
    val newText = text.substring(0, start) + "**$selected**" + text.substring(end)
    val newSelection = TextRange(start + 2, end + 2)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as italic.
 */
fun formatAsItalic(text: String, selection: TextRange): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    
    // Check if already italic
    if (start >= 1 && end + 1 <= text.length) {
        val before = text.substring(start - 1, start)
        val after = text.substring(end, end + 1)
        if ((before == "*" || before == "_") && (after == "*" || after == "_")) {
            // Remove italic
            val newText = text.substring(0, start - 1) + selected + text.substring(end + 1)
            return Pair(newText, TextRange(start - 1, end - 1))
        }
    }
    
    // Add italic markers
    val newText = text.substring(0, start) + "*$selected*" + text.substring(end)
    val newSelection = TextRange(start + 1, end + 1)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as inline code.
 */
fun formatAsCode(text: String, selection: TextRange): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    
    // Check if already code
    if (start >= 1 && end + 1 <= text.length) {
        val before = text.substring(start - 1, start)
        val after = text.substring(end, end + 1)
        if (before == "`" && after == "`") {
            // Remove code
            val newText = text.substring(0, start - 1) + selected + text.substring(end + 1)
            return Pair(newText, TextRange(start - 1, end - 1))
        }
    }
    
    // Add code markers
    val newText = text.substring(0, start) + "`$selected`" + text.substring(end)
    val newSelection = TextRange(start + 1, end + 1)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as a code block.
 */
fun formatAsCodeBlock(text: String, selection: TextRange): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    
    // Check if already a code block
    val isCodeBlock = text.substring(maxOf(0, start - 3), start).endsWith("```")
    
    if (isCodeBlock && end + 3 <= text.length && text.substring(end, end + 3) == "```") {
        // Remove code block
        val before = text.substring(0, start - 3)
        val after = text.substring(end + 3)
        val newText = before + selected + after
        return Pair(newText, TextRange(start - 3, end - 3))
    }
    
    // Add code block markers
    val newText = text.substring(0, start) + "```\n$selected\n```" + text.substring(end)
    val newSelection = TextRange(start + 4, end + 4)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as a blockquote.
 */
fun formatAsBlockquote(text: String, selection: TextRange): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    val lines = selected.split('\n')
    
    // Check if already blockquoted
    val isBlockquote = lines.all { it.trimStart().startsWith(">") }
    
    val newLines = if (isBlockquote) {
        // Remove blockquote markers
        lines.map { line ->
            if (line.trimStart().startsWith(">")) {
                line.replaceFirst(">", "").trimStart()
            } else {
                line
            }
        }
    } else {
        // Add blockquote markers
        lines.map { line ->
            if (line.isNotEmpty()) {
                "> $line"
            } else {
                line
            }
        }
    }
    
    val newText = text.substring(0, start) + newLines.joinToString("\n") + text.substring(end)
    val newSelection = TextRange(start, start + newLines.joinToString("\n").length)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as an unordered list.
 */
fun formatAsList(text: String, selection: TextRange, ordered: Boolean = false): Pair<String, TextRange> {
    val start = selection.min
    val end = selection.max
    
    val selected = text.substring(start, end)
    val lines = selected.split('\n')
    
    // Check if already a list
    val isList = lines.all { line ->
        line.trimStart().startsWith("- ") || line.trimStart().matches(Regex("^\\d+\\.\\s"))
    }
    
    val newLines = if (isList) {
        // Remove list markers
        lines.map { line ->
            line.replaceFirst(Regex("^[-*+]\\s|^\\d+\\.\\s"), "").trimStart()
        }
    } else {
        // Add list markers
        if (ordered) {
            lines.mapIndexed { index, line ->
                if (line.isNotEmpty()) {
                    "${index + 1}. $line"
                } else {
                    line
                }
            }
        } else {
            lines.map { line ->
                if (line.isNotEmpty()) {
                    "- $line"
                } else {
                    line
                }
            }
        }
    }
    
    val newText = text.substring(0, start) + newLines.joinToString("\n") + text.substring(end)
    val newSelection = TextRange(start, start + newLines.joinToString("\n").length)
    return Pair(newText, newSelection)
}

/**
 * Format selected text as an ordered list.
 */
fun formatAsOrderedList(text: String, selection: TextRange): Pair<String, TextRange> {
    return formatAsList(text, selection, ordered = true)
}

/**
 * Format selected text as an unordered list.
 */
fun formatAsUnorderedList(text: String, selection: TextRange): Pair<String, TextRange> {
    return formatAsList(text, selection, ordered = false)
}

