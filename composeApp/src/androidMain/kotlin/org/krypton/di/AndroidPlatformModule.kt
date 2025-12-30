package org.krypton.di

import android.content.Context
import org.krypton.data.files.FileSystem
import org.krypton.data.files.impl.AndroidFileSystem
import org.krypton.data.repository.SettingsPersistence
import org.krypton.data.repository.impl.AndroidSettingsPersistence
import org.krypton.platform.SettingsConfigProvider
import org.krypton.platform.VaultPicker
import org.krypton.platform.AndroidSettingsConfigProvider
import org.krypton.platform.AndroidVaultPicker
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android platform-specific dependency injection module.
 * 
 * Provides Android implementations for:
 * - FileSystem
 * - SettingsPersistence
 * - VaultPicker
 * - SettingsConfigProvider
 */
val androidPlatformModule = module {
    // Android Context is provided by startKoin { androidContext(...) }
    // We can access it using androidContext() function from Koin Android
    
    // Platform abstractions
    single<VaultPicker> { AndroidVaultPicker(androidContext()) }
    single<SettingsConfigProvider> { AndroidSettingsConfigProvider(androidContext()) }
    
    // File system
    single<FileSystem> { AndroidFileSystem(androidContext()) }
    
    // Settings persistence
    single<SettingsPersistence> { 
        AndroidSettingsPersistence(androidContext(), get())
    }
}

