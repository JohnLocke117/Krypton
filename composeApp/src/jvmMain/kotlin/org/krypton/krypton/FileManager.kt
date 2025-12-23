package org.krypton.krypton

import org.krypton.krypton.files.FileOperations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object FileManager : FileOperations {
    override fun listFiles(directory: Path): List<Path> {
        return try {
            if (Files.exists(directory) && Files.isDirectory(directory)) {
                Files.list(directory)
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
                    .toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun readFile(file: Path): String? {
        return try {
            if (Files.exists(file) && Files.isRegularFile(file)) {
                Files.readString(file)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun writeFile(file: Path, content: String): Boolean {
        return try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun createFile(file: Path): Boolean {
        return try {
            if (!Files.exists(file)) {
                Files.createFile(file)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun isDirectory(path: Path): Boolean {
        return Files.isDirectory(path)
    }

    override fun isFile(path: Path): Boolean {
        return Files.isRegularFile(path)
    }
}

