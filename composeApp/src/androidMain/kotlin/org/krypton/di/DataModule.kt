package org.krypton.di

import android.content.Context
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.krypton.FileManager
import org.krypton.data.files.FileSystem
import org.krypton.data.files.impl.AndroidFileSystem
import org.krypton.data.repository.SettingsPersistence
import org.krypton.data.repository.SettingsRepository
import org.krypton.data.repository.impl.AndroidSettingsPersistence
import org.krypton.data.repository.impl.SettingsRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Data layer dependency injection module for Android.
 * 
 * Provides file system, settings repository, HTTP clients, and database drivers.
 */
val dataModule = module {
    // File system - Android implementation
    // Note: AndroidFileSystem is already provided by androidPlatformModule
    // This is kept for compatibility but will be overridden by androidPlatformModule
    single<FileSystem> { 
        AndroidFileSystem(androidContext())
    }
    
    // FileManager (compatibility wrapper)
    single<FileManager> { FileManager(get()) }
    
    // Settings persistence - Android implementation
    single<SettingsPersistence> { 
        AndroidSettingsPersistence(androidContext(), get())
    }
    
    // Settings repository
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    
    // HTTP client engine
    single<HttpClientEngine> { CIO.create() }
}

