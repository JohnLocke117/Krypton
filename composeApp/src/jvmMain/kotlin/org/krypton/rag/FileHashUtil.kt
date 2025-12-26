package org.krypton.rag

import org.krypton.util.AppLogger
import java.io.File
import java.security.MessageDigest

/**
 * Utility for computing file content hashes.
 */
object FileHashUtil {
    /**
     * Computes SHA-256 hash of a file's content.
     * 
     * @param file The file to hash
     * @return Hash string in format "sha256:<hex_string>", or empty string on error
     */
    fun computeFileHash(file: File): String {
        return try {
            if (!file.exists() || !file.isFile) {
                AppLogger.w("FileHashUtil", "File does not exist or is not a file: ${file.path}")
                return ""
            }
            
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            "sha256:$hexString"
        } catch (e: Exception) {
            AppLogger.e("FileHashUtil", "Failed to compute hash for ${file.path}: ${e.message}", e)
            ""
        }
    }
}

