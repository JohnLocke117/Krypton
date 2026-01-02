package org.krypton.di

import org.krypton.core.domain.study.StudyGoalRepository
import org.krypton.core.domain.study.StudySessionRepository
import org.krypton.core.domain.study.StudyCacheRepository
import org.krypton.core.domain.study.StudyPlanner
import org.krypton.core.domain.study.StudyRunner
import org.krypton.data.study.*
import org.krypton.ui.study.StudyModeState
import org.krypton.ui.study.StudyModeStateImpl
import org.krypton.util.TimeProvider
import org.krypton.util.createTimeProvider
import org.koin.dsl.module

/**
 * Study module for dependency injection.
 * Provides study-related repositories, services, and state holders.
 */
val studyModule = module {
    // TimeProvider (platform-specific)
    single<TimeProvider> { createTimeProvider() }
    
    // Repositories
    single<StudyGoalRepository> { StudyGoalRepositoryImpl(get()) }
    single<StudySessionRepository> { StudySessionRepositoryImpl(get(), get()) }
    single<StudyCacheRepository> { StudyCacheRepositoryImpl(get(), get(), get()) }
    
    // Domain services
    single<StudyPlanner> {
        StudyPlannerImpl(
            fileSystem = get(),
            timeProvider = get(),
            searchNoteAgent = get(),
            settingsRepository = get(),
            sessionRepository = get(),
            llamaClient = get(),
            persistence = get()
        )
    }
    
    single<StudyRunner> {
        StudyRunnerImpl(
            sessionRepository = get(),
            cacheRepository = get(),
            goalRepository = get(),
            summarizeNoteAgent = get(),
            flashcardService = get(),
            settingsRepository = get(),
            timeProvider = get()
        )
    }
    
    // UI state holder
    single<StudyModeState> {
        StudyModeStateImpl(
            studyGoalRepository = get(),
            studySessionRepository = get(),
            studyCacheRepository = get(),
            studyPlanner = get(),
            studyRunner = get(),
            searchNoteAgent = get(),
            settingsRepository = get(),
            timeProvider = get(),
            coroutineScope = get(),
            persistence = get()
        )
    }
}

