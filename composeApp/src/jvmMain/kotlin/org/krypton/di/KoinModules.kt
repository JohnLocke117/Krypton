package org.krypton.di

import org.koin.core.module.Module

/**
 * Aggregates all Koin modules for easy initialization.
 */
val allModules: List<Module> = listOf(
    appModule,
    dataModule,
    ragModule,
    webModule,
    chatModule,
    uiModule
)

