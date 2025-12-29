package org.krypton.core.domain.flashcard

/**
 * Service for generating flashcards from notes.
 */
interface FlashcardService {
    /**
     * Generates flashcards from a note file.
     * 
     * @param notePath The path to the note file
     * @param maxCards Maximum number of flashcards to generate (default: 20)
     * @return List of generated flashcards
     */
    suspend fun generateFromNote(
        notePath: String,
        maxCards: Int = 20,
    ): List<Flashcard>
}

