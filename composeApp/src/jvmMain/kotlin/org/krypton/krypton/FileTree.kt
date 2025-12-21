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
            if (!FileManager.isDirectory(rootPath)) {
                return null
            }
            buildTreeRecursive(rootPath, rootPath.fileName.toString(), 0, maxDepth)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun buildTreeRecursive(
        path: Path,
        name: String,
        currentDepth: Int,
        maxDepth: Int
    ): FileTreeNode? {
        if (currentDepth >= maxDepth) {
            return null
        }
        
        return try {
            val node = FileTreeNode(
                path = path,
                name = name,
                isDirectory = FileManager.isDirectory(path)
            )
            
            if (node.isDirectory) {
                val files = FileManager.listFiles(path)
                for (file in files) {
                    val childNode = buildTreeRecursive(
                        file,
                        file.fileName.toString(),
                        currentDepth + 1,
                        maxDepth
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
    
    fun findNode(root: FileTreeNode, targetPath: Path): FileTreeNode? {
        if (root.path == targetPath) {
            return root
        }
        
        for (child in root.children) {
            val found = findNode(child, targetPath)
            if (found != null) {
                return found
            }
        }
        
        return null
    }
    
    fun expandPath(root: FileTreeNode, targetPath: Path) {
        // Find the node for the target path
        val targetNode = findNode(root, targetPath) ?: return
        
        // Expand all parent nodes up to root
        var currentPath = targetPath
        while (currentPath != root.path && currentPath.parent != null) {
            currentPath = currentPath.parent
            val node = findNode(root, currentPath)
            node?.isExpanded = true
        }
    }
}

