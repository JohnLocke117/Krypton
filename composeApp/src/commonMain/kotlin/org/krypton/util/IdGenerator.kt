package org.krypton.util

/**
 * Platform-agnostic interface for generating unique IDs.
 */
interface IdGenerator {
    fun generateId(): String
}

/**
 * Factory for creating IdGenerator instances.
 */
expect fun createIdGenerator(): IdGenerator

