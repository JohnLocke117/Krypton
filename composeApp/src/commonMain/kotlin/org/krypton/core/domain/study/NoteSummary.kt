package org.krypton.core.domain.study

import kotlinx.serialization.Serializable

/**
 * Cached summary for a note.
 * 
 * @param notePath Path to the note file
 * @param summary Generated summary text
 * @param generatedAtMillis Timestamp when summary was generated
 */
@Serializable
data class NoteSummary(
    val notePath: String,
    val summary: String,
    val generatedAtMillis: Long,
)

