package org.krypton.data.files.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.krypton.data.files.FileError
import org.krypton.data.files.FileSystem
import org.krypton.platform.VaultRoot
import org.krypton.platform.NoteFile
import org.krypton.platform.VaultDirectory
import org.krypton.platform.NoteEntry
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
            // #region agent log
            try {
                val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                val logEntry = """{"location":"AndroidFileSystem.kt:37","message":"listFiles called","data":{"directoryPath":"$directoryPath"},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                logFile.appendText(logEntry + "\n")
            } catch (e: Exception) { /* Ignore logging errors */ }
            // #endregion
            
            // Check if path is a SAF URI
            val uri = try {
                Uri.parse(directoryPath)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // #region agent log
                try {
                    val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                    val logEntry = """{"location":"AndroidFileSystem.kt:50","message":"Using SAF URI path","data":{"uri":"${uri}"},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                    logFile.appendText(logEntry + "\n")
                } catch (e: Exception) { /* Ignore logging errors */ }
                // #endregion
                
                // SAF-based access using DocumentFile
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                if (treeDocument != null && treeDocument.isDirectory) {
                    val children = treeDocument.listFiles()
                    // #region agent log
                    try {
                        val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                        val childNames = children.mapNotNull { it.name }.take(5).joinToString(",") { "\"$it\"" }
                        val logEntry = """{"location":"AndroidFileSystem.kt:58","message":"Listed SAF files","data":{"count":${children.size},"children":[$childNames]},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                        logFile.appendText(logEntry + "\n")
                    } catch (e: Exception) { /* Ignore logging errors */ }
                    // #endregion
                    
                    children.map { child ->
                        // Return URI as string for SAF files
                        child.uri.toString()
                    }
                } else {
                    // #region agent log
                    try {
                        val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                        val logEntry = """{"location":"AndroidFileSystem.kt:70","message":"Tree document is null or not directory","data":{"treeDocumentNull":${treeDocument == null},"isDirectory":${treeDocument?.isDirectory ?: false}},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                        logFile.appendText(logEntry + "\n")
                    } catch (e: Exception) { /* Ignore logging errors */ }
                    // #endregion
                    AppLogger.w("AndroidFileSystem", "Tree document is null or not a directory for URI: $uri")
                    emptyList()
                }
            } else {
                // #region agent log
                try {
                    val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                    val logEntry = """{"location":"AndroidFileSystem.kt:78","message":"Using file path (not SAF)","data":{"directoryPath":"$directoryPath"},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                    logFile.appendText(logEntry + "\n")
                } catch (e: Exception) { /* Ignore logging errors */ }
                // #endregion
                
                // File path-based access (fallback)
                val dir = File(directoryPath)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()?.map { it.absolutePath } ?: emptyList()
                    // #region agent log
                    try {
                        val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                        val logEntry = """{"location":"AndroidFileSystem.kt:86","message":"Listed file path files","data":{"count":${files.size}},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                        logFile.appendText(logEntry + "\n")
                    } catch (e: Exception) { /* Ignore logging errors */ }
                    // #endregion
                    files
                } else {
                    // #region agent log
                    try {
                        val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                        val logEntry = """{"location":"AndroidFileSystem.kt:92","message":"Directory does not exist","data":{"exists":${dir.exists()},"isDirectory":${dir.isDirectory}},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                        logFile.appendText(logEntry + "\n")
                    } catch (e: Exception) { /* Ignore logging errors */ }
                    // #endregion
                    emptyList()
                }
            }
        } catch (e: Exception) {
            // #region agent log
            try {
                val logFile = java.io.File("/Users/vararya/Varun/Code/Krypton/.cursor/debug.log")
                val logEntry = """{"location":"AndroidFileSystem.kt:100","message":"Exception in listFiles","data":{"error":"${e.message}","type":"${e.javaClass.simpleName}"},"timestamp":${System.currentTimeMillis()},"sessionId":"debug-session","runId":"run1","hypothesisId":"A"}"""
                logFile.appendText(logEntry + "\n")
            } catch (logErr: Exception) { /* Ignore logging errors */ }
            // #endregion
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
    
    /**
     * Lists entries (folders and files) in a vault directory using DocumentFile or File.
     * This method is Android-specific and uses SAF for proper file access when available,
     * or falls back to regular File operations for file paths.
     * 
     * @param directory The vault directory to list entries from
     * @return List of NoteEntry objects (folders and markdown files)
     */
    suspend fun listEntries(directory: VaultDirectory): List<NoteEntry> = withContext(Dispatchers.IO) {
        try {
            // Check if directory.uri is a SAF URI or a file path
            val uri = try {
                Uri.parse(directory.uri)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val docDir = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
                
                if (!docDir.isDirectory) {
                    return@withContext emptyList()
                }
                
                val children = docDir.listFiles()
                children.mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    when {
                        doc.isDirectory -> NoteEntry.Folder(
                            name = name,
                            uri = doc.uri.toString()
                        )
                        doc.isFile && name.endsWith(".md", ignoreCase = true) -> NoteEntry.File(
                            name = name,
                            uri = doc.uri.toString()
                        )
                        else -> null
                    }
                }
            } else {
                // File path-based access (fallback for non-SAF paths)
                val dir = File(directory.uri)
                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext emptyList()
                }
                
                val children = dir.listFiles() ?: return@withContext emptyList()
                children.mapNotNull { file ->
                    val name = file.name
                    when {
                        file.isDirectory -> NoteEntry.Folder(
                            name = name,
                            uri = file.absolutePath
                        )
                        file.isFile && name.endsWith(".md", ignoreCase = true) -> NoteEntry.File(
                            name = name,
                            uri = file.absolutePath
                        )
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to list entries in directory: ${directory.uri}", e)
            emptyList()
        }
    }
    
    override suspend fun listNotes(root: VaultRoot): List<NoteFile> = withContext(Dispatchers.IO) {
        try {
            // Check if root.id is a URI (SAF) or a file path
            val uri = try {
                Uri.parse(root.id)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                if (treeDocument != null && treeDocument.isDirectory) {
                    listMarkdownFilesRecursive(treeDocument, "")
                } else {
                    emptyList()
                }
            } else {
                // File path-based access (fallback)
                val rootDir = File(root.id)
                if (rootDir.exists() && rootDir.isDirectory) {
                    rootDir.walkTopDown()
                        .filter { it.isFile }
                        .filter { it.name.endsWith(".md", ignoreCase = true) }
                        .map { file ->
                            val relativePath = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                            val nameWithoutExt = file.nameWithoutExtension
                            NoteFile(
                                path = relativePath,
                                name = nameWithoutExt,
                                fullPath = relativePath
                            )
                        }
                        .toList()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to list notes in vault: ${root.id}", e)
            emptyList()
        }
    }
    
    private fun listMarkdownFilesRecursive(document: DocumentFile, basePath: String): List<NoteFile> {
        val files = mutableListOf<NoteFile>()
        
        try {
            val children = document.listFiles()
            for (child in children) {
                val childName = child.name ?: continue
                if (child.isDirectory) {
                    // Recursively list files in subdirectories
                    val childPath = if (basePath.isEmpty()) childName else "$basePath/$childName"
                    files.addAll(listMarkdownFilesRecursive(child, childPath))
                } else if (childName.endsWith(".md", ignoreCase = true)) {
                    // Found a markdown file
                    val relativePath = if (basePath.isEmpty()) childName else "$basePath/$childName"
                    val nameWithoutExt = childName.removeSuffix(".md").removeSuffix(".MD")
                    files.add(
                        NoteFile(
                            path = relativePath,
                            name = nameWithoutExt,
                            fullPath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to list files in directory: $basePath", e)
        }
        
        return files
    }
    
    override suspend fun readNote(root: VaultRoot, noteFile: NoteFile): String? = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(root.id)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                val fileDocument = findDocumentFile(treeDocument, noteFile.path)
                if (fileDocument != null && fileDocument.isFile && fileDocument.canRead()) {
                    context.contentResolver.openInputStream(fileDocument.uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                } else {
                    null
                }
            } else {
                // File path-based access (fallback)
                val rootDir = File(root.id)
                val file = File(rootDir, noteFile.path)
                if (file.exists() && file.isFile) {
                    file.readText()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to read note: ${noteFile.path}", e)
            null
        }
    }
    
    override suspend fun writeNote(root: VaultRoot, noteFile: NoteFile, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(root.id)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                val parentPath = noteFile.path.substringBeforeLast("/", "")
                val fileName = noteFile.path.substringAfterLast("/", noteFile.path)
                
                // Find or create parent directory
                val parentDocument = if (parentPath.isEmpty()) {
                    treeDocument
                } else {
                    findOrCreateDirectory(treeDocument, parentPath)
                }
                
                if (parentDocument != null) {
                    // Find or create the file
                    var fileDocument = parentDocument.findFile(fileName)
                    if (fileDocument == null) {
                        fileDocument = parentDocument.createFile("text/markdown", fileName)
                    }
                    
                    if (fileDocument != null && fileDocument.canWrite()) {
                        context.contentResolver.openOutputStream(fileDocument.uri)?.use { outputStream ->
                            outputStream.bufferedWriter().use { writer ->
                                writer.write(content)
                            }
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } else {
                // File path-based access (fallback)
                val rootDir = File(root.id)
                val file = File(rootDir, noteFile.path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                true
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to write note: ${noteFile.path}", e)
            false
        }
    }
    
    override suspend fun createNote(root: VaultRoot, noteFile: NoteFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(root.id)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                val parentPath = noteFile.path.substringBeforeLast("/", "")
                val fileName = noteFile.path.substringAfterLast("/", noteFile.path)
                
                // Find or create parent directory
                val parentDocument = if (parentPath.isEmpty()) {
                    treeDocument
                } else {
                    findOrCreateDirectory(treeDocument, parentPath)
                }
                
                if (parentDocument != null) {
                    // Check if file already exists
                    val existingFile = parentDocument.findFile(fileName)
                    if (existingFile == null) {
                        val newFile = parentDocument.createFile("text/markdown", fileName)
                        newFile != null
                    } else {
                        false // File already exists
                    }
                } else {
                    false
                }
            } else {
                // File path-based access (fallback)
                val rootDir = File(root.id)
                val file = File(rootDir, noteFile.path)
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to create note: ${noteFile.path}", e)
            false
        }
    }
    
    override suspend fun deleteNote(root: VaultRoot, noteFile: NoteFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(root.id)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access
                val treeDocument = DocumentFile.fromTreeUri(context, uri)
                val fileDocument = findDocumentFile(treeDocument, noteFile.path)
                if (fileDocument != null && fileDocument.canWrite()) {
                    fileDocument.delete()
                } else {
                    false
                }
            } else {
                // File path-based access (fallback)
                val rootDir = File(root.id)
                val file = File(rootDir, noteFile.path)
                if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to delete note: ${noteFile.path}", e)
            false
        }
    }
    
    /**
     * Helper function to find a DocumentFile by relative path.
     */
    private fun findDocumentFile(root: DocumentFile?, path: String): DocumentFile? {
        if (root == null || path.isEmpty()) return root
        
        val parts = path.split("/")
        var current = root
        
        for (part in parts) {
            current = current?.findFile(part)
            if (current == null) break
        }
        
        return current
    }
    
    /**
     * Helper function to find or create a directory by relative path.
     */
    private fun findOrCreateDirectory(root: DocumentFile?, path: String): DocumentFile? {
        if (root == null) return null
        if (path.isEmpty()) return root
        
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = root
        
        for (part in parts) {
            var child = current?.findFile(part)
            if (child == null) {
                child = current?.createDirectory(part)
            }
            current = child
            if (current == null) break
        }
        
        return current
    }
    
    /**
     * Creates a new file in the given vault directory.
     * 
     * @param directory The vault directory to create the file in
     * @param fileName Name of the file to create (should include .md extension for markdown files)
     * @return true if successful, false otherwise
     */
    suspend fun createFileInDirectory(directory: VaultDirectory, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(directory.uri)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val docDir = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
                
                // Check if file already exists
                val existingFile = docDir.findFile(fileName)
                if (existingFile != null) {
                    return@withContext false
                }
                
                // Create the file
                val newFile = if (fileName.endsWith(".md", ignoreCase = true)) {
                    docDir.createFile("text/markdown", fileName)
                } else {
                    docDir.createFile("*/*", fileName)
                }
                
                newFile != null
            } else {
                // File path-based access
                val dir = File(directory.uri)
                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext false
                }
                
                val file = File(dir, fileName)
                if (file.exists()) {
                    return@withContext false
                }
                
                file.createNewFile()
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to create file in directory: $fileName", e)
            false
        }
    }
    
    /**
     * Creates a new folder in the given vault directory.
     * 
     * @param directory The vault directory to create the folder in
     * @param folderName Name of the folder to create
     * @return true if successful, false otherwise
     */
    suspend fun createFolderInDirectory(directory: VaultDirectory, folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = try {
                Uri.parse(directory.uri)
            } catch (e: Exception) {
                null
            }
            
            if (uri != null && uri.scheme == "content") {
                // SAF-based access using DocumentFile
                val docDir = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
                
                // Check if folder already exists
                val existingFolder = docDir.findFile(folderName)
                if (existingFolder != null && existingFolder.isDirectory) {
                    return@withContext false
                }
                
                // Create the folder
                val newFolder = docDir.createDirectory(folderName)
                newFolder != null
            } else {
                // File path-based access
                val dir = File(directory.uri)
                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext false
                }
                
                val folder = File(dir, folderName)
                if (folder.exists()) {
                    return@withContext false
                }
                
                folder.mkdirs()
            }
        } catch (e: Exception) {
            AppLogger.e("AndroidFileSystem", "Failed to create folder in directory: $folderName", e)
            false
        }
    }
}

