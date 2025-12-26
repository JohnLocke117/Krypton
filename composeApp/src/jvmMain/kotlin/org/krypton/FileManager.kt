package org.krypton

import org.krypton.data.files.FileSystem
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Compatibility wrapper for FileManager that uses FileSystem internally.
 * 
 * This class provides a Path-based API for backward compatibility while
 * delegating to the platform-agnostic FileSystem interface.
 * 
 * @deprecated Prefer using FileSystem directly with String paths.
 */
class FileManager(private val fileSystem: FileSystem) {
    
    fun listFiles(directory: Path): List<Path> {
        val pathString = directory.toString()
        return fileSystem.listFiles(pathString)
            .map { Paths.get(it) }
    }

    fun readFile(file: Path): String? {
        return fileSystem.readFile(file.toString())
    }

    fun writeFile(file: Path, content: String): Boolean {
        return fileSystem.writeFile(file.toString(), content)
    }

    fun createFile(file: Path): Boolean {
        return fileSystem.createFile(file.toString())
    }

    fun isDirectory(path: Path): Boolean {
        return fileSystem.isDirectory(path.toString())
    }

    fun isFile(path: Path): Boolean {
        return fileSystem.isFile(path.toString())
    }

    fun renameFile(oldPath: Path, newPath: Path): Boolean {
        return fileSystem.renameFile(oldPath.toString(), newPath.toString())
    }

    fun deleteFile(path: Path): Boolean {
        return fileSystem.deleteFile(path.toString())
    }

    fun createDirectory(path: Path): Boolean {
        return fileSystem.createDirectory(path.toString())
    }
}

/**
 * Global FileManager instance for backward compatibility.
 * Should be initialized via DI.
 */
var globalFileManager: FileManager? = null

/**
 * Gets FileManager instance from Koin DI or throws if not available.
 */
fun getFileManager(): FileManager {
    return globalFileManager ?: run {
        // Try to get from Koin if available
        try {
            val koin = org.koin.core.context.GlobalContext.get()
            koin.get<FileManager>().also { globalFileManager = it }
        } catch (e: Exception) {
            throw IllegalStateException("FileManager not initialized. Please configure via DI.", e)
        }
    }
}

