package org.krypton.data.study

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.krypton.chat.agent.AgentContext
import org.krypton.chat.agent.AgentResult
import org.krypton.chat.agent.SummarizeNoteAgent
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.core.domain.study.*
import org.krypton.data.repository.SettingsRepository
import org.krypton.util.AppLogger
import org.krypton.util.TimeProvider
import kotlin.math.round

/**
 * Implementation of StudyRunner that prepares sessions and runs quizzes.
 */
class StudyRunnerImpl(
    private val sessionRepository: StudySessionRepository,
    private val cacheRepository: StudyCacheRepository,
    private val goalRepository: org.krypton.core.domain.study.StudyGoalRepository,
    private val summarizeNoteAgent: SummarizeNoteAgent,
    private val flashcardService: FlashcardService,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : StudyRunner {
    
    override suspend fun prepareSession(sessionId: StudySessionId) = withContext(Dispatchers.Default) {
        try {
            val session = sessionRepository.getSession(sessionId)
                ?: throw IllegalArgumentException("Session not found: $sessionId")
            
            val goal = goalRepository.getGoal(session.goalId)
                ?: throw IllegalArgumentException("Goal not found: ${session.goalId}")
            
            AppLogger.i("StudyRunner", "Preparing session: ${session.topic} (${session.notePaths.size} notes)")
            
            val settings = settingsRepository.settingsFlow.value
            val agentContext = AgentContext(
                currentVaultPath = goal.vaultId,
                settings = settings,
                currentNotePath = null
            )
            
            // Generate summaries for each note if not cached
            for (notePath in session.notePaths) {
                val fullNotePath = if (notePath.startsWith(goal.vaultId)) {
                    notePath
                } else {
                    "${goal.vaultId}/$notePath"
                }
                
                val cachedSummary = cacheRepository.getNoteSummary(fullNotePath)
                if (cachedSummary == null) {
                    try {
                        AppLogger.d("StudyRunner", "Generating summary for note: $fullNotePath")
                        
                        // Create context with note path for SummarizeNoteAgent
                        val noteContext = agentContext.copy(currentNotePath = fullNotePath)
                        val summaryResult = summarizeNoteAgent.execute(
                            message = "summarize this note",
                            history = emptyList(),
                            context = noteContext
                        )
                        
                        if (summaryResult is AgentResult.NoteSummarized) {
                            val summary = NoteSummary(
                                notePath = fullNotePath,
                                summary = summaryResult.summary,
                                generatedAtMillis = timeProvider.currentTimeMillis()
                            )
                            cacheRepository.saveNoteSummary(summary)
                            AppLogger.d("StudyRunner", "Generated and cached summary for: $fullNotePath")
                        } else {
                            AppLogger.w("StudyRunner", "Unexpected result type from SummarizeNoteAgent")
                        }
                    } catch (e: Exception) {
                        AppLogger.w("StudyRunner", "Failed to generate summary for note: $fullNotePath", e)
                    }
                } else {
                    AppLogger.d("StudyRunner", "Using cached summary for: $fullNotePath")
                }
            }
            
            // Generate flashcards for session if not cached
            val cachedFlashcards = cacheRepository.getSessionFlashcards(sessionId)
            if (cachedFlashcards == null) {
                try {
                    AppLogger.d("StudyRunner", "Generating flashcards for session: ${session.topic}")
                    
                    val allFlashcards = mutableListOf<org.krypton.core.domain.flashcard.Flashcard>()
                    
                    // Generate flashcards for each note in the session
                    for (notePath in session.notePaths) {
                        val fullNotePath = if (notePath.startsWith(goal.vaultId)) {
                            notePath
                        } else {
                            "${goal.vaultId}/$notePath"
                        }
                        
                        try {
                            val settings = settingsRepository.settingsFlow.value
                            val flashcards = flashcardService.generateFromNote(
                                notePath = fullNotePath,
                                maxCards = settings.study.maxFlashcardsPerNote
                            )
                            allFlashcards.addAll(flashcards)
                            AppLogger.d("StudyRunner", "Generated ${flashcards.size} flashcards for: $fullNotePath")
                        } catch (e: Exception) {
                            AppLogger.w("StudyRunner", "Failed to generate flashcards for note: $fullNotePath", e)
                        }
                    }
                    
                    if (allFlashcards.isNotEmpty()) {
                        val sessionFlashcards = SessionFlashcards(
                            sessionId = sessionId,
                            flashcards = allFlashcards,
                            generatedAtMillis = timeProvider.currentTimeMillis()
                        )
                        cacheRepository.saveSessionFlashcards(sessionFlashcards)
                        AppLogger.i("StudyRunner", "Generated and cached ${allFlashcards.size} flashcards for session")
                    } else {
                        AppLogger.w("StudyRunner", "No flashcards generated for session: ${session.topic}")
                    }
                } catch (e: Exception) {
                    AppLogger.e("StudyRunner", "Failed to generate flashcards for session: ${session.topic}", e)
                }
            } else {
                AppLogger.d("StudyRunner", "Using cached flashcards for session: ${session.topic}")
            }
            
            AppLogger.i("StudyRunner", "Session prepared: ${session.topic}")
        } catch (e: Exception) {
            AppLogger.e("StudyRunner", "Failed to prepare session: $sessionId", e)
            throw e
        }
    }
    
    override suspend fun runQuiz(
        sessionId: StudySessionId,
        flashcardCount: Int,
        answers: Map<Int, Boolean>
    ): SessionResult = withContext(Dispatchers.Default) {
        val session = sessionRepository.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        val flashcards = cacheRepository.getSessionFlashcards(sessionId)
            ?: throw IllegalStateException("Flashcards not found for session: $sessionId")
        
        // Select flashcards (take first flashcardCount, or all if less)
        val selectedFlashcards = flashcards.flashcards.take(flashcardCount)
        
        if (selectedFlashcards.isEmpty()) {
            throw IllegalStateException("No flashcards available for quiz")
        }
        
        // Calculate score
        val totalQuestions = answers.size
        val correctAnswers = answers.values.count { it }
        val score = if (totalQuestions > 0) {
            round(10.0 * correctAnswers / totalQuestions).toInt()
        } else {
            0
        }
        
        AppLogger.i("StudyRunner", "Quiz completed for session: ${session.topic}, score: $score/10 ($correctAnswers/$totalQuestions)")
        
        SessionResult(
            sessionId = sessionId,
            score = score,
            totalQuestions = totalQuestions,
            correctAnswers = correctAnswers,
            completedAtMillis = timeProvider.currentTimeMillis()
        )
    }
    
    override suspend fun completeSession(sessionId: StudySessionId, result: SessionResult) = withContext(Dispatchers.Default) {
        try {
            val session = sessionRepository.getSession(sessionId)
                ?: throw IllegalArgumentException("Session not found: $sessionId")
            
            // Save result
            cacheRepository.saveSessionResult(result)
            
            // Update session status if score >= 7
            if (result.score >= 7) {
                sessionRepository.updateSessionStatus(sessionId, SessionStatus.COMPLETED)
                AppLogger.i("StudyRunner", "Session completed: ${session.topic} (score: ${result.score}/10)")
                
                // Check if all sessions for goal are completed
                val goal = goalRepository.getGoal(session.goalId)
                    ?: throw IllegalArgumentException("Goal not found: ${session.goalId}")
                
                val allSessions = sessionRepository.observeSessionsForGoal(session.goalId)
                    .first()
                
                val allCompleted = allSessions.all { it.status == SessionStatus.COMPLETED }
                
                if (allCompleted) {
                    // Update goal status to COMPLETED
                    val updatedGoal = goal.copy(
                        status = GoalStatus.COMPLETED,
                        updatedAtMillis = timeProvider.currentTimeMillis()
                    )
                    goalRepository.upsert(updatedGoal)
                    AppLogger.i("StudyRunner", "Goal completed: ${goal.title}")
                }
            } else {
                AppLogger.i("StudyRunner", "Session not completed: ${session.topic} (score: ${result.score}/10, need ≥7)")
            }
        } catch (e: Exception) {
            AppLogger.e("StudyRunner", "Failed to complete session: $sessionId", e)
            throw e
        }
    }
}

