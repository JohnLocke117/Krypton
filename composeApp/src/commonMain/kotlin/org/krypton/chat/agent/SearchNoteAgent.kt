package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.retrieval.RagRetriever
import org.krypton.rag.RagChunk
import org.krypton.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Agent that searches notes when users request it.
 * 
 * Detects intent patterns like "find notes about X" and performs both
 * vector search (semantic) and keyword search (exact matches) to find
 * relevant notes in the current vault.
 */
class SearchNoteAgent(
    private val ragRetriever: RagRetriever?,
    private val fileSystem: FileSystem,
    private val settingsRepository: SettingsRepository
) : ChatAgent {

    companion object {
        // Intent detection patterns (case-insensitive)
        private val INTENT_PATTERNS = listOf(
            "find notes about",
            "find notes on",
            "search my notes for",
            "search my notes about",
            "search notes for",
            "search notes about",
            "show me notes on",
            "show me notes about",
            "list notes about",
            "list notes on",
            "which notes talk about",
            "which notes mention",
            "which notes discuss"
        )
        
        private const val SNIPPET_LENGTH = 150
        private const val MAX_KEYWORD_RESULTS = 20
    }

    override suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult? {
        val modelName = context.settings.rag.llamaModel
        AppLogger.i("SearchNoteAgent", "Starting query - message: \"$message\", model: $modelName")
        
        // Check if vault is open
        val vaultPath = context.currentVaultPath
        if (vaultPath.isNullOrBlank()) {
            AppLogger.d("SearchNoteAgent", "Ending query - no vault open, agent cannot operate")
            return null
        }

        // Validate vault path exists and is a directory
        if (!fileSystem.isDirectory(vaultPath)) {
            AppLogger.w("SearchNoteAgent", "Vault path is not a directory: $vaultPath")
            AppLogger.d("SearchNoteAgent", "Ending query - invalid vault path")
            return null
        }

        // Detect intent and extract query
        val query = extractQuery(message) ?: run {
            AppLogger.d("SearchNoteAgent", "Ending query - no intent detected")
            return null
        }

        AppLogger.i("SearchNoteAgent", "Detected note search intent for query: $query")

        try {
            // Perform vector search if available
            val vectorResults = if (ragRetriever != null) {
                try {
                    val chunks = ragRetriever.retrieveChunks(query)
                    // Assign decreasing similarity scores based on order (first chunk is most relevant)
                    chunks.mapIndexed { index, chunk ->
                        val filePath = chunk.metadata["filePath"] ?: "unknown"
                        // Assign similarity based on position (higher for earlier results)
                        // First result gets 0.9, decreasing by 0.1 for each subsequent result, minimum 0.3
                        val similarity = (0.9 - (index * 0.1)).coerceAtLeast(0.3)
                        VectorMatch(
                            filePath = filePath,
                            chunk = chunk,
                            similarity = similarity
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w("SearchNoteAgent", "Vector search failed: ${e.message}", e)
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Perform keyword search
            val keywordResults = performKeywordSearch(vaultPath, query)

            // Merge and deduplicate results
            val mergedResults = mergeResults(vectorResults, keywordResults, vaultPath)

            if (mergedResults.isEmpty()) {
                AppLogger.i("SearchNoteAgent", "No matching notes found for query: $query")
                AppLogger.d("SearchNoteAgent", "Ending query - no results")
                return null
            }

            // Convert to NoteMatch format
            val noteMatches = mergedResults.map { result ->
                val title = extractTitle(result.filePath, result.content)
                val snippet = extractSnippet(result.content, query)
                AgentResult.NotesFound.NoteMatch(
                    filePath = result.relativePath,
                    title = title,
                    snippet = snippet,
                    score = result.combinedScore
                )
            }

            AppLogger.i("SearchNoteAgent", "Found ${noteMatches.size} matching notes for query: $query")
            AppLogger.d("SearchNoteAgent", "Ending query - success, results: ${noteMatches.size}")

            return AgentResult.NotesFound(
                query = query,
                results = noteMatches
            )
        } catch (e: Exception) {
            AppLogger.e("SearchNoteAgent", "Error searching notes for query: $query, model: $modelName", e)
            AppLogger.d("SearchNoteAgent", "Ending query - exception occurred")
            return null
        }
    }

    /**
     * Extracts the search query from a message if it matches intent patterns.
     */
    private fun extractQuery(message: String): String? {
        val lowerMessage = message.lowercase().trim()
        
        for (pattern in INTENT_PATTERNS) {
            if (lowerMessage.contains(pattern)) {
                // Extract text after the pattern
                val index = lowerMessage.indexOf(pattern)
                val afterPattern = message.substring(index + pattern.length).trim()
                if (afterPattern.isNotBlank()) {
                    return afterPattern
                }
            }
        }
        
        return null
    }

    /**
     * Performs keyword search over markdown files in the vault.
     */
    private suspend fun performKeywordSearch(
        vaultPath: String,
        query: String
    ): List<KeywordMatch> = withContext(Dispatchers.IO) {
        val queryTokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) {
            return@withContext emptyList()
        }

        val matches = mutableListOf<KeywordMatch>()
        val markdownFiles = findMarkdownFiles(vaultPath)

        for (filePath in markdownFiles) {
            try {
                val content = fileSystem.readFile(filePath) ?: continue
                val lowerContent = content.lowercase()
                
                // Count token matches in title and content
                var matchCount = 0
                var totalTokens = queryTokens.size
                
                for (token in queryTokens) {
                    if (lowerContent.contains(token)) {
                        matchCount++
                    }
                }
                
                if (matchCount > 0) {
                    // Simple score: ratio of matched tokens
                    val score = matchCount.toDouble() / totalTokens
                    
                    // Boost score if tokens appear in filename
                    val fileName = filePath.substringAfterLast('/').lowercase()
                    val filenameBoost = if (queryTokens.any { fileName.contains(it) }) 0.2 else 0.0
                    val finalScore = (score + filenameBoost).coerceIn(0.0, 1.0)
                    
                    matches.add(KeywordMatch(
                        filePath = filePath,
                        content = content,
                        score = finalScore
                    ))
                }
            } catch (e: Exception) {
                AppLogger.w("SearchNoteAgent", "Failed to read file for keyword search: $filePath", e)
            }
        }

        // Sort by score and take top results
        matches.sortedByDescending { it.score }.take(MAX_KEYWORD_RESULTS)
    }

    /**
     * Recursively finds all markdown files in the vault.
     */
    private suspend fun findMarkdownFiles(vaultPath: String): List<String> = withContext(Dispatchers.IO) {
        val files = mutableListOf<String>()
        val toProcess = mutableListOf(vaultPath)
        
        while (toProcess.isNotEmpty()) {
            val currentPath = toProcess.removeAt(0)
            
            try {
                val entries = fileSystem.listFiles(currentPath)
                for (entry in entries) {
                    val fullPath = if (entry.startsWith("/") || entry.contains(":")) {
                        entry
                    } else {
                        "$currentPath/$entry"
                    }
                    
                    if (fileSystem.isDirectory(fullPath)) {
                        toProcess.add(fullPath)
                    } else if (fileSystem.isFile(fullPath) && 
                              fullPath.endsWith(".md", ignoreCase = true)) {
                        files.add(fullPath)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("SearchNoteAgent", "Failed to list files in: $currentPath", e)
            }
        }
        
        files
    }

    /**
     * Merges vector and keyword search results, deduplicating by file path.
     */
    private fun mergeResults(
        vectorResults: List<VectorMatch>,
        keywordResults: List<KeywordMatch>,
        vaultPath: String
    ): List<MergedResult> {
        val resultMap = mutableMapOf<String, MergedResult>()
        
        // Add vector results (weight: 0.7)
        for (vectorMatch in vectorResults) {
            val filePath = vectorMatch.filePath
            val relativePath = if (filePath.startsWith(vaultPath)) {
                filePath.removePrefix(vaultPath).removePrefix("/")
            } else {
                filePath
            }
            
            // Read content if not already available
            val content = try {
                fileSystem.readFile(filePath) ?: ""
            } catch (e: Exception) {
                vectorMatch.chunk.text
            }
            
            resultMap[filePath] = MergedResult(
                filePath = filePath,
                relativePath = relativePath,
                content = content,
                vectorScore = vectorMatch.similarity,
                keywordScore = 0.0,
                combinedScore = vectorMatch.similarity * 0.7
            )
        }
        
        // Add/merge keyword results (weight: 0.3)
        for (keywordMatch in keywordResults) {
            val existing = resultMap[keywordMatch.filePath]
            if (existing != null) {
                // Merge: combine scores
                val combinedScore = (existing.vectorScore * 0.7) + (keywordMatch.score * 0.3)
                resultMap[keywordMatch.filePath] = existing.copy(
                    keywordScore = keywordMatch.score,
                    combinedScore = combinedScore.coerceIn(0.0, 1.0)
                )
            } else {
                // New result from keyword search only
                val relativePath = if (keywordMatch.filePath.startsWith(vaultPath)) {
                    keywordMatch.filePath.removePrefix(vaultPath).removePrefix("/")
                } else {
                    keywordMatch.filePath
                }
                
                resultMap[keywordMatch.filePath] = MergedResult(
                    filePath = keywordMatch.filePath,
                    relativePath = relativePath,
                    content = keywordMatch.content,
                    vectorScore = 0.0,
                    keywordScore = keywordMatch.score,
                    combinedScore = keywordMatch.score * 0.3
                )
            }
        }
        
        // Sort by combined score and return
        return resultMap.values.sortedByDescending { it.combinedScore }
    }

    /**
     * Extracts title from file path or content.
     */
    private fun extractTitle(filePath: String, content: String): String {
        // Try to get first heading from content
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim()
            } else if (trimmed.startsWith("#")) {
                return trimmed.substring(1).trim()
            }
        }
        
        // Fallback to filename without extension
        return filePath.substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('-', ' ')
            .replace('_', ' ')
    }

    /**
     * Extracts a snippet showing why the note matched.
     */
    private fun extractSnippet(content: String, query: String): String {
        val queryTokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val lowerContent = content.lowercase()
        
        // Find first line containing a query token
        for (line in content.lines()) {
            val lowerLine = line.lowercase()
            if (queryTokens.any { lowerLine.contains(it) }) {
                val snippet = line.trim()
                if (snippet.length <= SNIPPET_LENGTH) {
                    return snippet
                } else {
                    return snippet.take(SNIPPET_LENGTH) + "..."
                }
            }
        }
        
        // Fallback: first non-empty line
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                return if (trimmed.length <= SNIPPET_LENGTH) {
                    trimmed
                } else {
                    trimmed.take(SNIPPET_LENGTH) + "..."
                }
            }
        }
        
        // Last resort: first N characters
        return content.trim().take(SNIPPET_LENGTH) + if (content.length > SNIPPET_LENGTH) "..." else ""
    }

    // Helper data classes
    private data class VectorMatch(
        val filePath: String,
        val chunk: RagChunk,
        val similarity: Double
    )

    private data class KeywordMatch(
        val filePath: String,
        val content: String,
        val score: Double
    )

    private data class MergedResult(
        val filePath: String,
        val relativePath: String,
        val content: String,
        val vectorScore: Double,
        val keywordScore: Double,
        val combinedScore: Double
    )
}

