package org.krypton.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

/**
 * JVM implementation of VaultPicker using Swing's JFileChooser.
 */
class JvmVaultPicker : VaultPicker {
    
    override suspend fun pickVault(): String? = withContext(Dispatchers.IO) {
        val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        fileChooser.dialogTitle = "Select Folder"
        
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            selectedFile.absolutePath
        } else {
            null
        }
    }
    
    override suspend fun pickFile(filter: FileFilter?): String? = withContext(Dispatchers.IO) {
        val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
        fileChooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileChooser.dialogTitle = "Select Folder or File"
        
        // Apply filter if provided
        filter?.let { fileFilter ->
            fileChooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
                override fun accept(f: File): Boolean {
                    return f.isDirectory || fileFilter.extensions.any { ext ->
                        f.name.lowercase().endsWith(ext.lowercase())
                    }
                }
                override fun getDescription(): String = fileFilter.description
            }
        }
        
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            selectedFile.absolutePath
        } else {
            null
        }
    }
}

