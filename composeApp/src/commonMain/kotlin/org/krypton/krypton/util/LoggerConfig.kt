package org.krypton.krypton.util

import co.touchlab.kermit.Severity

/**
 * Configuration utilities for application-wide logging using Kermit.
 * 
 * Provides a consistent log format across all platforms with color coding:
 * "[{ISO-8601-timestamp}] [{level}] [{tag}] {message} {throwable_if_any}"
 * 
 * Example: "[2025-12-23T17:45:00.123Z] [I] [HomeScreen] Opened"
 */
object LoggerConfig {
    
    // ANSI color codes for terminal output
    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_RED = "\u001B[31m"
    
    // Fixed widths for tabular formatting
    private const val LEVEL_WIDTH = 3
    private const val TAG_WIDTH = 30
    
    /**
     * Gets ANSI color code for a severity level.
     */
    private fun getColorForSeverity(severity: Severity): String {
        return when (severity) {
            Severity.Info -> ANSI_GREEN
            Severity.Error -> ANSI_RED
            Severity.Debug -> ANSI_YELLOW
            else -> ANSI_RESET
        }
    }
    
    /**
     * Formats a log message with short log level and tag.
     * Uses tabular format with consistent spacing and color coding.
     * 
     * Format: "[{level}] [{tag}] {message}"
     * 
     * @param timestamp ISO-8601 formatted timestamp (UTC) - not used, kept for compatibility
     * @param level Short log level (D/I/W/E)
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable to append
     * @param severity Severity level for color coding
     * @return Formatted log message with color codes
     */
    fun formatLogMessage(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        severity: Severity = Severity.Info
    ): String {
        val color = getColorForSeverity(severity)
        val reset = ANSI_RESET
        
        // Pad fields for tabular format (no timestamp)
        val paddedLevel = "[$level]".padEnd(LEVEL_WIDTH + 2)
        // Remove trailing spaces from tag - just use the tag as-is with brackets
        val formattedTag = "[$tag]"
        
        val throwableStr = throwable?.let { 
            "\n${color}${it.stackTraceToString()}${reset}" 
        } ?: ""
        
        return "${color}$paddedLevel $formattedTag $message${reset}$throwableStr"
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

