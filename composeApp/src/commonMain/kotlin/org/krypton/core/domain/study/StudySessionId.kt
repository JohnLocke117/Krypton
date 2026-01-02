package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Unique identifier for a study session.
 */
@Serializable
data class StudySessionId(val value: String)

