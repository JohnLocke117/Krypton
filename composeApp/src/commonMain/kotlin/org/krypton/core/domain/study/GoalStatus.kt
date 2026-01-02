package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Status of a study goal.
 */
@Serializable
enum class GoalStatus {
    /** Goal created but not started */
    PENDING,
    
    /** Goal has at least one session in progress */
    IN_PROGRESS,
    
    /** All sessions for the goal are completed */
    COMPLETED
}

