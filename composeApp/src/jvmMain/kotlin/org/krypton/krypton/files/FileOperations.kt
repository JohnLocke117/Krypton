package org.krypton.krypton.files

import java.nio.file.Path

/**
 * Interface for file operations to improve testability.
 * 
 * This interface abstracts file system operations, allowing for easier testing
 * and potential platform-specific implementations.
 */
interface FileOperations {
    fun listFiles(directory: Path): List<Path>
    fun readFile(file: Path): String?
    fun writeFile(file: Path, content: String): Boolean
    fun createFile(file: Path): Boolean
    fun isDirectory(path: Path): Boolean
    fun isFile(path: Path): Boolean
}

