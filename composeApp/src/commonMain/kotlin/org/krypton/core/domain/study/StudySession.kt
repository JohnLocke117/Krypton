package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Represents a study session for a specific topic within a goal.
 * 
 * @param id Unique identifier for this session
 * @param goalId ID of the goal this session belongs to
 * @param topic The topic name for this session
 * @param notePaths List of note file paths found for this topic
 * @param status Current status of the session
 * @param order Order of this session within the goal (1-based)
 * @param createdAtMillis Creation timestamp in milliseconds
 * @param completedAtMillis Completion timestamp in milliseconds, null if not completed
 */
@Serializable
data class StudySession(
    val id: StudySessionId,
    val goalId: StudyGoalId,
    val topic: String,
    val notePaths: List<String>,
    val status: SessionStatus,
    val order: Int,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null,
)
