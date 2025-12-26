package org.krypton.util

/**
 * JVM implementation of TimeProvider using System.currentTimeMillis().
 */
class DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Factory function for creating TimeProvider instances on JVM.
 */
actual fun createTimeProvider(): TimeProvider = DefaultTimeProvider()

