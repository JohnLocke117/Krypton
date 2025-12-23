package org.krypton.krypton.util

import java.util.UUID

/**
 * JVM implementation of IdGenerator using UUID.
 */
class DefaultIdGenerator : IdGenerator {
    override fun generateId(): String = UUID.randomUUID().toString()
}

/**
 * Factory function for creating IdGenerator instances on JVM.
 */
actual fun createIdGenerator(): IdGenerator = DefaultIdGenerator()

