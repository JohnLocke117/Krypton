package org.krypton.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Platform-specific timestamp formatter.
 */
expect fun getCurrentTimestamp(): String

/**
 * Centralized logging facade for the application.
 * 
 * Provides thread-safe, error-safe logging with consistent formatting.
 * All log messages follow the format:
 * "[{ISO-8601-timestamp}] [{level}] [{tag}] {message} {throwable_if_any}"
 * 
 * This logger never throws exceptions - all errors are silently caught.
 */
object AppLogger {
    @Volatile
    private var logger: Logger? = null
    
    /**
     * Initialize the logger with a Kermit Logger instance.
     * Should be called once at app startup.
     * 
     * @param kermitLogger Initialized Kermit Logger instance
     */
    fun initialize(kermitLogger: Logger) {
        logger = kermitLogger
    }
    
    /**
     * Get the current logger instance, or create a default one if not initialized.
     * This ensures the logger never crashes even if not properly initialized.
     */
    private fun getLogger(): Logger {
        return logger ?: run {
            // Fallback to a default logger if not initialized
            // This should rarely happen, but ensures we never crash
            Logger.withTag("AppLogger")
        }
    }
    
    /**
     * Format and log a message with the specified severity.
     * 
     * The actual formatting is handled by the LogWriter (e.g., CleanLogWriter),
     * so we just pass the raw message to Kermit.
     * 
     * @param severity Log severity level
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    private fun log(
        severity: Severity,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        try {
            // Use Kermit's logging methods - pass raw message, let LogWriter handle formatting
            val kermitLogger = getLogger().withTag(tag)
            when (severity) {
                Severity.Verbose -> kermitLogger.v { message }
                Severity.Debug -> kermitLogger.d { message }
                Severity.Info -> kermitLogger.i { message }
                Severity.Warn -> kermitLogger.w { message }
                Severity.Error -> {
                    if (throwable != null) {
                        kermitLogger.e(throwable) { message }
                    } else {
                        kermitLogger.e { message }
                    }
                }
                Severity.Assert -> kermitLogger.a { message }
            }
        } catch (e: Exception) {
            // Never crash - silently ignore logging errors
            // In production, this ensures logging never breaks the app
        }
    }
    
    /**
     * Log a debug message.
     * 
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(Severity.Debug, tag, message, throwable)
    }
    
    /**
     * Log an info message.
     * 
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Severity.Info, tag, message, throwable)
    }
    
    /**
     * Log a warning message.
     * 
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Severity.Warn, tag, message, throwable)
    }
    
    /**
     * Log an error message.
     * 
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Severity.Error, tag, message, throwable)
    }
    
    /**
     * Log a user action or event.
     * 
     * This is a convenience method for logging user interactions and navigation events.
     * 
     * @param screen Screen or component name
     * @param event Event name (e.g., "Opened", "Clicked", "Created")
     * @param details Optional additional details
     */
    fun action(screen: String, event: String, details: String? = null) {
        val message = if (details != null) {
            "$event: $details"
        } else {
            event
        }
        i(screen, message)
    }
}

