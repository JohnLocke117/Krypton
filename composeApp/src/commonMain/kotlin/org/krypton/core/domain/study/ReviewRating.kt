package org.krypton.core.domain.study

/**
 * Rating given by user after reviewing a study item.
 */
enum class ReviewRating {
    /** Item needs to be reviewed again soon (forgot or incorrect) */
    AGAIN,
    
    /** Item was difficult to recall */
    HARD,
    
    /** Item was easy to recall */
    EASY
}

