package org.krypton.di

import org.krypton.data.study.StudyPersistence
import org.krypton.data.study.impl.JvmStudyPersistence
import org.koin.dsl.module

/**
 * JVM/Desktop-specific study module.
 * Provides platform-specific implementations for study persistence.
 */
val jvmStudyModule = module {
    single<StudyPersistence> { JvmStudyPersistence() }
}

