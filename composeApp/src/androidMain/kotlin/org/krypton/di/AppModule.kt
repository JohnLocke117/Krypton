package org.krypton.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Application-level dependency injection module for Android.
 * 
 * Provides coroutine scopes, logger factories, and other app-wide dependencies.
 */
val appModule = module {
    // Application coroutine scope
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob())
    }
}

