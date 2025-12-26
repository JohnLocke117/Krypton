package org.krypton.krypton.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Initializes Kermit logging for JVM/Desktop platform.
 * 
 * Uses CleanLogWriter to output logs in a clean, Ollama-style format.
 * Format: [TAG] YYYY/MM/DD - HH:mm:ss | message
 */
fun initializeKermit(): Logger {
    // Create logger with clean writer for JVM/Desktop
    val logger = Logger.withTag("Krypton")
    
    // Set log writers and minimum severity
    Logger.setLogWriters(listOf(CleanLogWriter()))
    Logger.setMinSeverity(Severity.Debug)
    
    // Initialize AppLogger with the Kermit logger
    AppLogger.initialize(logger)
    
    return logger
}

