package org.krypton.di

import kotlinx.coroutines.CoroutineScope
import org.krypton.chat.ChatService
import org.krypton.chat.conversation.ConversationRepository
import org.krypton.core.domain.editor.EditorDomain
import org.krypton.core.domain.flashcard.FlashcardService
import org.krypton.core.domain.search.SearchDomain
import org.krypton.data.files.FileSystem
import org.krypton.data.repository.SettingsRepository
import org.krypton.ui.state.ChatStateHolder
import org.krypton.ui.state.EditorStateHolder
import org.krypton.ui.state.SearchStateHolder
import org.koin.dsl.module

/**
 * Common UI layer dependency injection module.
 * 
 * Provides state holders and view models for UI components.
 * This module contains platform-agnostic bindings.
 * 
 * Platform-specific modules should extend this module and add
 * platform-specific bindings (e.g., PatternMatcher).
 */
val commonUiModule = module {
    // Domain layer
    single<EditorDomain> { EditorDomain() }
    
    // Note: PatternMatcher binding is platform-specific and should be provided
    // by platform modules (jvmMain or androidMain)
    
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
        // Try to get ConversationRepository, but make it optional
        // It may not be available in all contexts or may fail to initialize
        val conversationRepository: ConversationRepository? = try {
            get<ConversationRepository>()
        } catch (e: org.koin.core.error.NoBeanDefFoundException) {
            null // Optional - may not be available in all contexts
        } catch (e: org.koin.core.error.InstanceCreationException) {
            // Failed to create instance - this can happen if AndroidConversationPersistence
            // fails to initialize (e.g., context issues). Make it optional.
            null
        } catch (e: Exception) {
            null // Optional - may not be available in all contexts
        }
        
        ChatStateHolder(
            chatService = get(),
            conversationRepository = conversationRepository,
            coroutineScope = get()
        )
    }
}

