package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Result of a quiz session.
 * 
 * @param sessionId ID of the session this result belongs to
 * @param score Score out of 10 (0-10)
 * @param totalQuestions Total number of questions in the quiz
 * @param correctAnswers Number of correct answers
 * @param completedAtMillis Timestamp when quiz was completed
 */
@Serializable
data class SessionResult(
    val sessionId: StudySessionId,
    val score: Int, // 0-10
    val totalQuestions: Int,
    val correctAnswers: Int,
    val completedAtMillis: Long,
)

