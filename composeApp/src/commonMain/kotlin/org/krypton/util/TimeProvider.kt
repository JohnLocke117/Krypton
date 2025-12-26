package org.krypton.util

/**
 * Platform-agnostic interface for getting current time in milliseconds.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
}

/**
 * Factory for creating TimeProvider instances.
 */
expect fun createTimeProvider(): TimeProvider

