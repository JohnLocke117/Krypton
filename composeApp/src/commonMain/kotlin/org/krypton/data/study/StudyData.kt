package org.krypton.data.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.study.StudyGoal
import org.krypton.core.domain.study.StudyItem

/**
 * Container for all study data in a vault.
 * Serialized to/from JSON for persistence.
 * 
 * @param goals List of all study goals for this vault
 * @param items List of all study items across all goals
 */
@Serializable
data class StudyData(
    val goals: List<StudyGoal> = emptyList(),
    val items: List<StudyItem> = emptyList(),
)

