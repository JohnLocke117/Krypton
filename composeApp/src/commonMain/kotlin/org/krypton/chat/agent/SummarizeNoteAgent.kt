package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.LlamaClient
import org.krypton.rag.RagChunk
import org.krypton.retrieval.RagRetriever
import org.krypton.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Agent that summarizes notes when users request it.
 * 
 * Supports two modes:
 * 1. Current-note mode: Summarizes the currently active note
 * 2. Topic-from-vault mode: Summarizes notes about a specific topic from the vault
 */
class SummarizeNoteAgent(
    private val llamaClient: LlamaClient,
    private val ragRetriever: RagRetriever?,
    private val fileSystem: FileSystem,
    private val settingsRepository: SettingsRepository
) : ChatAgent {

    companion object {
        // Intent patterns for current-note mode
        private val CURRENT_NOTE_PATTERNS = listOf(
            "summarize this note",
            "summarize the current note",
            "summarize this",
            "give me a summary of this note",
            "give me a summary of this",
            "summarize the note",
            "what is this note about"
        )
        
        // Intent patterns for topic-from-vault mode
        // Note: Patterns use "summarize" but matching is case-insensitive, so "summarise" (British spelling) will also match
        private val TOPIC_PATTERNS = listOf(
            "summarize my notes about",
            "summarize my notes on",
            "summarize notes about",
            "summarize notes on",
            "summarise my notes about",  // British spelling
            "summarise my notes on",     // British spelling
            "summarise notes about",     // British spelling
            "summarise notes on",        // British spelling
            "give a summary of my notes about",
            "give a summary of my notes on",
            "give me a summary of my notes about",
            "give me a summary of my notes on",
            "what do my notes say about"
        )
        
        private const val MAX_CHUNKS_FOR_SUMMARY = 6  // Reduced for 8B model capacity
        private const val MAX_WORDS_PER_CHUNK = 800    // Limit chunk size for 8B model
    }

    override suspend fun tryHandle(
        message: String,
        chatHistory: List<ChatMessage>,
        context: AgentContext
    ): AgentResult? {
        val modelName = when (context.settings.llm.provider) {
            org.krypton.LlmProvider.OLLAMA -> context.settings.llm.ollamaModel
            org.krypton.LlmProvider.GEMINI -> context.settings.llm.geminiModel
        }
        AppLogger.i("SummarizeNoteAgent", "Starting query - message: \"$message\", model: $modelName")
        
        val lowerMessage = message.lowercase().trim()
        
        // Check for current-note mode
        val isCurrentNoteMode = CURRENT_NOTE_PATTERNS.any { lowerMessage.contains(it) }
        if (isCurrentNoteMode) {
            return handleCurrentNoteMode(context, modelName)
        }
        
        // Check for topic-from-vault mode
        val topic = extractTopic(message)
        if (topic != null) {
            AppLogger.i("SummarizeNoteAgent", "Topic extracted: \"$topic\"")
            return handleTopicMode(context, topic, modelName)
        }
        
        AppLogger.d("SummarizeNoteAgent", "Ending query - no intent detected")
        return null
    }

    /**
     * Handles summarization of the current note.
     */
    private suspend fun handleCurrentNoteMode(
        context: AgentContext,
        modelName: String
    ): AgentResult? {
        val notePath = context.currentNotePath
        if (notePath.isNullOrBlank()) {
            AppLogger.d("SummarizeNoteAgent", "Ending query - no current note path")
            return null
        }

        if (!fileSystem.isFile(notePath)) {
            AppLogger.w("SummarizeNoteAgent", "Current note path is not a file: $notePath")
            AppLogger.d("SummarizeNoteAgent", "Ending query - invalid note path")
            return null
        }

        try {
            val content = withContext(Dispatchers.IO) {
                fileSystem.readFile(notePath)
            }

            if (content.isNullOrBlank()) {
                AppLogger.w("SummarizeNoteAgent", "Note content is empty: $notePath")
                AppLogger.d("SummarizeNoteAgent", "Ending query - empty content")
                return null
            }

            val title = extractTitle(content, notePath)
            // For current note mode, use a simple prompt
            val summary = generateSummaryForCurrentNote(content)

            if (summary.isBlank()) {
                AppLogger.w("SummarizeNoteAgent", "Generated summary is empty for note: $notePath")
                AppLogger.d("SummarizeNoteAgent", "Ending query - empty summary")
                return null
            }

            // Get relative path
            val vaultPath = context.currentVaultPath
            val relativePath = if (vaultPath != null && notePath.startsWith(vaultPath)) {
                notePath.removePrefix(vaultPath).removePrefix("/")
            } else {
                notePath
            }

            AppLogger.i("SummarizeNoteAgent", "Summarized current note: $notePath")
            AppLogger.d("SummarizeNoteAgent", "Ending query - success, model: $modelName")

            return AgentResult.NoteSummarized(
                title = title,
                summary = summary,
                sourceFiles = listOf(relativePath)
            )
        } catch (e: Exception) {
            AppLogger.e("SummarizeNoteAgent", "Error summarizing current note: $notePath, model: $modelName", e)
            AppLogger.d("SummarizeNoteAgent", "Ending query - exception occurred")
            return null
        }
    }

    /**
     * Handles summarization of notes about a topic from the vault.
     */
    private suspend fun handleTopicMode(
        context: AgentContext,
        topic: String,
        modelName: String
    ): AgentResult? {
        val vaultPath = context.currentVaultPath
        if (vaultPath.isNullOrBlank()) {
            AppLogger.d("SummarizeNoteAgent", "Ending query - no vault open")
            return null
        }

        if (ragRetriever == null) {
            AppLogger.d("SummarizeNoteAgent", "Ending query - RagRetriever not available for topic mode")
            return null
        }

        try {
            // Build better retrieval query
            val retrievalQuery = buildRetrievalQuery(topic)
            AppLogger.i("SummarizeNoteAgent", "Retrieval query: \"$retrievalQuery\"")
            
            // Retrieve relevant chunks
            val allChunks = ragRetriever.retrieveChunks(retrievalQuery)
            
            // Filter for relevance and limit count
            val relevantChunks = allChunks
                .filter { isChunkRelevant(it, topic) }
                .take(MAX_CHUNKS_FOR_SUMMARY)
            
            AppLogger.i("SummarizeNoteAgent", "Retrieved ${allChunks.size} chunks, ${relevantChunks.size} relevant after filtering")

            if (relevantChunks.isEmpty()) {
                AppLogger.i("SummarizeNoteAgent", "No relevant chunks found for topic: $topic")
                AppLogger.d("SummarizeNoteAgent", "Ending query - no relevant chunks")
                return null
            }
            
            val chunks = relevantChunks

            // Extract unique file paths from chunks
            val sourceFiles = chunks.mapNotNull { chunk ->
                chunk.metadata["filePath"]
            }.distinct().map { filePath ->
                // Convert to relative path if possible
                if (vaultPath != null && filePath.startsWith(vaultPath)) {
                    filePath.removePrefix(vaultPath).removePrefix("/")
                } else {
                    filePath
                }
            }

            // Concatenate chunks for context (truncate long chunks)
            val contextText = buildString {
                chunks.forEachIndexed { index, chunk ->
                    val filePath = chunk.metadata["filePath"] ?: "unknown"
                    val sectionTitle = chunk.metadata["sectionTitle"]
                    val truncatedText = truncateChunkText(chunk.text, MAX_WORDS_PER_CHUNK)
                    
                    if (index > 0) appendLine()
                    if (sectionTitle != null) {
                        appendLine("## $sectionTitle")
                    }
                    appendLine("From: $filePath")
                    appendLine()
                    appendLine(truncatedText)
                    appendLine()
                }
            }

            val summary = generateSummary(contextText, topic)

            if (summary.isBlank()) {
                AppLogger.w("SummarizeNoteAgent", "Generated summary is empty for topic: $topic")
                AppLogger.d("SummarizeNoteAgent", "Ending query - empty summary")
                return null
            }

            AppLogger.i("SummarizeNoteAgent", "Summarized topic: $topic from ${sourceFiles.size} files")
            AppLogger.d("SummarizeNoteAgent", "Ending query - success, model: $modelName")

            return AgentResult.NoteSummarized(
                title = topic,
                summary = summary,
                sourceFiles = sourceFiles
            )
        } catch (e: Exception) {
            AppLogger.e("SummarizeNoteAgent", "Error summarizing topic: $topic, model: $modelName", e)
            AppLogger.d("SummarizeNoteAgent", "Ending query - exception occurred")
            return null
        }
    }

    /**
     * Extracts the topic from a message if it matches topic patterns.
     */
    private fun extractTopic(message: String): String? {
        val lowerMessage = message.lowercase().trim()
        
        for (pattern in TOPIC_PATTERNS) {
            if (lowerMessage.contains(pattern)) {
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
     * Generates a summary using the LLM for topic-based summarization.
     * Designed for 8B Llama model - simple, direct prompt.
     */
    private suspend fun generateSummary(content: String, topic: String): String {
        val prompt = """Summarize the following notes about $topic.

$content

Summary:"""

        AppLogger.d("SummarizeNoteAgent", "Generating summary for topic: $topic")
        val summary = llamaClient.complete(prompt).trim()
        
        if (summary.isBlank()) {
            AppLogger.w("SummarizeNoteAgent", "Summary generation returned empty result")
        } else {
            AppLogger.d("SummarizeNoteAgent", "Summary generated, length: ${summary.length}")
        }
        
        return summary
    }
    
    /**
     * Generates a summary for the current note.
     * Designed for 8B Llama model - simple, direct prompt.
     */
    private suspend fun generateSummaryForCurrentNote(content: String): String {
        val prompt = """Summarize the following note.

$content

Summary:"""

        AppLogger.d("SummarizeNoteAgent", "Generating summary for current note")
        val summary = llamaClient.complete(prompt).trim()
        
        if (summary.isBlank()) {
            AppLogger.w("SummarizeNoteAgent", "Summary generation returned empty result")
        } else {
            AppLogger.d("SummarizeNoteAgent", "Summary generated, length: ${summary.length}")
        }
        
        return summary
    }

    /**
     * Builds a better retrieval query from a topic.
     */
    private fun buildRetrievalQuery(topic: String): String {
        return "notes about $topic"
    }
    
    /**
     * Truncates chunk text to a maximum word count for 8B model capacity.
     */
    private fun truncateChunkText(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+"))
        if (words.size <= maxWords) {
            return text
        }
        return words.take(maxWords).joinToString(" ") + "..."
    }
    
    /**
     * Checks if a chunk is relevant to the topic using simple keyword matching.
     */
    private fun isChunkRelevant(chunk: RagChunk, topic: String): Boolean {
        val topicLower = topic.lowercase()
        val textLower = chunk.text.lowercase()
        // Simple check: topic appears in chunk text
        return textLower.contains(topicLower)
    }
    
    /**
     * Extracts title from content or file path.
     */
    private fun extractTitle(content: String, filePath: String): String {
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
}

