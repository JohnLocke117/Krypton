package org.krypton.krypton

import java.nio.file.Path

data class FileTreeNode(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    var isExpanded: Boolean = false,
    var children: MutableList<FileTreeNode> = mutableListOf()
) {
    fun addChild(node: FileTreeNode) {
        children.add(node)
    }
    
    fun toggleExpanded() {
        isExpanded = !isExpanded
    }
}

object FileTreeBuilder {
    fun buildTree(rootPath: Path, maxDepth: Int = 10): FileTreeNode? {
        return try {
            val fileManager = getFileManager()
            if (!fileManager.isDirectory(rootPath)) {
                return null
            }
            buildTreeRecursive(rootPath, rootPath.fileName.toString(), 0, maxDepth, fileManager)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun buildTreeRecursive(
        path: Path,
        name: String,
        currentDepth: Int,
        maxDepth: Int,
        fileManager: FileManager
    ): FileTreeNode? {
        if (currentDepth >= maxDepth) {
            return null
        }
        
        return try {
            val node = FileTreeNode(
                path = path,
                name = name,
                isDirectory = fileManager.isDirectory(path)
            )
            
            if (node.isDirectory) {
                val files = fileManager.listFiles(path)
                for (file in files) {
                    val childNode = buildTreeRecursive(
                        file,
                        file.fileName.toString(),
                        currentDepth + 1,
                        maxDepth,
                        fileManager
                    )
                    childNode?.let { node.addChild(it) }
                }
                // Sort: directories first, then files, both alphabetically
                node.children.sortWith { a, b ->
                    when {
                        a.isDirectory && !b.isDirectory -> -1
                        !a.isDirectory && b.isDirectory -> 1
                        else -> a.name.compareTo(b.name, ignoreCase = true)
                    }
                }
            }
            
            node
        } catch (e: Exception) {
            null
        }
    }
    
}

