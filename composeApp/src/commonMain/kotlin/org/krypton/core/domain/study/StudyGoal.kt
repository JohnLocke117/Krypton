package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Unique identifier for a study goal.
 */
@Serializable
data class StudyGoalId(val value: String)

/**
 * Represents a study goal for a vault.
 * 
 * @param id Unique identifier for this goal
 * @param vaultId ID of the vault this goal belongs to
 * @param title Title of the study goal
 * @param description Optional description
 * @param topics List of topics to study (each topic becomes a session)
 * @param matchedNotes List of note paths found during goal creation via SearchAgent
 * @param roadmap Brief roadmap/summary generated for this goal (1-2 paragraphs)
 * @param status Current status of the goal
 * @param targetDate Optional target completion date (ISO-8601 string)
 * @param createdAtMillis Creation timestamp in milliseconds
 * @param updatedAtMillis Last update timestamp in milliseconds
 */
@Serializable
data class StudyGoal(
    val id: StudyGoalId,
    val vaultId: String,
    val title: String,
    val description: String?,
    val topics: List<String>,
    val matchedNotes: List<String> = emptyList(),
    val roadmap: String? = null,
    val status: GoalStatus,
    val targetDate: String?, // ISO-8601 date string (YYYY-MM-DD)
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

