package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Unique identifier for a study item.
 */
@Serializable
data class StudyItemId(val value: String)

/**
 * Type of study item.
 */
@Serializable
enum class StudyItemType {
    /** Flashcard item */
    FLASHCARD,
    
    /** Note/document item */
    NOTE
}

/**
 * Represents a single item in a study plan (flashcard or note).
 * 
 * @param id Unique identifier
 * @param goalId Goal this item belongs to
 * @param referenceId ID of the underlying flashcard or note
 * @param type Type of item (flashcard or note)
 * @param difficulty Difficulty level (1-5)
 * @param lastReviewedAtEpochMillis Last review timestamp, null if never reviewed
 * @param nextDueAtEpochMillis When this item is next due for review
 */
@Serializable
data class StudyItem(
    val id: StudyItemId,
    val goalId: StudyGoalId,
    val referenceId: String,
    val type: StudyItemType,
    val difficulty: Int,
    val lastReviewedAtEpochMillis: Long?,
    val nextDueAtEpochMillis: Long,
)

