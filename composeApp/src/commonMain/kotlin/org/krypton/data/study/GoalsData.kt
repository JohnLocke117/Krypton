package org.krypton.data.study

import kotlinx.serialization.Serializable
import org.krypton.core.domain.study.StudyGoal

/**
 * Container for all study goals in a vault.
 * Serialized to/from JSON for persistence.
 * Stored in `.krypton/goals.json`.
 */
@Serializable
data class GoalsData(
    val goals: List<StudyGoal> = emptyList(),
)

