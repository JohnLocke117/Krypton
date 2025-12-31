package org.krypton.di

import kotlinx.coroutines.CoroutineScope
import org.krypton.chat.ChatService
import org.krypton.core.domain.editor.EditorDomain
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.core.domain.search.PatternMatcher
import org.krypton.core.domain.search.SearchDomain
import org.krypton.core.domain.search.AndroidPatternMatcher
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.koin.dsl.module

/**
 * UI layer dependency injection module for Android.
 * 
 * Provides Android-specific UI bindings, extending commonUiModule.
 * 
 * Note: For now, we use a simple PatternMatcher implementation.
 * A full Android implementation might use platform-specific search.
 */
val androidUiModule = module {
    // Simple pattern matcher for Android (can be enhanced later)
    single<PatternMatcher> { 
        AndroidPatternMatcher()
    }
}

