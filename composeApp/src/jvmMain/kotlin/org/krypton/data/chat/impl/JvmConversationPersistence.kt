package org.krypton.data.chat.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.chat.conversation.*
import org.krypton.util.AppLogger
import org.krypton.util.createIdGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Registry mapping conversation IDs to vault IDs for efficient lookup.
 */
@Serializable
private data class ConversationRegistry(
    val conversationToVault: Map<String, String> = emptyMap()
)

/**
 * JVM implementation of ConversationRepository using java.nio.file.
 * 
 * Stores conversations in `.krypton/chat/` directory within vault.
 * Each conversation is stored as a separate JSON file: `{conversationId}.json`
 * An index file `index.json` contains summaries of all conversations.
 */
class JvmConversationPersistence : org.krypton.chat.conversation.ConversationRepository {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val idGenerator = createIdGenerator()
    
    // Cache for conversation summaries per vault
    private val vaultCaches = mutableMapOf<String, MutableStateFlow<List<ConversationSummary>>>()
    
    override fun observeConversations(vaultId: String): Flow<List<ConversationSummary>> {
        val cache = vaultCaches.getOrPut(vaultId) {
            MutableStateFlow(loadConversationIndex(vaultId))
        }
        return cache.asStateFlow()
    }
    
    override suspend fun getConversationMessages(conversationId: ConversationId): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val vaultId = findVaultIdForConversation(conversationId)
                ?: throw IllegalStateException("Could not find vault for conversation: ${conversationId.value}")
            
