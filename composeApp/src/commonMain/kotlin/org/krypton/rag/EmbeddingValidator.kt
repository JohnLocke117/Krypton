package org.krypton.rag

import org.krypton.config.RagDefaults
import org.krypton.util.AppLogger

/**
 * Utility for validating and splitting chunks before embedding to prevent
 * "input length exceeds the context length" errors.
 * 
 * Ensures chunks respect the embedding model's context limit, accounting for
 * prefixes added by the embedding service (e.g., "search_document: ").
 */
object EmbeddingValidator {
    // For mxbai-embed-large:335m via Ollama (max=1818 chars including prefix)
    // We stay below the theoretical limit to add safety margin
    private const val MAX_CONTENT_CHARS: Int = 1700
    private const val CHUNK_OVERLAP_CHARS: Int = 150
    
    /**
     * Internal data class for fixed-size chunking with overlap.
     */
    private data class TextChunk(
        val id: String,
        val content: String,
        val startOffset: Int,
        val endOffset: Int,
    )
    
    /**
     * Estimates the maximum content characters allowed for a given embedding model.
     * 
     * Uses model name heuristics to determine context limits:
     * - Models with "512" in name or known 512-token models: ~1700 chars (conservative)
     * - Models with "large" but no specific token count: ~2000 chars (conservative)
     * - Default: uses RagDefaults.MAX_EMBEDDING_CONTEXT_CHARS
     * 
     * @param modelName The embedding model name (e.g., "mxbai-embed-large:335m")
     * @return Maximum content characters (excluding prefix)
     */
    fun estimateMaxContentChars(modelName: String): Int {
        val lowerModel = modelName.lowercase()
        
        // Known 512-token models or models with "512" in name
        if (lowerModel.contains("512") || 
            lowerModel.contains("mxbai-embed-large") ||
            lowerModel.contains("bge-small") ||
            lowerModel.contains("e5-small")) {
            // 512 tokens total - prefix (~4-5 tokens) = ~507 tokens for content
            // 507 tokens * 3.5 chars/token (conservative estimate) = ~1775 chars
            // Use 1700 chars for safety margin (100 chars below 1800 limit)
            // This ensures we stay well below the 1818-char total limit (1700 + 18 prefix = 1718)
            return MAX_CONTENT_CHARS
        }
        
        // Models with larger context windows (8192+ tokens)
        if (lowerModel.contains("nomic-embed") ||
            lowerModel.contains("bge-large") ||
            lowerModel.contains("e5-large")) {
            // These support 8192 tokens, but use conservative default
            return RagDefaults.Embedding.MAX_EMBEDDING_CONTEXT_CHARS
        }
        
        // Default: conservative limit
        return RagDefaults.Embedding.MAX_EMBEDDING_CONTEXT_CHARS
    }
    
    /**
     * Validates and splits a chunk if it exceeds the embedding context limit.
     * 
     * @param chunk The chunk to validate
     * @param maxChars Maximum characters allowed (including prefix). If null, uses default from RagDefaults.
     * @param prefixLength Length of prefix that will be added (e.g., "search_document: " = 18)
     * @return List of validated chunks (may be split if original was too large)
     */
    fun validateAndSplitChunk(
        chunk: RagChunk,
        maxChars: Int? = null,
        prefixLength: Int = RagDefaults.Embedding.DOCUMENT_PREFIX_LENGTH
    ): List<RagChunk> {
        val effectiveMaxChars = maxChars ?: RagDefaults.Embedding.MAX_EMBEDDING_CONTEXT_CHARS
        val maxContentChars = effectiveMaxChars - prefixLength
        
        // Check if chunk text exceeds limit
        if (chunk.text.length <= maxContentChars) {
            return listOf(chunk)
        }
        
        // Chunk is too large, need to split
        val filePath = chunk.metadata["filePath"] ?: "unknown"
        val originalLength = chunk.text.length
        AppLogger.w(
            "EmbeddingValidator",
            "Chunk exceeds embedding limit: file=$filePath, originalLength=$originalLength, maxContentChars=$maxContentChars. Splitting..."
        )
        
        val splitChunks = splitChunk(chunk, maxContentChars)
        
        AppLogger.i(
            "EmbeddingValidator",
            "Split chunk from file=$filePath: originalLength=$originalLength -> ${splitChunks.size} chunks"
        )
        
        return splitChunks
    }
    
