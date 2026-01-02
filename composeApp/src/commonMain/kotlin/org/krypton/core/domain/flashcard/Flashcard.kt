package org.krypton.core.domain.flashcard

import kotlinx.serialization.Serializable

/**
 * Represents a single flashcard with a question and answer.
 * 
 * @param question The question prompt for the flashcard
 * @param answer The answer to the question
 * @param sourceFile The path to the source file from which this flashcard was generated (optional)
 */
@Serializable
data class Flashcard(
    val question: String,
    val answer: String,
    val sourceFile: String? = null,
)

