package org.krypton.core.domain.study

/**
 * Adjusts the schedule for a study item based on review rating.
 * Implements a simple spaced repetition algorithm with fixed intervals.
 * 
 * Intervals:
 * - AGAIN: 1 hour
 * - HARD: 1 day
 * - EASY: 3 days
 * 
 * @param now Current time in milliseconds
 * @param rating User's rating of the review
 * @return Updated study item with new schedule
 */
fun StudyItem.adjustSchedule(now: Long, rating: ReviewRating): StudyItem {
    val intervalMillis = when (rating) {
        ReviewRating.AGAIN -> 1 * 60 * 60 * 1000L // 1 hour
        ReviewRating.HARD -> 24 * 60 * 60 * 1000L // 1 day
        ReviewRating.EASY -> 3 * 24 * 60 * 60 * 1000L // 3 days
    }
    
    return copy(
        lastReviewedAtEpochMillis = now,
        nextDueAtEpochMillis = now + intervalMillis,
    )
}