    /**
     * Splits a chunk that exceeds the size limit.
     * 
     * Strategy:
     * 1. Try to split at sentence boundaries
     * 2. If still too large, split at word boundaries
     * 3. Last resort: use fixed-size character-based chunking with overlap
     */
    private fun splitChunk(chunk: RagChunk, maxContentChars: Int): List<RagChunk> {
        val text = chunk.text
        val filePath = chunk.metadata["filePath"] ?: "unknown"
        val sectionTitle = chunk.metadata["sectionTitle"]
        val startLine = chunk.metadata["startLine"]?.toIntOrNull() ?: 1
        val endLine = chunk.metadata["endLine"]?.toIntOrNull() ?: startLine
        
        // First, try the existing boundary-based approach
        val boundaryChunks = mutableListOf<RagChunk>()
        var remainingText = text
        var currentStartLine = startLine
        var chunkIndex = 0
        var useFixedSize = false
        
        while (remainingText.isNotEmpty() && !useFixedSize) {
            if (remainingText.length <= maxContentChars) {
                // Last chunk fits
                boundaryChunks.add(createSplitChunk(
                    text = remainingText,
                    originalChunk = chunk,
                    chunkIndex = chunkIndex,
                    startLine = currentStartLine,
                    endLine = endLine
                ))
                break
            }
            
            // Try to find a good split point
            val splitPoint = findSplitPoint(remainingText, maxContentChars)
            
            if (splitPoint <= 0) {
                // No good split point found, fall back to fixed-size chunking
                useFixedSize = true
                break
            }
            
            // Split at the found point
            val chunkText = remainingText.substring(0, splitPoint).trim()
            
            // Verify the chunk doesn't exceed limit (shouldn't happen, but double-check)
            if (chunkText.length > maxContentChars) {
                useFixedSize = true
                break
            }
            
            boundaryChunks.add(createSplitChunk(
                text = chunkText,
                originalChunk = chunk,
                chunkIndex = chunkIndex,
                startLine = currentStartLine,
                endLine = endLine
            ))
            
            // Update remaining text and line number
            remainingText = remainingText.substring(splitPoint).trimStart()
            currentStartLine += chunkText.lines().size
            chunkIndex++
        }
        
        // If boundary-based approach worked, return those chunks
        if (!useFixedSize && boundaryChunks.isNotEmpty()) {
            return boundaryChunks
        }
        
        // Fall back to fixed-size chunking with overlap
        AppLogger.w(
            "EmbeddingValidator",
            "Falling back to fixed-size chunking for chunk from file=$filePath (length=${text.length}, maxContentChars=$maxContentChars)"
        )
        
        val fixedChunks = fixedSizeChunksWithOverlap(
            text = text,
            maxChars = maxContentChars,
            overlapChars = CHUNK_OVERLAP_CHARS
        )
        
        return fixedChunks.mapIndexed { index, textChunk ->
            // Estimate line numbers based on character offsets
            val estimatedStartLine = startLine + text.substring(0, textChunk.startOffset).lines().size - 1
            val estimatedEndLine = startLine + text.substring(0, textChunk.endOffset).lines().size - 1
            
            createSplitChunk(
                text = textChunk.content,
                originalChunk = chunk,
                chunkIndex = index,
                startLine = estimatedStartLine.coerceAtLeast(startLine),
                endLine = estimatedEndLine.coerceAtMost(endLine)
            )
        }
    }
    
    /**
     * Finds the best split point in text that doesn't exceed maxChars.
     * 
     * Prefers sentence boundaries, then paragraph boundaries, then word boundaries.
     */
    private fun findSplitPoint(text: String, maxChars: Int): Int {
        if (text.length <= maxChars) {
            return text.length
        }
        
        // Try to split at sentence boundaries first
        val sentenceEndRegex = Regex("[.!?]\\s+")
        var lastSentenceEnd = 0
        
        sentenceEndRegex.findAll(text).forEach { match ->
            val endPos = match.range.last + 1
            if (endPos <= maxChars) {
                lastSentenceEnd = endPos
            } else {
                return@forEach
            }
        }
        
        if (lastSentenceEnd > 0 && lastSentenceEnd >= maxChars * 0.7) {
            // Found a sentence boundary that's reasonably close to maxChars
            return lastSentenceEnd
        }
        
        // Try paragraph boundaries (double newline)
        val paragraphEnd = text.lastIndexOf("\n\n", maxChars)
        if (paragraphEnd > 0 && paragraphEnd >= maxChars * 0.7) {
            return paragraphEnd + 2
        }
        
        // Try single newline
        val newlineEnd = text.lastIndexOf('\n', maxChars)
        if (newlineEnd > 0 && newlineEnd >= maxChars * 0.7) {
            return newlineEnd + 1
        }
        
        // Try word boundaries
        val wordEnd = text.lastIndexOf(' ', maxChars)
        if (wordEnd > 0 && wordEnd >= maxChars * 0.7) {
            return wordEnd + 1
        }
        
        // No good split point found
        return 0
    }
    
