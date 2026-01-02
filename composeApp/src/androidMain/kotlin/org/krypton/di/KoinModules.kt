package org.krypton.di

import org.koin.core.module.Module

/**
 * Common modules shared across all platforms.
 * Imported from commonMain source set.
 */
val commonModules: List<Module> = listOf(
    appModule,
    dataModule,
    ragModule,
    webModule,
    chatModule,
    commonUiModule,
    studyModule
)

/**
 * All modules for Android platform.
 */
val allModules: List<Module> = commonModules + androidPlatformModule + androidUiModule + androidStudyModule

