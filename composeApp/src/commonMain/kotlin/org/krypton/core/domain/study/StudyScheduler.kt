package org.krypton.core.domain.study

/**
 * Service for scheduling study sessions and managing review intervals.
 */
interface StudyScheduler {
    /**
     * Returns a study session with items due for review now.
     * 
     * @param goalId The goal to get session for
     * @param nowEpochMillis Current time in milliseconds
     * @return Study session with due items
     */
    suspend fun getTodaySession(goalId: StudyGoalId, nowEpochMillis: Long): StudySession
    
    /**
     * Update a study item after user feedback.
     * Adjusts the next review time based on the rating.
     * 
     * @param itemId ID of the item reviewed
     * @param nowEpochMillis Current time in milliseconds
     * @param rating User's rating of the review
     */
    suspend fun registerReviewResult(
        itemId: StudyItemId,
        nowEpochMillis: Long,
        rating: ReviewRating,
    )
}

