package org.krypton.krypton.di

import org.krypton.krypton.chat.ChatService
import org.krypton.krypton.data.chat.impl.ChatServiceFactory
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.rag.RagComponents
import org.koin.dsl.module

/**
 * Chat dependency injection module.
 * 
 * Provides chat service with optional RAG support.
 */
val chatModule = module {
    single<ChatService> {
        // Try to get RAG components, but it's optional (may be null if initialization failed)
        val ragComponents: RagComponents? = try {
            get<RagComponents>()
        } catch (e: org.koin.core.error.NoBeanDefFoundException) {
            null
        } catch (e: Exception) {
            null
        }
        val settingsRepository: SettingsRepository = get()
        val ragSettings = settingsRepository.settingsFlow.value.rag
        
        ChatServiceFactory.createChatService(
            ragComponents = ragComponents,
            ragSettings = ragSettings
        )
    }
}

