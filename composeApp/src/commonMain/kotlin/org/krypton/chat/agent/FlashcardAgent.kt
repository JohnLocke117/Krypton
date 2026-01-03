package org.krypton.chat.agent

import org.krypton.chat.ChatMessage
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.data.files.FileSystem
import org.krypton.util.AppLogger

/**
 * Interface for generating flashcards from notes.
 * 
 * Assumes intent has already been classified as GENERATE_FLASHCARDS by MasterAgent.
 * Focuses purely on execution: extracting note path, generating flashcards, and returning results.
 */
interface FlashcardAgent {
    /**
     * Executes flashcard generation for the given message.
     * 
     * @param message The user's message (assumed to be a flashcard generation request)
     * @param history Conversation history
     * @param context Agent context
     * @return AgentResult.FlashcardsGenerated on success
     * @throws Exception if execution fails (note not found, generation fails, etc.)
     */
    suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult
}

/**
 * Implementation of FlashcardAgent that generates flashcards from notes.
 * 
 * Extracts note path from message or uses current note, then generates flashcards using FlashcardService.
 */
class FlashcardAgentImpl(
    private val flashcardService: FlashcardService,
    private val fileSystem: FileSystem
) : FlashcardAgent {

    companion object {
        private const val DEFAULT_MAX_CARDS = 20
        private val NOTE_PATH_PATTERNS = listOf(
            Regex("""(?:from|for|of)\s+(?:the\s+)?(?:note\s+)?["']?([^"'\s]+(?:\.md)?)["']?""", RegexOption.IGNORE_CASE),
            Regex("""(?:note|file)\s+["']?([^"'\s]+(?:\.md)?)["']?""", RegexOption.IGNORE_CASE),
            Regex("""["']([^"']+\.md)["']""", RegexOption.IGNORE_CASE)
        )
        private val MAX_CARDS_PATTERN = Regex("""(?:max|maximum|limit|up\s+to)\s+(\d+)\s*(?:cards?|flashcards?)?""", RegexOption.IGNORE_CASE)
    }

    override suspend fun execute(
        message: String,
        history: List<ChatMessage>,
        context: AgentContext
    ): AgentResult {
        AppLogger.i("FlashcardAgent", "Executing flashcard generation - message: \"$message\"")
        
        // Extract note path from message or use current note
        val notePath = extractNotePath(message, context)
            ?: throw IllegalStateException("No note specified. Please specify a note path or open a note.")
        
        // Validate note exists
        if (!fileSystem.isFile(notePath)) {
            throw IllegalStateException("Note file not found: $notePath")
        }
        
        // Extract maxCards from message (default: 20)
        val maxCards = extractMaxCards(message) ?: DEFAULT_MAX_CARDS
        
        AppLogger.d("FlashcardAgent", "Generating flashcards from note: $notePath, maxCards: $maxCards")
        
        // Generate flashcards
        val flashcards = flashcardService.generateFromNote(notePath, maxCards)
        
        if (flashcards.isEmpty()) {
            throw IllegalStateException("No flashcards were generated from the note. The note may be too short or not contain suitable content.")
        }
        
        AppLogger.i("FlashcardAgent", "Generated ${flashcards.size} flashcards from note: $notePath")
        
        return AgentResult.FlashcardsGenerated(
            flashcards = flashcards,
            notePath = notePath,
            count = flashcards.size
        )
    }
    
    /**
     * Extracts note path from message or uses current note from context.
     */
    private fun extractNotePath(message: String, context: AgentContext): String? {
        // Try to extract from message first
        for (pattern in NOTE_PATH_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                val extractedPath = match.groupValues[1]
                AppLogger.d("FlashcardAgent", "Extracted note path from message: $extractedPath")
                return extractedPath
            }
        }
        
        // Fall back to current note from context
        val currentNotePath = context.currentNotePath
        if (currentNotePath != null) {
            AppLogger.d("FlashcardAgent", "Using current note from context: $currentNotePath")
            return currentNotePath
        }
        
        return null
    }
    
    /**
     * Extracts maxCards parameter from message.
     */
    private fun extractMaxCards(message: String): Int? {
        val match = MAX_CARDS_PATTERN.find(message)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}

