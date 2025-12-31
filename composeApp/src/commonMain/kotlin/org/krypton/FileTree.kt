package org.krypton

import org.krypton.data.files.FileSystem
import org.krypton.util.AppLogger
import org.krypton.util.PathUtils

/**
 * Represents a node in a file tree structure.
 * 
 * Used for building hierarchical file/folder trees for display in the UI.
 * Each node represents either a file or a directory, and can have child nodes
 * if it's a directory.
 * 
 * @param path Full path to the file or directory
 * @param name Display name (filename or directory name)
 * @param isDirectory True if this node represents a directory, false for a file
 * @param isExpanded Whether this directory node is expanded in the UI (only relevant for directories)
 * @param children List of child nodes (only populated for directories)
 */
data class FileTreeNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    var isExpanded: Boolean = false,
    var children: MutableList<FileTreeNode> = mutableListOf()
) {
    /**
     * Adds a child node to this tree node.
     * 
     * @param node The child node to add
     */
    fun addChild(node: FileTreeNode) {
        children.add(node)
    }
    
    /**
     * Toggles the expanded state of this node.
     * Only meaningful for directory nodes.
     */
    fun toggleExpanded() {
        isExpanded = !isExpanded
    }
}

/**
 * Builder for creating file tree structures from a directory path.
 * 
 * Recursively builds a tree structure representing the file system hierarchy,
 * suitable for display in UI components like file explorers.
 */
object FileTreeBuilder {
    /**
     * Builds a file tree starting from the given root path.
     * 
     * The tree is built recursively up to the specified maximum depth.
     * Directories are sorted before files, and both are sorted alphabetically.
     * 
     * @param rootPath The root directory path to build the tree from
     * @param fileSystem The file system implementation to use for file operations
     * @param maxDepth Maximum depth to recurse (default: 10). Prevents infinite recursion on circular links.
     * @return FileTreeNode representing the root directory, or null if the path doesn't exist or isn't a directory
     */
    fun buildTree(rootPath: String, fileSystem: FileSystem, maxDepth: Int = 10): FileTreeNode? {
        return try {
            if (!fileSystem.isDirectory(rootPath)) {
                AppLogger.w("FileTreeBuilder", "Path is not a directory: $rootPath")
                return null
            }
            buildTreeRecursive(rootPath, PathUtils.getFileName(rootPath), 0, maxDepth, fileSystem)
        } catch (e: Exception) {
            AppLogger.e("FileTreeBuilder", "Failed to build tree for path: $rootPath", e)
            null
        }
    }
    
    /**
     * Recursively builds a file tree node and its children.
     * 
     * This is an internal implementation detail. Use [buildTree] instead.
     * 
     * @param path The path of the current node
     * @param name The display name for this node
     * @param currentDepth Current recursion depth
     * @param maxDepth Maximum allowed depth
     * @param fileSystem The file system implementation
     * @return FileTreeNode for this path, or null if building failed or max depth reached
     */
    private fun buildTreeRecursive(
        path: String,
        name: String,
        currentDepth: Int,
        maxDepth: Int,
        fileSystem: FileSystem
    ): FileTreeNode? {
        if (currentDepth >= maxDepth) {
            AppLogger.w("FileTreeBuilder", "Max depth reached for path: $path")
            return null
        }
        
        return try {
            val node = FileTreeNode(
                path = path,
                name = name,
                isDirectory = fileSystem.isDirectory(path)
            )
            
            if (node.isDirectory) {
                val files = fileSystem.listFiles(path)
                for (filePath in files) {
                    val childName = PathUtils.getFileName(filePath)
                    val childNode = buildTreeRecursive(
                        filePath,
                        childName,
                        currentDepth + 1,
                        maxDepth,
                        fileSystem
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
            AppLogger.e("FileTreeBuilder", "Failed to build tree node for path: $path", e)
            null
        }
    }
    
}
