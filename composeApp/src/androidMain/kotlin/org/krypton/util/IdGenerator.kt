package org.krypton.util

import java.util.UUID

/**
 * Android implementation of IdGenerator using UUID.
 */
class DefaultIdGenerator : IdGenerator {
    override fun generateId(): String = UUID.randomUUID().toString()
}

/**
 * Factory function for creating IdGenerator instances on Android.
 */
actual fun createIdGenerator(): IdGenerator = DefaultIdGenerator()

