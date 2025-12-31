package org.krypton.platform

import org.koin.dsl.module

/**
 * JVM platform-specific dependency injection module.
 * 
 * Provides platform-specific implementations for:
 * - VaultPicker (file/folder selection)
 * - SettingsConfigProvider (settings file path management)
 */
import org.krypton.core.domain.search.PatternMatcher
import org.krypton.core.domain.search.JvmPatternMatcher

val jvmPlatformModule = module {
    // JVM-specific bindings
    single<PatternMatcher> { JvmPatternMatcher() }
    single<VaultPicker> { JvmVaultPicker() }
    single<SettingsConfigProvider> { JvmSettingsConfigProvider() }
}

