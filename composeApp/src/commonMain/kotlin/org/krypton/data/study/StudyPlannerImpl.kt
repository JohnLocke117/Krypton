package org.krypton.data.study

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import org.krypton.chat.agent.SearchNoteAgent
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.core.domain.study.*
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.platform.VaultRoot
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider

/**
 * Implementation of StudyPlanner that uses SearchNoteAgent to find relevant notes and generates flashcards.
 */
class StudyPlannerImpl(
    private val fileSystem: FileSystem,
    private val flashcardService: FlashcardService,
    private val studyItemRepository: StudyItemRepository,
    private val timeProvider: TimeProvider,
    private val searchNoteAgent: SearchNoteAgent,
    private val settingsRepository: SettingsRepository,
) : StudyPlanner {
    
    override suspend fun planForGoal(goal: StudyGoal) = withContext(Dispatchers.Default) {
        try {
            AppLogger.i("StudyPlanner", "Planning for goal: ${goal.title} (vault: ${goal.vaultId})")
            
            // Use SearchNoteAgent to find notes related to the goal title
            val vaultPath = goal.vaultId
            if (vaultPath.isBlank()) {
                AppLogger.w("StudyPlanner", "Vault path is empty for goal: ${goal.title}")
                return@withContext
            }
            
            val settings = settingsRepository.settingsFlow.value
            val agentContext = AgentContext(
                currentVaultPath = vaultPath,
                settings = settings,
                currentNotePath = null
            )
            
            // Search for notes related to the goal title
            val searchMessage = "search my notes for ${goal.title}"
            val searchResult = try {
                searchNoteAgent.execute(searchMessage, emptyList(), agentContext)
            } catch (e: Exception) {
                AppLogger.w("StudyPlanner", "SearchNoteAgent execution failed for '${goal.title}'", e)
                null
            }
            
            val notePaths = if (searchResult is AgentResult.NotesFound) {
                AppLogger.i("StudyPlanner", "Found ${searchResult.results.size} notes related to '${goal.title}'")
                searchResult.results.map { it.filePath }
            } else {
                AppLogger.w("StudyPlanner", "SearchNoteAgent did not find notes for '${goal.title}', falling back to all notes")
                // Fallback to all notes if search fails
                val vaultRoot = VaultRoot(id = vaultPath, displayName = vaultPath)
                fileSystem.listNotes(vaultRoot).map { it.path }
            }
            
            AppLogger.i("StudyPlanner", "Processing ${notePaths.size} notes for goal: ${goal.title}")
            
            val studyItems = mutableListOf<StudyItem>()
            val now = timeProvider.currentTimeMillis()
            
            for (notePath in notePaths) {
                try {
                    val fullNotePath = if (notePath.startsWith(vaultPath)) {
                        notePath
                    } else {
                        "$vaultPath/$notePath"
                    }
                    
                    // Generate flashcards for this note
                    val flashcards = flashcardService.generateFromNote(
                        notePath = fullNotePath,
                        maxCards = 10
                    )
                    
                    AppLogger.d("StudyPlanner", "Generated ${flashcards.size} flashcards for note: $notePath")
                    
                    // Create study items for each flashcard
                    flashcards.forEach { flashcard ->
                        val itemId = StudyItemId("${goal.id.value}-flashcard-$notePath-${flashcard.question.hashCode()}")
                        val studyItem = StudyItem(
                            id = itemId,
                            goalId = goal.id,
                            referenceId = "$notePath:${flashcard.question.hashCode()}",
                            type = StudyItemType.FLASHCARD,
                            difficulty = 3, // Default medium difficulty
                            lastReviewedAtEpochMillis = null,
                            nextDueAtEpochMillis = now, // Due immediately for new items
                        )
                        studyItems.add(studyItem)
                    }
                    
                    // Also create a study item for the note itself
                    val noteItemId = StudyItemId("${goal.id.value}-note-$notePath")
                    val noteItem = StudyItem(
                        id = noteItemId,
                        goalId = goal.id,
                        referenceId = notePath,
                        type = StudyItemType.NOTE,
                        difficulty = 3,
                        lastReviewedAtEpochMillis = null,
                        nextDueAtEpochMillis = now,
                    )
                    studyItems.add(noteItem)
                    
                } catch (e: Exception) {
                    AppLogger.w("StudyPlanner", "Failed to process note: $notePath", e)
                }
            }
            
            // Save all study items
            studyItemRepository.upsertItems(studyItems)
            
            AppLogger.i("StudyPlanner", "Created ${studyItems.size} study items for goal: ${goal.title}")
            
        } catch (e: Exception) {
            AppLogger.e("StudyPlanner", "Failed to plan for goal: ${goal.title}", e)
        }
    }
}

