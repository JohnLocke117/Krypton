package org.krypton.rag

/**
 * Interface for chunking markdown content into smaller pieces.
 * 
 * This interface is platform-independent and suitable for use across different platforms.
 */
interface MarkdownChunker {
    /**
     * Chunks markdown content into RagChunk objects.
     * 
     * @param filePath The path to the source file
     * @param content The markdown file content
     * @return List of RagChunk objects with metadata
     */
    fun chunk(
        filePath: String,
        content: String
    ): List<RagChunk>
}

/**
 * Default implementation of MarkdownChunker.
 * 
 * Strategy:
 * 1. Split by top-level headings (h1, h2)
 * 2. For each section, use token-aware splitting targeting 350-512 tokens
 * 3. Preserve code blocks and lists (don't split mid-block/list)
 * 4. Extract section titles from heading hierarchy
 * 
 * This implementation will be moved to jvmMain in a later refactoring step.
 */
class MarkdownChunkerImpl(
    private val targetTokens: Int = org.krypton.config.RagDefaults.Chunking.DEFAULT_CHUNK_TARGET_TOKENS,
    private val minTokens: Int = org.krypton.config.RagDefaults.Chunking.DEFAULT_CHUNK_MIN_TOKENS,
    private val maxTokens: Int = org.krypton.config.RagDefaults.Chunking.DEFAULT_CHUNK_MAX_TOKENS,
    private val overlapTokens: Int = org.krypton.config.RagDefaults.Chunking.DEFAULT_CHUNK_OVERLAP_TOKENS,
    private val charsPerToken: Int = org.krypton.config.RagDefaults.Chunking.DEFAULT_CHARS_PER_TOKEN
) : MarkdownChunker {
    
    override fun chunk(filePath: String, content: String): List<RagChunk> {
        // Convert NoteChunk results to RagChunk
        return chunkToNoteChunks(content, filePath).map { it.toRagChunk() }
    }
    
    /**
     * Chunks markdown content into NoteChunk objects (internal method for backward compatibility).
     * 
     * @param content The markdown file content
     * @param filePath The path to the source file
     * @return List of NoteChunk objects with line numbers, text, and section titles
     */
    private fun chunkToNoteChunks(content: String, filePath: String): List<NoteChunk> {
        if (content.isBlank()) {
            return emptyList()
        }
        
        val lines = content.lines()
        val chunks = mutableListOf<NoteChunk>()
        
        // First, identify top-level headings (h1 or h2) and track heading hierarchy
        val headingIndices = mutableListOf<Int>()
        val headingTexts = mutableListOf<String>()
        val headingLevels = mutableListOf<Int>()
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            when {
                line.startsWith("# ") -> {
                    headingIndices.add(i)
                    headingTexts.add(line.removePrefix("# ").trim())
                    headingLevels.add(1)
                }
                line.startsWith("## ") -> {
                    headingIndices.add(i)
                    headingTexts.add(line.removePrefix("## ").trim())
                    headingLevels.add(2)
                }
            }
        }
        
        // Add end of file as a boundary
        headingIndices.add(lines.size)
        headingTexts.add("")
        headingLevels.add(0)
        
        // Process each section between headings
        for (sectionIndex in headingIndices.indices) {
            val startLine = if (sectionIndex == 0) 0 else headingIndices[sectionIndex - 1]
            val endLine = headingIndices[sectionIndex]
            
            if (startLine >= endLine) continue
            
            val sectionLines = lines.subList(startLine, endLine)
            val sectionText = sectionLines.joinToString("\n")
            
            // Extract section title from heading hierarchy
            val sectionTitle = extractSectionTitle(
                headingTexts = headingTexts,
                headingLevels = headingLevels,
                sectionIndex = sectionIndex,
                filePath = filePath
            )
            
            // Estimate tokens for the section
            val sectionTokens = estimateTokens(sectionText)
            
            // If section is short, keep as single chunk
            if (sectionTokens < minTokens) {
                if (sectionText.isNotBlank()) {
                    chunks.add(
                        NoteChunk(
                            id = generateChunkId(filePath, startLine + 1, endLine),
                            filePath = filePath,
                            startLine = startLine + 1,
                            endLine = endLine,
                            text = sectionText.trim(),
                            sectionTitle = sectionTitle
                        )
                    )
                }
            } else {
                // Split long sections using token-aware logic
                val sectionChunks = splitByTokens(
                    text = sectionText,
                    startLineNumber = startLine + 1,
                    filePath = filePath,
                    sectionTitle = sectionTitle,
                    lines = lines,
                    sectionStartLine = startLine
                )
                chunks.addAll(sectionChunks)
            }
        }
        
        return chunks
    }
    
    /**
     * Estimates token count from text using character-based approximation.
     */
    private fun estimateTokens(text: String): Int {
        return text.length / charsPerToken
    }
    
    /**
     * Splits text into chunks using token-aware logic with sentence/paragraph boundaries.
     * Preserves code blocks and lists.
     */
    private fun splitByTokens(
        text: String,
        startLineNumber: Int,
        filePath: String,
        sectionTitle: String?,
        lines: List<String>,
        sectionStartLine: Int
    ): List<NoteChunk> {
        val chunks = mutableListOf<NoteChunk>()
        
        // Split by paragraphs first (double newlines)
        val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
        
        if (paragraphs.isEmpty()) {
            return emptyList()
        }
        
        var currentChunk = StringBuilder()
        var currentLineStart = startLineNumber
        var currentTokens = 0
        var paragraphIndex = 0
        
        for (paragraph in paragraphs) {
            val paragraphTokens = estimateTokens(paragraph)
            
            // Check if paragraph is a code block or list (preserve as single unit)
            val isCodeBlock = paragraph.trimStart().startsWith("```")
            val isList = paragraph.trimStart().startsWith("- ") || 
                        paragraph.trimStart().startsWith("* ") ||
                        paragraph.trimStart().matches(Regex("^\\d+\\.\\s"))
            
            // If adding this paragraph would exceed max tokens, finalize current chunk
            if (currentTokens > 0 && currentTokens + paragraphTokens > maxTokens) {
                if (currentChunk.isNotEmpty()) {
                    val chunkText = currentChunk.toString().trim()
                    if (chunkText.isNotBlank()) {
                        val endLine = calculateEndLine(
                            chunkText = chunkText,
                            startLine = currentLineStart,
                            lines = lines,
                            sectionStartLine = sectionStartLine
                        )
                        chunks.add(
                            NoteChunk(
                                id = generateChunkId(filePath, currentLineStart, endLine),
                                filePath = filePath,
                                startLine = currentLineStart,
                                endLine = endLine,
                                text = chunkText,
                                sectionTitle = sectionTitle
                            )
                        )
                    }
                }
                // Start new chunk with overlap if possible
                if (overlapTokens > 0 && chunks.isNotEmpty()) {
                    // Try to include last few sentences from previous chunk for overlap
                    val lastChunkText = chunks.last().text
                    val sentences = findSentenceBoundaries(lastChunkText)
                    if (sentences.size > 1) {
                        // Take last 1-2 sentences for overlap
                        val overlapStart = sentences.takeLast(2).firstOrNull() ?: 0
                        val overlapText = lastChunkText.substring(overlapStart).trim()
                        if (estimateTokens(overlapText) <= overlapTokens) {
                            currentChunk.append(overlapText)
                            currentTokens = estimateTokens(overlapText)
                            currentLineStart = chunks.last().endLine - overlapText.lines().size + 1
                        } else {
                            currentChunk = StringBuilder()
                            currentTokens = 0
                            currentLineStart = chunks.last().endLine + 1
                        }
                    } else {
                        currentChunk = StringBuilder()
                        currentTokens = 0
                        currentLineStart = chunks.last().endLine + 1
                    }
                } else {
                    currentChunk = StringBuilder()
                    currentTokens = 0
                    currentLineStart = calculateEndLine(
                        chunkText = currentChunk.toString(),
                        startLine = currentLineStart,
                        lines = lines,
                        sectionStartLine = sectionStartLine
                    ) + 1
                }
            }
            
            // Add paragraph to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(paragraph)
            currentTokens += paragraphTokens
            paragraphIndex++
            
            // If we've reached target size and there are more paragraphs, consider finalizing
            // But preserve code blocks and lists as single units
            if (currentTokens >= targetTokens && paragraphIndex < paragraphs.size && !isCodeBlock && !isList) {
                val chunkText = currentChunk.toString().trim()
                if (chunkText.isNotBlank()) {
                    val endLine = calculateEndLine(
                        chunkText = chunkText,
                        startLine = currentLineStart,
                        lines = lines,
                        sectionStartLine = sectionStartLine
                    )
                    chunks.add(
                        NoteChunk(
                            id = generateChunkId(filePath, currentLineStart, endLine),
                            filePath = filePath,
                            startLine = currentLineStart,
                            endLine = endLine,
                            text = chunkText,
                            sectionTitle = sectionTitle
                        )
                    )
                    currentChunk = StringBuilder()
                    currentLineStart = endLine + 1
                    currentTokens = 0
                }
            }
        }
        
        // Add remaining chunk
        if (currentChunk.isNotEmpty()) {
            val chunkText = currentChunk.toString().trim()
            if (chunkText.isNotBlank()) {
                val endLine = calculateEndLine(
                    chunkText = chunkText,
                    startLine = currentLineStart,
                    lines = lines,
                    sectionStartLine = sectionStartLine
                )
                chunks.add(
                    NoteChunk(
                        id = generateChunkId(filePath, currentLineStart, endLine),
                        filePath = filePath,
                        startLine = currentLineStart,
                        endLine = endLine,
                        text = chunkText,
                        sectionTitle = sectionTitle
                    )
                )
            }
        }
        
        return chunks
    }
    
    /**
     * Finds sentence boundaries in text (positions where sentences end).
     * Returns list of character positions where sentences end.
     */
    private fun findSentenceBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        val sentenceEndRegex = Regex("[.!?]\\s+")
        var lastIndex = 0
        
        sentenceEndRegex.findAll(text).forEach { match ->
            boundaries.add(match.range.last + 1)
            lastIndex = match.range.last + 1
        }
        
        // Add end of text if it doesn't end with sentence punctuation
        if (lastIndex < text.length) {
            boundaries.add(text.length)
        }
        
        return boundaries
    }
    
    /**
     * Extracts section title from heading hierarchy.
     * Returns format like "filePath#H1 > H2" or just "filePath#H1".
     */
    private fun extractSectionTitle(
        headingTexts: List<String>,
        headingLevels: List<Int>,
        sectionIndex: Int,
        filePath: String
    ): String? {
        if (sectionIndex == 0) {
            // First section (before any heading)
            return null
        }
        
        val currentHeadingIndex = sectionIndex - 1
        if (currentHeadingIndex < 0 || currentHeadingIndex >= headingTexts.size) {
            return null
        }
        
        val currentLevel = headingLevels[currentHeadingIndex]
        val currentText = headingTexts[currentHeadingIndex]
        
        // Build heading path: find parent headings (h1 if current is h2)
        val titleParts = mutableListOf<String>()
        
        if (currentLevel == 2) {
            // Find preceding h1
            for (i in currentHeadingIndex - 1 downTo 0) {
                if (headingLevels[i] == 1) {
                    titleParts.add(headingTexts[i])
                    break
                }
            }
        }
        
        titleParts.add(currentText)
        
        return if (titleParts.size == 1) {
            "$filePath#${titleParts[0]}"
        } else {
            "$filePath#${titleParts.joinToString(" > ")}"
        }
    }
    
    /**
     * Calculates the end line number for a chunk based on its text content.
     */
    private fun calculateEndLine(
        chunkText: String,
        startLine: Int,
        lines: List<String>,
        sectionStartLine: Int
    ): Int {
        val chunkLines = chunkText.lines().size
        val estimatedEndLine = startLine + chunkLines - 1
        
        // Ensure we don't exceed section boundaries
        val sectionEndLine = sectionStartLine + lines.size
        return minOf(estimatedEndLine, sectionEndLine)
    }
    
    /**
     * Generates a unique ID for a chunk.
     */
    private fun generateChunkId(filePath: String, startLine: Int, endLine: Int): String {
        // Normalize file path for use in ID
        val normalizedPath = filePath.replace('\\', '/').replace(":", "_")
        return "$normalizedPath:$startLine:$endLine"
    }
}

