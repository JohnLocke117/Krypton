package org.krypton.krypton.data.files.impl

import org.krypton.krypton.data.files.FileSystem
import org.krypton.krypton.data.files.FileError
import org.krypton.krypton.util.AppLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.NoSuchFileException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.InvalidPathException
import java.io.FileNotFoundException
import java.awt.Desktop
import java.io.File

/**
 * JVM implementation of FileSystem using java.nio.file.
 * 
 * Provides error handling and logging for all file operations.
 */
class JvmFileSystem : FileSystem {
    
    override fun mapExceptionToError(path: String, operation: String, exception: Throwable): FileError {
        val error = when (exception) {
            is NoSuchFileException, is FileNotFoundException -> FileError.NotFound(path)
            is AccessDeniedException -> FileError.PermissionDenied(path)
            is FileAlreadyExistsException -> FileError.AlreadyExists(path)
            is InvalidPathException -> FileError.InvalidPath(path, exception.message)
            else -> FileError.Unknown(path, exception.message, exception.javaClass.simpleName)
        }
        
        // Log the error with context
        AppLogger.e("JvmFileSystem", "File operation failed: $operation on $path - ${error::class.simpleName}: ${exception.message}", exception)
        
        return error
    }
    
    override fun listFiles(directoryPath: String): List<String> {
        return try {
            val path = Paths.get(directoryPath)
            if (Files.exists(path) && Files.isDirectory(path)) {
                Files.list(path)
                    .sorted { a, b ->
                        // Directories first, then files, both alphabetically
                        val aIsDir = Files.isDirectory(a)
                        val bIsDir = Files.isDirectory(b)
                        when {
                            aIsDir && !bIsDir -> -1
                            !aIsDir && bIsDir -> 1
                            else -> a.fileName.toString().compareTo(b.fileName.toString(), ignoreCase = true)
                        }
                    }
                    .map { it.toString() }
                    .toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to list files in directory: $directoryPath", e)
            emptyList()
        }
    }
    
    override fun readFile(filePath: String): String? {
        return try {
            val path = Paths.get(filePath)
            if (Files.exists(path) && Files.isRegularFile(path)) {
                Files.readString(path)
            } else {
                AppLogger.e("JvmFileSystem", "File not found or not a regular file: $filePath", null)
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to read file: $filePath", e)
            null
        }
    }
    
    override fun writeFile(filePath: String, content: String): Boolean {
        return try {
            val path = Paths.get(filePath)
            // Ensure parent directory exists
            path.parent?.let { Files.createDirectories(it) }
            Files.writeString(
                path,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            true
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to write file: $filePath", e)
            false
        }
    }
    
    override fun createFile(filePath: String): Boolean {
        return try {
            val path = Paths.get(filePath)
            if (!Files.exists(path)) {
                // Ensure parent directory exists
                path.parent?.let { Files.createDirectories(it) }
                Files.createFile(path)
                true
            } else {
                AppLogger.e("JvmFileSystem", "File already exists: $filePath", null)
                false
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to create file: $filePath", e)
            false
        }
    }
    
    override fun isDirectory(path: String): Boolean {
        return try {
            Files.isDirectory(Paths.get(path))
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isFile(path: String): Boolean {
        return try {
            Files.isRegularFile(Paths.get(path))
        } catch (e: Exception) {
            false
        }
    }
    
    override fun renameFile(oldPath: String, newPath: String): Boolean {
        return try {
            val old = Paths.get(oldPath)
            val new = Paths.get(newPath)
            if (Files.exists(old) && !Files.exists(new)) {
                Files.move(old, new)
                true
            } else {
                AppLogger.e("JvmFileSystem", "Cannot rename: old path exists=${Files.exists(old)}, new path exists=${Files.exists(new)}", null)
                false
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to rename file: $oldPath -> $newPath", e)
            false
        }
    }
    
    override fun deleteFile(path: String): Boolean {
        return try {
            val filePath = Paths.get(path)
            if (!Files.exists(filePath)) {
                AppLogger.e("JvmFileSystem", "Cannot delete: path does not exist: $path", null)
                return false
            }
            
            if (Files.isDirectory(filePath)) {
                // Recursive deletion for directories
                Files.walkFileTree(filePath, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                        Files.delete(file)
                        return FileVisitResult.CONTINUE
                    }
                    
                    override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
            } else {
                Files.delete(filePath)
            }
            true
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to delete file: $path", e)
            false
        }
    }
    
    override fun createDirectory(directoryPath: String): Boolean {
        return try {
            val path = Paths.get(directoryPath)
            if (!Files.exists(path)) {
                Files.createDirectory(path)
                true
            } else {
                AppLogger.e("JvmFileSystem", "Directory already exists: $directoryPath", null)
                false
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to create directory: $directoryPath", e)
            false
        }
    }
    
    override fun exists(path: String): Boolean {
        return try {
            Files.exists(Paths.get(path))
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to check if path exists: $path", e)
            false
        }
    }
    
    override fun moveToTrash(path: String): Boolean {
        return try {
            val filePath = Paths.get(path)
            if (!Files.exists(filePath)) {
                AppLogger.e("JvmFileSystem", "Cannot move to trash: path does not exist: $path", null)
                return false
            }
            
            // Use Desktop API to move to trash (works on macOS, Windows, and Linux with proper desktop environment)
            val desktop = Desktop.getDesktop()
            val file = filePath.toFile()
            
            if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                desktop.moveToTrash(file)
                true
            } else {
                // Fallback: If Desktop.moveToTrash is not supported, use permanent delete
                // This can happen on some Linux systems without a desktop environment
                AppLogger.e("JvmFileSystem", "Move to trash not supported, falling back to permanent delete: $path", null)
                deleteFile(path)
            }
        } catch (e: Exception) {
            AppLogger.e("JvmFileSystem", "Failed to move file to trash: $path", e)
            false
        }
    }
}

