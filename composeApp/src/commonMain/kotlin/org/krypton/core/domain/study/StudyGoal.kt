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
    val targetDate: String?, // ISO-8601 date string (YYYY-MM-DD)
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

