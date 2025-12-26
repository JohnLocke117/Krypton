package org.krypton.config

/**
 * Default configuration values for logging system.
 * 
 * These defaults are used when initializing loggers.
 */
object LoggingDefaults {
    /**
     * Default log level (DEBUG, INFO, WARN, ERROR).
     * In production, this should typically be INFO or WARN.
     */
    const val DEFAULT_LOG_LEVEL = "INFO"
    
    /**
     * Default log tag prefix for all app logs.
     */
    const val DEFAULT_LOG_TAG_PREFIX = "Krypton"
    
    /**
     * Whether to enable structured logging by default.
     */
    const val DEFAULT_ENABLE_STRUCTURED_LOGGING = true
    
    /**
     * Whether to log to console by default.
     */
    const val DEFAULT_LOG_TO_CONSOLE = true
    
    /**
     * Whether to log to file by default.
     */
    const val DEFAULT_LOG_TO_FILE = false
}

