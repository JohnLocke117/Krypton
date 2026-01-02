package org.krypton.di

import org.krypton.platform.jvmPlatformModule
import org.koin.core.module.Module

/**
 * Common modules shared across all platforms.
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
 * All modules for JVM/Desktop platform.
 */
val allModules: List<Module> = commonModules + jvmPlatformModule + jvmStudyModule

