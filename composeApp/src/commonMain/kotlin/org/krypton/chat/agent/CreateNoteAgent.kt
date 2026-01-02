package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.rag.LlamaClient
import org.krypton.util.AppLogger

/**
 * Interface for creating markdown notes.
 * 
 * Assumes intent has already been classified as CREATE_NOTE by MasterAgent.
 * Focuses purely on execution: extracting topic, generating content, and creating the file.
 */
interface CreateNoteAgent {
    /**
     * Executes note creation for the given message.
     * 
     * @param message The user's message (assumed to be a note creation request)
     * @param history Conversation history
     * @param context Agent context
     * @return AgentResult.NoteCreated on success
     * @throws Exception if execution fails (vault not open, file write fails, etc.)
     */
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}

/**
 * Implementation of CreateNoteAgent that creates markdown notes.
 * 
 * Extracts topic from message, generates content using LLM, and creates the file.
 */
class CreateNoteAgentImpl(
    private val llamaClient: LlamaClient,
    private val fileSystem: FileSystem,
    private val settingsRepository: SettingsRepository
) : CreateNoteAgent {

    companion object {
        private const val PREVIEW_LENGTH = 200
    }

    override suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult {
        val modelName = when (context.settings.llm.provider) {
            org.krypton.LlmProvider.OLLAMA -> context.settings.llm.ollamaModel
            org.krypton.LlmProvider.GEMINI -> context.settings.llm.geminiModel
        }
        AppLogger.i("CreateNoteAgent", "Executing note creation - message: \"$message\", model: $modelName")
        
        // Check if vault is open
        var vaultPath = context.currentVaultPath
        if (vaultPath.isNullOrBlank()) {
            throw IllegalStateException("No vault open. Please open a vault to create notes.")
        }

        // Handle "default" vault ID on Android - resolve to actual vault path
        if (vaultPath == "default") {
            // Try to get vault root from settings
            val vaultRootUri = context.settings.app.vaultRootUri
            if (vaultRootUri != null && vaultRootUri.isNotBlank()) {
                vaultPath = vaultRootUri
            } else {
                // Fallback: use default Documents/Vault directory
                // This matches AndroidVaultPicker.pickVaultRoot() fallback behavior
                // Note: This is Android-specific, but CreateNoteAgent is common code
                // The actual path resolution should ideally be done in platform-specific code,
                // but for now we handle it here to avoid breaking the agent interface
                throw IllegalStateException(
                    "No vault is open. Please open a vault folder to create notes. " +
                    "The 'default' vault ID cannot be used for note creation."
                )
            }
        }

        // Validate vault path exists and is a directory
        if (!fileSystem.isDirectory(vaultPath)) {
            throw IllegalStateException("Vault path is not a directory: $vaultPath")
        }

        // Extract topic from message (assume intent already classified, so extract directly)
        val topic = extractTopic(message) ?: throw IllegalArgumentException(
            "Could not extract topic from message: $message"
        )

        AppLogger.i("CreateNoteAgent", "Extracted topic: $topic")

        // Generate content
        val content = generateNoteContent(topic)
        if (content.isBlank()) {
            throw IllegalStateException("Generated content is empty for topic: $topic")
        }

        // Generate filename
        val fileName = generateFileName(topic, vaultPath)
        val filePath = "$vaultPath/$fileName"

        // Write file
        val success = fileSystem.writeFile(filePath, content)
        if (!success) {
            throw IllegalStateException("Failed to write file: $filePath")
        }

        // Extract title and preview
        val title = extractTitle(content, topic)
        val preview = extractPreview(content)

        AppLogger.i("CreateNoteAgent", "Created note: $filePath")

        return AgentResult.NoteCreated(
            filePath = filePath,
            title = title,
            preview = preview
        )
    }

    /**
     * Extracts the topic from a message.
     * Assumes message is a note creation request and extracts the topic directly.
     */
    private fun extractTopic(message: String): String? {
        // Try common patterns, but be flexible since intent is already classified
        val lowerMessage = message.lowercase().trim()
        
        val patterns = listOf(
            "create a note on",
            "create a short note on",
            "create note on",
            "make a note about",
            "make a note on",
            "write a note about",
            "write a note on",
            "create a note about",
            "note on",
            "note about",
            "create",
            "make",
            "write"
        )
        
        for (pattern in patterns) {
            if (lowerMessage.contains(pattern)) {
                val index = lowerMessage.indexOf(pattern)
                val afterPattern = message.substring(index + pattern.length).trim()
                if (afterPattern.isNotBlank()) {
                    return afterPattern
                }
            }
        }
        
        // If no pattern matches, return the whole message as topic
        return message.trim().takeIf { it.isNotBlank() }
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
