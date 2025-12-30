package org.krypton.platform

import org.koin.dsl.module

/**
 * JVM platform-specific dependency injection module.
 * 
 * Provides platform-specific implementations for:
 * - VaultPicker (file/folder selection)
 * - SettingsConfigProvider (settings file path management)
 */
val jvmPlatformModule = module {
    single<VaultPicker> { JvmVaultPicker() }
    single<SettingsConfigProvider> { JvmSettingsConfigProvider() }
}

