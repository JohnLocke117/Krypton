package org.krypton.data.files.impl

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.data.files.FileError
import org.krypton.data.files.FileSystem
import org.krypton.util.AppLogger
import java.io.File
import java.io.FileNotFoundException

/**
 * Android implementation of FileSystem using Android's file APIs.
 * 
 * Uses app's internal storage and external storage as appropriate.
 */
class AndroidFileSystem(
    private val context: Context
) : FileSystem {
    
    override fun mapExceptionToError(path: String, operation: String, exception: Throwable): FileError {
        val error = when (exception) {
            is FileNotFoundException -> FileError.NotFound(path)
            is SecurityException -> FileError.PermissionDenied(path)
            else -> FileError.Unknown(path, exception.message, exception.javaClass.simpleName)
        }
        
        AppLogger.e("AndroidFileSystem", "File operation failed: $operation on $path - ${error::class.simpleName}: ${exception.message}", exception)
        
        return error
    }
    
    override fun listFiles(directoryPath: String): List<String> {
        return try {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.map { it.absolutePath } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to list files: $directoryPath", e)
            emptyList()
        }
    }
    
    override fun readFile(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to read file: $filePath", e)
            null
        }
    }
    
    override fun writeFile(filePath: String, content: String): Boolean {
        return try {
            val file = File(filePath)
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to write file: $filePath", e)
            false
        }
    }
    
    override fun createFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                // Ensure parent directory exists
                file.parentFile?.mkdirs()
                file.createNewFile()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to create file: $filePath", e)
            false
        }
    }
    
    override fun isDirectory(path: String): Boolean {
        return try {
            File(path).isDirectory
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isFile(path: String): Boolean {
        return try {
            File(path).isFile
        } catch (e: Exception) {
            false
        }
    }
    
    override fun renameFile(oldPath: String, newPath: String): Boolean {
        return try {
            val oldFile = File(oldPath)
            val newFile = File(newPath)
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to rename file: $oldPath -> $newPath", e)
            false
        }
    }
    
    override fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to delete file: $path", e)
            false
        }
    }
    
    override fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun createDirectory(directoryPath: String): Boolean {
        return try {
            val dir = File(directoryPath)
            if (!dir.exists()) {
                dir.mkdirs()
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to create directory: $directoryPath", e)
            false
        }
    }
    
    override fun moveToTrash(path: String): Boolean {
        // Android doesn't have a system trash, so we'll just delete
        // In a full implementation, you might want to move to a "trash" folder
        return deleteFile(path)
    }
}

