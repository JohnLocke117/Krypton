package org.krypton.di

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.data.files.FileSystem
import org.krypton.data.files.impl.JvmFileSystem
import org.krypton.data.repository.SettingsPersistence
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.repository.impl.JvmSettingsPersistence
import org.krypton.data.repository.impl.SettingsRepositoryImpl
import org.koin.dsl.module

/**
 * Data layer dependency injection module.
 * 
 * Provides file system, settings repository, HTTP clients, and database drivers.
 */
val dataModule = module {
    // File system
    single<FileSystem> { JvmFileSystem() }
    
    // Settings persistence
    single<SettingsPersistence> { JvmSettingsPersistence }
    
    // Settings repository
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    
    // HTTP client engine
    single<HttpClientEngine> { CIO.create() }
}

