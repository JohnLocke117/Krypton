package org.krypton.di

import org.krypton.core.domain.study.StudyGoalRepository
import org.krypton.core.domain.study.StudyItemRepository
import org.krypton.core.domain.study.StudyPlanner
import org.krypton.core.domain.study.StudyScheduler
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
    single<StudyItemRepository> { StudyItemRepositoryImpl(get(), get()) }
    
    // Domain services
    single<StudyPlanner> {
        StudyPlannerImpl(
            fileSystem = get(),
            flashcardService = get(),
            studyItemRepository = get(),
            timeProvider = get(),
            searchNoteAgent = get(),
            settingsRepository = get()
        )
    }
    
    single<StudyScheduler> {
        StudySchedulerImpl(
            studyGoalRepository = get(),
            studyItemRepository = get()
        )
    }
    
    // UI state holder
    single<StudyModeState> {
        StudyModeStateImpl(
            studyGoalRepository = get(),
            studyPlanner = get(),
            studyScheduler = get(),
            studyItemRepository = get(),
            timeProvider = get(),
            coroutineScope = get()
        )
    }
}

