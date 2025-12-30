package org.krypton.util

/**
 * Android implementation of TimeProvider using System.currentTimeMillis().
 */
class DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Factory function for creating TimeProvider instances on Android.
 */
actual fun createTimeProvider(): TimeProvider = DefaultTimeProvider()

