package org.krypton.di

import org.krypton.data.study.StudyPersistence
import org.krypton.data.study.impl.AndroidStudyPersistence
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android-specific study module.
 * Provides platform-specific implementations for study persistence.
 */
val androidStudyModule = module {
    single<StudyPersistence> { AndroidStudyPersistence(androidContext()) }
}

