package org.krypton.util

/**
 * JVM implementation of Logger using println.
 * 
 * This is a simple implementation that outputs to console.
 * In the future, this could be replaced with a proper logging framework.
 */
class DefaultLogger(private val tag: String) : Logger {
    override fun debug(message: String, throwable: Throwable?) {
        println("[$tag] DEBUG: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }

    override fun info(message: String, throwable: Throwable?) {
        println("[$tag] INFO: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }

    override fun warn(message: String, throwable: Throwable?) {
        println("[$tag] WARN: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }

    override fun error(message: String, throwable: Throwable?) {
        println("[$tag] ERROR: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }
}

/**
 * Factory function for creating Logger instances on JVM.
 */
actual fun createLogger(tag: String): Logger = DefaultLogger(tag)

