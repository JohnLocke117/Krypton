package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Status of a study session.
 */
@Serializable
enum class SessionStatus {
    /** Session created but not started */
    PENDING,
    
    /** Session completed (quiz passed with score ≥ 7/10) */
    COMPLETED
}

