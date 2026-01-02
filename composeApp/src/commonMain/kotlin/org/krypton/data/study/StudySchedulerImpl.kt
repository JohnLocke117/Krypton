package org.krypton.data.study

import org.krypton.core.domain.study.*
import org.krypton.util.AppLogger

/**
 * Implementation of StudyScheduler with simple spaced repetition scheduling.
 */
class StudySchedulerImpl(
    private val studyGoalRepository: StudyGoalRepository,
    private val studyItemRepository: StudyItemRepository,
) : StudyScheduler {
    
    override suspend fun getTodaySession(goalId: StudyGoalId, nowEpochMillis: Long): StudySession {
        try {
            val goal = studyGoalRepository.getGoal(goalId)
                ?: throw IllegalArgumentException("Goal not found: $goalId")
            
            val dueItems = studyItemRepository.getItemsDueNow(goalId, nowEpochMillis)
            
            AppLogger.i("StudyScheduler", "Found ${dueItems.size} items due for goal: ${goal.title}")
            
            return StudySession(
                goal = goal,
                itemsDue = dueItems,
            )
        } catch (e: Exception) {
            AppLogger.e("StudyScheduler", "Failed to get today's session for goal: $goalId", e)
            throw e
        }
    }
    
    override suspend fun registerReviewResult(
        itemId: StudyItemId,
        nowEpochMillis: Long,
        rating: ReviewRating,
    ) {
        try {
            val item = studyItemRepository.getItem(itemId)
                ?: throw IllegalArgumentException("Item not found: $itemId")
            
            val updatedItem = item.adjustSchedule(nowEpochMillis, rating)
            studyItemRepository.updateItem(updatedItem)
            
            AppLogger.d(
                "StudyScheduler",
                "Updated item ${item.id} with rating $rating, next due: ${updatedItem.nextDueAtEpochMillis}"
            )
        } catch (e: Exception) {
            AppLogger.e("StudyScheduler", "Failed to register review result for item: $itemId", e)
            throw e
        }
    }
}

