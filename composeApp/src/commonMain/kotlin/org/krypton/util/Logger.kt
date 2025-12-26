package org.krypton.util

/**
 * Platform-agnostic interface for logging.
 * 
 * Provides structured logging capabilities that can be implemented
 * differently on each platform.
 */
interface Logger {
    fun debug(message: String, throwable: Throwable? = null)
    fun info(message: String, throwable: Throwable? = null)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}

/**
 * Factory for creating Logger instances.
 */
expect fun createLogger(tag: String): Logger

