package org.krypton.core.domain.study

/**
 * Represents an active study session with items due for review.
 * 
 * @param goal The study goal for this session
 * @param itemsDue List of items due for review in this session
 */
data class StudySession(
    val goal: StudyGoal,
    val itemsDue: List<StudyItem>,
)