    /**
     * Creates a split chunk with preserved metadata.
     */
    private fun createSplitChunk(
        text: String,
        originalChunk: RagChunk,
        chunkIndex: Int,
        startLine: Int,
        endLine: Int
    ): RagChunk {
        val filePath = originalChunk.metadata["filePath"] ?: "unknown"
        val originalId = originalChunk.id
        
        // Create new ID for split chunk
        val newId = if (chunkIndex == 0) {
            originalId
        } else {
            "$originalId:split$chunkIndex"
        }
        
        // Preserve all metadata, update line numbers if available
        val newMetadata = originalChunk.metadata.toMutableMap()
        newMetadata["startLine"] = startLine.toString()
        newMetadata["endLine"] = endLine.toString()
        if (chunkIndex > 0) {
            newMetadata["splitFrom"] = originalId
            newMetadata["splitIndex"] = chunkIndex.toString()
        }
        
        return RagChunk(
            id = newId,
            text = text,
            metadata = newMetadata,
            embedding = null // Will be set after embedding
        )
    }
    
    /**
     * Validates a list of chunks and splits any that exceed the limit.
     * 
     * @param chunks List of chunks to validate
     * @param maxChars Maximum characters allowed (including prefix)
     * @param prefixLength Length of prefix that will be added
     * @return List of validated chunks (may include splits)
     */
    fun validateAndSplitChunks(
        chunks: List<RagChunk>,
        maxChars: Int? = null,
        prefixLength: Int = RagDefaults.Embedding.DOCUMENT_PREFIX_LENGTH
    ): List<RagChunk> {
        val validatedChunks = mutableListOf<RagChunk>()
        var splitCount = 0
        
        for (chunk in chunks) {
            val validated = validateAndSplitChunk(chunk, maxChars, prefixLength)
            if (validated.size > 1) {
                splitCount++
            }
            validatedChunks.addAll(validated)
        }
        
        if (splitCount > 0) {
            val filePath = chunks.firstOrNull()?.metadata?.get("filePath") ?: "unknown"
            AppLogger.i(
                "EmbeddingValidator",
                "Validated ${chunks.size} chunks from file=$filePath: ${splitCount} chunks split, total chunks after validation: ${validatedChunks.size}"
            )
        }
        
        return validatedChunks
    }
    
    /**
     * Creates fixed-size character-based chunks with overlap.
     * 
     * Uses a sliding window approach where each chunk is at most maxChars long,
     * with overlapChars of overlap between consecutive chunks.
     * 
     * @param text The text to chunk
     * @param maxChars Maximum characters per chunk (default: MAX_CONTENT_CHARS)
     * @param overlapChars Number of characters to overlap between chunks (default: CHUNK_OVERLAP_CHARS)
     * @return List of TextChunk objects with id, content, and offsets
     */
    private fun fixedSizeChunksWithOverlap(
        text: String,
        maxChars: Int = MAX_CONTENT_CHARS,
        overlapChars: Int = CHUNK_OVERLAP_CHARS,
    ): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        var start = 0
        var index = 0

        val step = (maxChars - overlapChars).coerceAtLeast(1)

        while (start < text.length) {
            val end = (start + maxChars).coerceAtMost(text.length)
            var chunkText = text.substring(start, end)

            // Optional: trim end to nearest newline boundary if within reasonable range
            val lastNewline = chunkText.lastIndexOf('\n')
            if (lastNewline in 200 until chunkText.length) {
                chunkText = chunkText.substring(0, lastNewline)
            }

            val actualEnd = start + chunkText.length

            chunks += TextChunk(
                id = "chunk_$index",
                content = chunkText,
                startOffset = start,
                endOffset = actualEnd,
            )

            index += 1
            if (actualEnd >= text.length) break

            start = (actualEnd - overlapChars).coerceAtLeast(0)
        }

        return chunks
    }
}

