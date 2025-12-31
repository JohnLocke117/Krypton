package org.krypton.util

/**
 * Platform-agnostic path utility functions.
 * 
 * These functions provide common path operations without relying on
 * platform-specific APIs like java.nio.file.Path.
 */
object PathUtils {
    /**
     * Gets the file name from a path string.
     * 
     * @param path The full path
     * @return The file name (last component of the path)
     */
    fun getFileName(path: String): String {
        return path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: path
    }
    
    /**
     * Gets the parent directory path.
     * 
     * @param path The full path
     * @return The parent path, or null if path has no parent
     */
    fun getParent(path: String): String? {
        val parent = path.substringBeforeLast("/")
        return parent.takeIf { it != path && it.isNotEmpty() }
    }
    
    /**
     * Joins path components together.
     * 
     * @param parts Path components to join
     * @return Joined path string
     */
    fun join(vararg parts: String): String {
        return parts.filter { it.isNotEmpty() }.joinToString("/")
    }
    
    /**
     * Normalizes a path by removing redundant separators and resolving ".." and "." components.
     * 
     * @param path The path to normalize
     * @return Normalized path
     */
    fun normalize(path: String): String {
        if (path.isEmpty()) return path
        
        val parts = path.split("/").filter { it.isNotEmpty() && it != "." }
        val result = mutableListOf<String>()
        
        for (part in parts) {
            when (part) {
                ".." -> {
                    if (result.isNotEmpty() && result.last() != "..") {
                        result.removeLast()
                    } else {
                        result.add(part)
                    }
                }
                else -> result.add(part)
            }
        }
        
        return if (path.startsWith("/")) {
            "/${result.joinToString("/")}"
        } else {
            result.joinToString("/")
        }
    }
    
    /**
     * Gets the file extension from a path.
     * 
     * @param path The full path
     * @return The file extension (without the dot), or empty string if no extension
     */
    fun getExtension(path: String): String {
        val fileName = getFileName(path)
        return fileName.substringAfterLast(".", "")
    }
    
    /**
     * Checks if a path is absolute (starts with "/" on Unix-like systems).
     * 
     * @param path The path to check
     * @return True if the path appears to be absolute
     */
    fun isAbsolute(path: String): Boolean {
        return path.startsWith("/")
    }
    
    /**
     * Gets a relative path from a base path to a target path.
     * 
     * This function computes the relative path needed to navigate from basePath to targetPath.
     * Both paths are normalized before computation. If the paths are the same, returns ".".
     * 
     * @param basePath The base directory path
     * @param targetPath The target file/directory path
     * @return The relative path from base to target. Returns "." if paths are the same,
     *         or the normalized target path if they're not related (different roots).
     */
    fun getRelativePath(basePath: String, targetPath: String): String {
        if (basePath.isEmpty() || targetPath.isEmpty()) {
            return targetPath
        }
        
        return try {
            val normalizedBase = normalize(basePath.trimEnd('/'))
            val normalizedTarget = normalize(targetPath.trimEnd('/'))
            
            if (normalizedBase == normalizedTarget) {
                return "."
            }
            
            // Check if paths have the same root (both absolute or both relative)
            val baseIsAbsolute = isAbsolute(normalizedBase)
            val targetIsAbsolute = isAbsolute(normalizedTarget)
            
            if (baseIsAbsolute != targetIsAbsolute) {
                // Different root types, return normalized target
                return normalizedTarget
            }
            
            val baseParts = normalizedBase.split("/").filter { it.isNotEmpty() }
            val targetParts = normalizedTarget.split("/").filter { it.isNotEmpty() }
            
            // Find common prefix
            var commonLength = 0
            val minLength = minOf(baseParts.size, targetParts.size)
            while (commonLength < minLength && baseParts[commonLength] == targetParts[commonLength]) {
                commonLength++
            }
            
            // Build relative path
            val upLevels = baseParts.size - commonLength
            val relativeParts = targetParts.drop(commonLength)
            
            val result = if (upLevels == 0 && relativeParts.isEmpty()) {
                "."
            } else {
                val upPath = "../".repeat(upLevels)
                val downPath = relativeParts.joinToString("/")
                if (upPath.isEmpty()) {
                    if (downPath.isEmpty()) "." else downPath
                } else {
                    upPath + downPath
                }
            }
            
            result
        } catch (e: Exception) {
            // If normalization or path computation fails, return the target path as fallback
            targetPath
        }
    }
}

