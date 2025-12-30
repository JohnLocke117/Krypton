package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger

/**
 * Agent that creates markdown notes when users request it.
 * 
 * Detects intent patterns like "create a note on X" and generates
 * a markdown note about the topic, saving it to the current vault.
 */
class CreateNoteAgent(
    private val llamaClient: LlamaClient,
    private val fileSystem: FileSystem,
    private val settingsRepository: SettingsRepository
) : ChatAgent {

    companion object {
        // Intent detection patterns (case-insensitive)
        private val INTENT_PATTERNS = listOf(
            "create a note on",
            "create a short note on",
            "create note on",
            "make a note about",
            "make a note on",
            "write a note about",
            "write a note on",
            "create a note about"
        )
        
        private const val PREVIEW_LENGTH = 200
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
        AppLogger.i("CreateNoteAgent", "Starting query - message: \"$message\", model: $modelName")
        
        // Check if vault is open
        val vaultPath = context.currentVaultPath
        if (vaultPath.isNullOrBlank()) {
            AppLogger.d("CreateNoteAgent", "Ending query - no vault open, agent cannot operate")
            return null // No vault open, agent cannot operate
        }

        // Validate vault path exists and is a directory
        if (!fileSystem.isDirectory(vaultPath)) {
            AppLogger.w("CreateNoteAgent", "Vault path is not a directory: $vaultPath")
            AppLogger.d("CreateNoteAgent", "Ending query - invalid vault path")
            return null
        }

        // Detect intent
        val topic = extractTopic(message) ?: run {
            AppLogger.d("CreateNoteAgent", "Ending query - no intent detected")
            return null
        }

        AppLogger.i("CreateNoteAgent", "Detected note creation intent for topic: $topic")

        try {
            // Generate content
            val content = generateNoteContent(topic)
            if (content.isBlank()) {
                AppLogger.w("CreateNoteAgent", "Generated content is empty for topic: $topic")
                AppLogger.d("CreateNoteAgent", "Ending query - empty content generated")
                return null
            }

            // Generate filename
            val fileName = generateFileName(topic, vaultPath)
            val filePath = "$vaultPath/$fileName"

            // Write file
            val success = fileSystem.writeFile(filePath, content)
            if (!success) {
                AppLogger.e("CreateNoteAgent", "Failed to write file: $filePath")
                AppLogger.d("CreateNoteAgent", "Ending query - file write failed")
                return null
            }

            // Extract title and preview
            val title = extractTitle(content, topic)
            val preview = extractPreview(content)

            AppLogger.i("CreateNoteAgent", "Created note: $filePath")
            AppLogger.i("CreateNoteAgent", "Ending query - success, file: $filePath, model: $modelName")

            return AgentResult.NoteCreated(
                filePath = filePath,
                title = title,
                preview = preview
            )
        } catch (e: Exception) {
            AppLogger.e("CreateNoteAgent", "Error creating note for topic: $topic, model: $modelName", e)
            AppLogger.d("CreateNoteAgent", "Ending query - exception occurred")
            return null
        }
    }

    /**
     * Extracts the topic from a message if it matches intent patterns.
     */
    private fun extractTopic(message: String): String? {
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
     * Generates markdown content for a topic using the LLM.
     */
    private suspend fun generateNoteContent(topic: String): String {
        val prompt = """
            Write a short markdown note about "$topic". 
            Include a title as # $topic and a few bullet points or a brief explanation.
            Keep it concise and informative.
        """.trimIndent()

        return llamaClient.complete(prompt).trim()
    }

    /**
     * Generates a safe filename from a topic, handling collisions.
     */
    private fun generateFileName(topic: String, vaultPath: String): String {
        // Slugify topic
        var baseName = slugify(topic)
        
        // Ensure it ends with .md
        if (!baseName.endsWith(".md", ignoreCase = true)) {
            baseName += ".md"
        }

        // Check for collisions and append numeric suffix if needed
        var fileName = baseName
        var counter = 1
        
        while (fileSystem.exists("$vaultPath/$fileName")) {
            val nameWithoutExt = baseName.removeSuffix(".md")
            fileName = "$nameWithoutExt-$counter.md"
            counter++
            
            // Safety limit to prevent infinite loop
            if (counter > 1000) {
                AppLogger.w("CreateNoteAgent", "Too many collisions for filename: $baseName")
                break
            }
        }

        return fileName
    }

    /**
     * Converts a string to a safe filename slug.
     * - Lowercase
     * - Replace spaces with hyphens
     * - Remove invalid characters
     * - Limit length
     */
    private fun slugify(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "") // Remove invalid chars
            .replace(Regex("\\s+"), "-") // Replace spaces with hyphens
            .replace(Regex("-+"), "-") // Collapse multiple hyphens
            .trim('-') // Remove leading/trailing hyphens
            .take(100) // Limit length
            .ifBlank { "note" } // Fallback if empty
    }

    /**
     * Extracts the title from markdown content.
     * Looks for the first # heading, or uses the topic as fallback.
     */
    private fun extractTitle(content: String, topic: String): String {
        // Look for first # heading
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim()
            } else if (trimmed.startsWith("#")) {
                return trimmed.substring(1).trim()
            }
        }
        
        // Fallback to topic
        return topic
    }

    /**
     * Extracts a preview from markdown content.
     * Returns first paragraph or first N characters.
     */
    private fun extractPreview(content: String): String {
        // Try to get first paragraph (text between blank lines)
        val lines = content.lines()
        val firstParagraph = buildString {
            for (line in lines) {
                val trimmed = line.trim()
                // Skip empty lines and headings at start
                if (trimmed.isEmpty()) {
                    if (isNotEmpty()) break // End of first paragraph
                    continue
                }
                if (trimmed.startsWith("#")) continue // Skip headings
                if (isNotEmpty()) append(" ")
                append(trimmed)
            }
        }
        
        val preview = if (firstParagraph.isNotBlank()) {
            firstParagraph
        } else {
            // Fallback: first N characters of content (excluding markdown syntax)
            content.replace(Regex("^#+\\s*"), "") // Remove heading markers
                .trim()
                .take(PREVIEW_LENGTH)
        }
        
        return preview.take(PREVIEW_LENGTH).trim()
    }
}