            val conversationData = loadConversationData(vaultId, conversationId)
            conversationData?.messages ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to load messages for conversation: ${conversationId.value}", e)
            emptyList()
        }
    }
    
    override suspend fun createConversation(
        vaultId: String,
        initialUserMessage: String,
        retrievalMode: String,
    ): ConversationId = withContext(Dispatchers.IO) {
        val conversationId = ConversationId(idGenerator.generateId())
        val now = System.currentTimeMillis()
        
        val title = generateTitle(initialUserMessage)
        val preview = initialUserMessage.take(100)
        
        val conversationData = ConversationData(
            id = conversationId,
            vaultId = vaultId,
            title = title,
            createdAt = now,
            updatedAt = now,
            retrievalMode = retrievalMode,
            messages = emptyList()
        )
        
        saveConversationData(vaultId, conversationData)
        updateRegistry(conversationId, vaultId)
        
        val summary = ConversationSummary(
            id = conversationId,
            title = title,
            createdAt = now,
            updatedAt = now,
            lastMessagePreview = preview,
            retrievalMode = retrievalMode
        )
        
        addToIndex(vaultId, summary)
        
        conversationId
    }
    
    override suspend fun appendMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        try {
            val vaultId = findVaultIdForConversation(message.conversationId)
                ?: throw IllegalStateException("Could not find vault for conversation: ${message.conversationId.value}")
            
            val conversationData = loadConversationData(vaultId, message.conversationId)
                ?: throw IllegalStateException("Conversation not found: ${message.conversationId.value}")
            
            val updatedData = conversationData.copy(
                messages = conversationData.messages + message,
                updatedAt = message.createdAt
            )
            
            saveConversationData(vaultId, updatedData)
            
            // Update index
            val preview = message.text.take(100)
            updateConversationSummary(
                message.conversationId,
                lastMessagePreview = preview,
                updatedAt = message.createdAt
            )
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to append message to conversation: ${message.conversationId.value}", e)
            throw e
        }
    }
    
    override suspend fun updateConversationSummary(
        conversationId: ConversationId,
        title: String?,
        lastMessagePreview: String?,
        updatedAt: Long,
    ) = withContext(Dispatchers.IO) {
        try {
            val vaultId = findVaultIdForConversation(conversationId)
                ?: throw IllegalStateException("Could not find vault for conversation: ${conversationId.value}")
            val index = loadConversationIndex(vaultId)
            val summaryIndex = index.indexOfFirst { it.id == conversationId }
            
            if (summaryIndex >= 0) {
                val existing = index[summaryIndex]
                val updated = existing.copy(
                    title = title ?: existing.title,
                    lastMessagePreview = lastMessagePreview ?: existing.lastMessagePreview,
                    updatedAt = updatedAt
                )
                
                val newIndex = index.toMutableList().apply {
                    this[summaryIndex] = updated
                    // Sort by updatedAt descending (most recent first)
                    sortByDescending { it.updatedAt }
                }
                
                saveConversationIndex(vaultId, ConversationIndex(newIndex))
                
                // Update cache
                vaultCaches[vaultId]?.value = newIndex
            }
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to update conversation summary: ${conversationId.value}", e)
        }
    }
    
    override suspend fun deleteConversation(conversationId: ConversationId) = withContext(Dispatchers.IO) {
        try {
            val vaultId = findVaultIdForConversation(conversationId)
                ?: throw IllegalStateException("Could not find vault for conversation: ${conversationId.value}")
            
            // Delete conversation file
            val conversationFile = getConversationFilePath(vaultId, conversationId)
            Files.deleteIfExists(conversationFile)
            
            // Remove from index
            val index = loadConversationIndex(vaultId)
            val newIndex = index.filter { it.id != conversationId }
            saveConversationIndex(vaultId, ConversationIndex(newIndex))
            
            // Update cache
            vaultCaches[vaultId]?.value = newIndex
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to delete conversation: ${conversationId.value}", e)
            throw e
        }
    }
    
    private fun loadConversationIndex(vaultId: String): List<ConversationSummary> {
        return try {
            val indexPath = getIndexPath(vaultId)
            if (Files.exists(indexPath) && Files.isRegularFile(indexPath)) {
                val content = Files.readString(indexPath)
                if (content.isBlank()) {
                    emptyList()
                } else {
                    val index = json.decodeFromString<ConversationIndex>(content)
                    return index.conversations.sortedByDescending { it.updatedAt }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to load conversation index for vault: $vaultId", e)
            emptyList()
        }
    }
    
    private fun saveConversationIndex(vaultId: String, index: ConversationIndex) {
        val indexPath = getIndexPath(vaultId)
        Files.createDirectories(indexPath.parent)
        
        val content = json.encodeToString(ConversationIndex.serializer(), index)
        Files.writeString(
            indexPath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
    
    private fun addToIndex(vaultId: String, summary: ConversationSummary) {
        val index = loadConversationIndex(vaultId)
        val newIndex = (index + summary).sortedByDescending { it.updatedAt }
        saveConversationIndex(vaultId, ConversationIndex(newIndex))
        
        // Update cache
        vaultCaches[vaultId]?.value = newIndex
    }
    
    private fun loadConversationData(vaultId: String, conversationId: ConversationId): ConversationData? {
        return try {
            val conversationFile = getConversationFilePath(vaultId, conversationId)
            if (Files.exists(conversationFile) && Files.isRegularFile(conversationFile)) {
                val content = Files.readString(conversationFile)
                if (content.isBlank()) {
                    null
                } else {
                    json.decodeFromString<ConversationData>(content)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to load conversation data: ${conversationId.value}", e)
            null
        }
    }
    
    private fun saveConversationData(vaultId: String, data: ConversationData) {
        val conversationFile = getConversationFilePath(vaultId, data.id)
        Files.createDirectories(conversationFile.parent)
        
        val content = json.encodeToString(ConversationData.serializer(), data)
        Files.writeString(
            conversationFile,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
    
    private fun getChatDirectory(vaultId: String): Path {
        val vaultPath = Paths.get(vaultId)
        return vaultPath.resolve(".krypton").resolve("chat")
    }
    
    private fun getIndexPath(vaultId: String): Path {
        return getChatDirectory(vaultId).resolve("index.json")
    }
    
    private fun getConversationFilePath(vaultId: String, conversationId: ConversationId): Path {
        return getChatDirectory(vaultId).resolve("${conversationId.value}.json")
    }
    
    private fun findVaultIdForConversation(conversationId: ConversationId): String? {
        // Load from registry if it exists
        val registryPath = getRegistryPath()
        if (Files.exists(registryPath)) {
            try {
                val content = Files.readString(registryPath)
                if (content.isNotBlank()) {
                    val registry = json.decodeFromString<ConversationRegistry>(content)
                    return registry.conversationToVault[conversationId.value]
                }
            } catch (e: Exception) {
                AppLogger.e("JvmConversationPersistence", "Failed to load conversation registry", e)
            }
        }
        
        // Fallback: search through common vault locations
        val homeDir = Paths.get(System.getProperty("user.home"))
        try {
            Files.walk(homeDir, 5).use { paths ->
                for (kryptonDir in paths.filter { it.fileName.toString() == ".krypton" }) {
                    val chatDir = kryptonDir.resolve("chat")
                    if (Files.exists(chatDir)) {
                        val conversationFile = chatDir.resolve("${conversationId.value}.json")
                        if (Files.exists(conversationFile)) {
                            // Extract vaultId from path: .../vault/.krypton/chat/...
                            val vaultPath = kryptonDir.parent
                            return vaultPath.toString()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to search for conversation", e)
        }
        
        return null
    }
    
    private fun getRegistryPath(): Path {
        val homeDir = Paths.get(System.getProperty("user.home"))
        return homeDir.resolve(".krypton").resolve("conversation-registry.json")
    }
    
    private fun updateRegistry(conversationId: ConversationId, vaultId: String) {
        try {
            val registryPath = getRegistryPath()
            Files.createDirectories(registryPath.parent)
            
            val existingRegistry = if (Files.exists(registryPath)) {
                try {
                    val content = Files.readString(registryPath)
                    if (content.isNotBlank()) {
                        json.decodeFromString<ConversationRegistry>(content)
                    } else {
                        ConversationRegistry()
                    }
                } catch (e: Exception) {
                    ConversationRegistry()
                }
            } else {
                ConversationRegistry()
            }
            
            val updatedRegistry = existingRegistry.copy(
                conversationToVault = existingRegistry.conversationToVault + (conversationId.value to vaultId)
            )
            
            val content = json.encodeToString(ConversationRegistry.serializer(), updatedRegistry)
            Files.writeString(
                registryPath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        } catch (e: Exception) {
            AppLogger.e("JvmConversationPersistence", "Failed to update conversation registry", e)
        }
    }
    
    private fun generateTitle(message: String): String {
        // Simple title generation: use first 50 chars of message
        val trimmed = message.trim()
        return if (trimmed.length <= 50) {
            trimmed
        } else {
            trimmed.take(47) + "..."
        }
    }
}


