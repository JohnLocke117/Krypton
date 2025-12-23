package org.krypton.krypton.di

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.krypton.FileManager
import org.krypton.krypton.data.files.FileSystem
import org.krypton.krypton.data.files.impl.JvmFileSystem
import org.krypton.krypton.data.repository.SettingsPersistence
import org.krypton.krypton.data.repository.SettingsRepository
import org.krypton.krypton.data.repository.impl.JvmSettingsPersistence
import org.krypton.krypton.data.repository.impl.SettingsRepositoryImpl
import org.koin.dsl.module

/**
 * Data layer dependency injection module.
 * 
 * Provides file system, settings repository, HTTP clients, and database drivers.
 */
val dataModule = module {
    // File system
    single<FileSystem> { JvmFileSystem() }
    
    // FileManager (compatibility wrapper)
    single<FileManager> { FileManager(get()) }
    
    // Settings persistence
    single<SettingsPersistence> { JvmSettingsPersistence }
    
    // Settings repository
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    
    // HTTP client engine
    single<HttpClientEngine> { CIO.create() }
}

