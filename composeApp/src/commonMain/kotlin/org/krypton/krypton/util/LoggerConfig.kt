package org.krypton.krypton.util

import co.touchlab.kermit.Severity

/**
 * Configuration utilities for application-wide logging using Kermit.
 * 
 * Provides a consistent log format across all platforms:
 * "[{ISO-8601-timestamp}] [{level}] [{tag}] {message} {throwable_if_any}"
 * 
 * Example: "[2025-12-23T17:45:00.123Z] [I] [HomeScreen] Opened"
 */
object LoggerConfig {
    
    /**
     * Formats a log message with ISO-8601 timestamp and short log level.
     * 
     * Format: "[{ISO-8601-timestamp}] [{level}] [{tag}] {message}"
     * 
     * @param timestamp ISO-8601 formatted timestamp (UTC)
     * @param level Short log level (D/I/W/E)
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable to append
     * @return Formatted log message
     */
    fun formatLogMessage(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ): String {
        val throwableStr = throwable?.let { 
            "\n${it.stackTraceToString()}" 
        } ?: ""
        return "[$timestamp] [$level] [$tag] $message$throwableStr"
    }
    
    /**
     * Converts Kermit Severity to short string representation.
     * 
     * @param severity Kermit severity level
     * @return Short level string (D/I/W/E/A)
     */
    fun severityToShortLevel(severity: Severity): String {
        return when (severity) {
            Severity.Verbose -> "V"
            Severity.Debug -> "D"
            Severity.Info -> "I"
            Severity.Warn -> "W"
            Severity.Error -> "E"
            Severity.Assert -> "A"
        }
    }
}

