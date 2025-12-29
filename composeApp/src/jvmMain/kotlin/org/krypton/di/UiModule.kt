package org.krypton.di

import kotlinx.coroutines.CoroutineScope
import org.krypton.chat.ChatService
import org.krypton.core.domain.editor.EditorDomain
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.core.domain.search.PatternMatcher
import org.krypton.core.domain.search.SearchDomain
import org.krypton.core.domain.search.JvmPatternMatcher
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.koin.dsl.module

/**
 * UI layer dependency injection module.
 * 
 * Provides state holders and view models for UI components.
 */
val uiModule = module {
    // Domain layer
    single<EditorDomain> { EditorDomain() }
    
    single<PatternMatcher> { JvmPatternMatcher() }
    
    single<SearchDomain> { SearchDomain(get()) }
    
    // State holders
    single<EditorStateHolder> {
        EditorStateHolder(
            editorDomain = get(),
            fileSystem = get(),
            coroutineScope = get(),
            settingsRepository = get(),
            flashcardService = try {
                get<FlashcardService>()
            } catch (e: Exception) {
                null
            }
        )
    }
    
    single<SearchStateHolder> {
        SearchStateHolder(
            searchDomain = get()
        )
    }
    
    single<ChatStateHolder> {
        ChatStateHolder(
            chatService = get(),
            coroutineScope = get()
        )
    }
}

