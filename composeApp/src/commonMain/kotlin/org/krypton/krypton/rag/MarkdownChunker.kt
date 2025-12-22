package org.krypton.krypton.rag

/**
 * Chunks markdown content into smaller pieces for embedding and retrieval.
 * 
 * Strategy:
 * 1. Split by top-level headings (h1, h2)
 * 2. Further split long sections into ~300-500 word chunks
 */
class MarkdownChunker {
    
    /**
     * Chunks markdown content into NoteChunk objects.
     * 
     * @param content The markdown file content
     * @param filePath The path to the source file
     * @return List of NoteChunk objects with line numbers and text
     */
    fun chunk(content: String, filePath: String): List<NoteChunk> {
        if (content.isBlank()) {
            return emptyList()
        }
        
        val lines = content.lines()
        val chunks = mutableListOf<NoteChunk>()
        
        // First, identify top-level headings (h1 or h2)
        val headingIndices = mutableListOf<Int>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("# ") || line.startsWith("## ")) {
                headingIndices.add(i)
            }
        }
        
        // Add end of file as a boundary
        headingIndices.add(lines.size)
        
        // Process each section between headings
        for (sectionIndex in headingIndices.indices) {
            val startLine = if (sectionIndex == 0) 0 else headingIndices[sectionIndex - 1]
            val endLine = headingIndices[sectionIndex]
            
            if (startLine >= endLine) continue
            
            val sectionLines = lines.subList(startLine, endLine)
            val sectionText = sectionLines.joinToString("\n")
            
            // Split long sections into smaller chunks by word count
            val sectionChunks = splitByWordCount(sectionText, startLine + 1, filePath)
            chunks.addAll(sectionChunks)
        }
        
        return chunks
    }
    
    /**
     * Splits text into chunks of approximately 300-500 words.
     * Tries to break at paragraph boundaries when possible.
     */
    private fun splitByWordCount(
        text: String,
        startLineNumber: Int,
        filePath: String
    ): List<NoteChunk> {
        val chunks = mutableListOf<NoteChunk>()
        val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
        
        if (paragraphs.isEmpty()) {
            return emptyList()
        }
        
        var currentChunk = StringBuilder()
        var currentLineStart = startLineNumber
        var currentWordCount = 0
        var paragraphIndex = 0
        
        for (paragraph in paragraphs) {
            val wordCount = paragraph.split(Regex("\\s+")).count { it.isNotBlank() }
            
            // If adding this paragraph would exceed 500 words, finalize current chunk
            if (currentWordCount > 0 && currentWordCount + wordCount > 500) {
                if (currentChunk.isNotEmpty()) {
                    val chunkText = currentChunk.toString().trim()
                    if (chunkText.isNotBlank()) {
                        // Estimate end line (rough approximation)
                        val estimatedEndLine = currentLineStart + chunkText.lines().size - 1
                        chunks.add(
                            NoteChunk(
                                id = generateChunkId(filePath, currentLineStart, estimatedEndLine),
                                filePath = filePath,
                                startLine = currentLineStart,
                                endLine = estimatedEndLine,
                                text = chunkText
                            )
                        )
                    }
                }
                // Start new chunk
                currentChunk = StringBuilder()
                currentLineStart = startLineNumber + paragraphIndex
                currentWordCount = 0
            }
            
            // Add paragraph to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(paragraph)
            currentWordCount += wordCount
            paragraphIndex++
            
            // If we've reached a good size (300+ words), consider finalizing
            if (currentWordCount >= 300 && paragraphIndex < paragraphs.size) {
                val chunkText = currentChunk.toString().trim()
                if (chunkText.isNotBlank()) {
                    val estimatedEndLine = currentLineStart + chunkText.lines().size - 1
                    chunks.add(
                        NoteChunk(
                            id = generateChunkId(filePath, currentLineStart, estimatedEndLine),
                            filePath = filePath,
                            startLine = currentLineStart,
                            endLine = estimatedEndLine,
                            text = chunkText
                        )
                    )
                    currentChunk = StringBuilder()
                    currentLineStart = estimatedEndLine + 1
                    currentWordCount = 0
                }
            }
        }
        
        // Add remaining chunk
        if (currentChunk.isNotEmpty()) {
            val chunkText = currentChunk.toString().trim()
            if (chunkText.isNotBlank()) {
                val estimatedEndLine = currentLineStart + chunkText.lines().size - 1
                chunks.add(
                    NoteChunk(
                        id = generateChunkId(filePath, currentLineStart, estimatedEndLine),
                        filePath = filePath,
                        startLine = currentLineStart,
                        endLine = estimatedEndLine,
                        text = chunkText
                    )
                )
            }
        }
        
        return chunks
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

