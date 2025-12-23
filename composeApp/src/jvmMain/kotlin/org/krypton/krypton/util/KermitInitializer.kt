package org.krypton.krypton.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Severity

/**
 * Initializes Kermit logging for JVM/Desktop platform.
 * 
 * Uses CommonWriter to output logs to stdout/console.
 * Logs are formatted with ISO-8601 timestamps and short log levels.
 */
fun initializeKermit(): Logger {
    // Create logger with common writer for JVM/Desktop (uses println)
    // Kermit 2.0+ uses a simpler API
    val logger = Logger.withTag("Krypton")
    
    // Set log writers and minimum severity
    Logger.setLogWriters(listOf(CommonWriter()))
    Logger.setMinSeverity(Severity.Debug)
    
    // Initialize AppLogger with the Kermit logger
    AppLogger.initialize(logger)
    
    return logger
}

