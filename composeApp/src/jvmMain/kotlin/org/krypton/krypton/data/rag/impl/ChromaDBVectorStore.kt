package org.krypton.krypton.data.rag.impl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.krypton.krypton.rag.NoteChunk
import org.krypton.krypton.rag.VectorStore
import org.krypton.krypton.util.AppLogger

/**
 * ChromaDB vector store implementation using HTTP API.
 * 
 * This implementation:
 * 1. Connects to ChromaDB running in Docker container
 * 2. Stores embeddings, documents, and metadata in ChromaDB collections
 * 3. Uses ChromaDB's built-in cosine similarity search
 * 
 * @param baseUrl Base URL of ChromaDB server (e.g., "http://localhost:8000")
 * @param collectionName Name of the ChromaDB collection to use
 * @param httpClientEngine HTTP client engine for making requests
 */
class ChromaDBVectorStore(
    private val baseUrl: String,
    private val collectionName: String,
    private val httpClientEngine: HttpClientEngine,
    private val tenant: String = "default",
    private val database: String = "defaultDB"
) : VectorStore {
    
    private val collectionsBasePath = "$baseUrl/api/v2/tenants/$tenant/databases/$database/collections"
    
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }
    
    // Cache collection ID to avoid repeated lookups
    private var collectionId: String? = null
    
    /**
     * Gets collection ID from collection name.
     */
    private suspend fun getCollectionIdFromName(collectionName: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$collectionsBasePath/$collectionName")
            if (response.status == HttpStatusCode.OK) {
                val collectionResponse: CollectionResponse = response.body()
                return@withContext collectionResponse.id
            }
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }
    
    /**
     * Ensures the collection exists, creating it if necessary, and caches the collection ID.
     */
    private suspend fun ensureCollection() = withContext(Dispatchers.IO) {
        // If we already have the collection ID cached, use it
        if (collectionId != null) {
            return@withContext
        }
        
        try {
            // Try to get the collection ID from name
            val id = getCollectionIdFromName(collectionName)
            if (id != null) {
                collectionId = id
                return@withContext
            }
            
            // Collection doesn't exist, create it
            createCollection()
        } catch (e: Exception) {
            // Collection doesn't exist or error, try to create it
            try {
                createCollection()
            } catch (createException: Exception) {
                AppLogger.e("ChromaDBVectorStore", "Failed to ensure collection exists: ${createException.message}", createException)
                throw ChromaDBException("Failed to connect to ChromaDB at $baseUrl: ${createException.message}", createException)
            }
        }
    }
    
    /**
     * Creates a new collection in ChromaDB and caches the collection ID.
     */
    private suspend fun createCollection() = withContext(Dispatchers.IO) {
        val request = CreateCollectionRequest(
            name = collectionName,
            get_or_create = true,
            metadata = null,
            configuration = null,
            schema = null
        )
        
        val response = client.post(collectionsBasePath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val errorBody = response.body<String>()
            throw ChromaDBException("Failed to create collection: ${response.status} - $errorBody")
        }
        
        // Parse collection ID from response
        val collectionResponse: CollectionResponse = response.body()
        collectionId = collectionResponse.id
        
        AppLogger.i("ChromaDBVectorStore", "Collection Created Successfully")
        AppLogger.i("ChromaDBVectorStore", "  Collection Name: $collectionName")
        AppLogger.i("ChromaDBVectorStore", "  Collection ID: ${collectionResponse.id}")
    }
    
    override suspend fun upsert(chunks: List<NoteChunk>) = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) return@withContext
        
        try {
            // Ensure collection exists
            ensureCollection()
            
            // Prepare data for ChromaDB
            val ids = chunks.map { it.id }
            val embeddings = chunks.mapNotNull { chunk ->
                chunk.embedding?.toList() // Convert FloatArray to List<Float>
            }
            val documents = chunks.map { it.text }
            val metadatas = chunks.map { chunk ->
                buildJsonObject {
                    put("filePath", JsonPrimitive(chunk.filePath))
                    put("startLine", JsonPrimitive(chunk.startLine.toString()))
                    put("endLine", JsonPrimitive(chunk.endLine.toString()))
                }
            }
            
            // Validate that all chunks have embeddings
            if (embeddings.size != chunks.size) {
                throw ChromaDBException("Cannot upsert chunks without embeddings")
            }
            
            // Ensure we have collection ID
            if (collectionId == null) {
                ensureCollection()
            }
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for upsert")
            
            val request = AddRequest(
                ids = ids,
                embeddings = embeddings,
                documents = documents,
                metadatas = metadatas,
                uris = null
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/add") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            // ChromaDB v2 API returns 201 Created for successful add operations
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = response.body<String>()
                throw ChromaDBException("Failed to upsert chunks: ${response.status} - $errorBody")
            }
            
            AppLogger.d("ChromaDBVectorStore", "Upserted ${chunks.size} chunks into collection '$collectionName'")
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to upsert ${chunks.size} chunks", e)
            throw ChromaDBException("Failed to upsert chunks: ${e.message}", e)
        }
    }
    
    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<NoteChunk> = 
        withContext(Dispatchers.IO) {
            try {
                ensureCollection()
                
                val collectionIdToUse = collectionId
                    ?: throw ChromaDBException("Collection ID not available for search")
                
                // Log query execution
                AppLogger.d("ChromaDBVectorStore", "Running Query on Collection")
                AppLogger.d("ChromaDBVectorStore", "  Collection Name: $collectionName")
                AppLogger.d("ChromaDBVectorStore", "  Collection ID: $collectionIdToUse")
                AppLogger.d("ChromaDBVectorStore", "  Top K: $topK")
                AppLogger.d("ChromaDBVectorStore", "  Query Embedding Dimension: ${queryEmbedding.size}")
                
                val request = QueryRequest(
                    query_embeddings = listOf(queryEmbedding.toList()),
                    n_results = topK,
                    where = null,
                    where_document = null,
                    ids = null,
                    include = listOf("documents", "metadatas", "distances")
                )
                
                val response = client.post("$collectionsBasePath/$collectionIdToUse/query") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = response.body<String>()
                    throw ChromaDBException("Failed to search: ${response.status} - $errorBody")
                }
                
                val queryResponse: QueryResponse = response.body()
                
                // Convert ChromaDB response to NoteChunk list
                val chunks = mutableListOf<NoteChunk>()
                
                if (queryResponse.ids != null && queryResponse.ids.isNotEmpty()) {
                    val ids = queryResponse.ids[0] // First query embedding results
                    val documents = queryResponse.documents?.get(0) ?: emptyList()
                    val metadatas = queryResponse.metadatas?.get(0) ?: emptyList()
                    val distances = queryResponse.distances?.get(0) ?: emptyList()
                    
                    for (i in ids.indices) {
                        val id = ids[i]
                        val document = documents.getOrNull(i) ?: ""
                        val metadata = metadatas.getOrNull(i) ?: buildJsonObject { }
                        
                        // Note: ChromaDB doesn't return embeddings in query results by default
                        // We'll need to store them separately or fetch them if needed
                        // For now, we'll create chunks without embeddings (they can be re-embedded if needed)
                        val chunk = NoteChunk(
                            id = id,
                            filePath = (metadata["filePath"] as? JsonPrimitive)?.content ?: "",
                            startLine = ((metadata["startLine"] as? JsonPrimitive)?.content)?.toIntOrNull() ?: 0,
                            endLine = ((metadata["endLine"] as? JsonPrimitive)?.content)?.toIntOrNull() ?: 0,
                            text = document,
                            embedding = null // ChromaDB doesn't return embeddings in query results
                        )
                        chunks.add(chunk)
                    }
                }
                
                AppLogger.d("ChromaDBVectorStore", "Query Completed Successfully")
                AppLogger.d("ChromaDBVectorStore", "  Results Returned: ${chunks.size} chunks")
                return@withContext chunks
            } catch (e: Exception) {
                AppLogger.e("ChromaDBVectorStore", "Failed to search (query size: ${queryEmbedding.size}, topK: $topK)", e)
                // Return empty list on error rather than crashing
                return@withContext emptyList()
            }
        }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available for clear")
            
            // Delete collection with body containing new collection info
            val deleteRequest = DeleteCollectionRequest(
                new_name = collectionName,
                new_metadata = null,
                new_configuration = null
            )
            
            val deleteResponse = client.delete("$collectionsBasePath/$collectionIdToUse") {
                contentType(ContentType.Application.Json)
                setBody(deleteRequest)
            }
            
            if (deleteResponse.status == HttpStatusCode.OK || deleteResponse.status == HttpStatusCode.NoContent) {
                // Clear cached ID and recreate the collection
                collectionId = null
                createCollection()
                AppLogger.d("ChromaDBVectorStore", "Cleared collection '$collectionName'")
            } else {
                throw ChromaDBException("Failed to clear collection: ${deleteResponse.status}")
            }
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to clear vector store", e)
            throw ChromaDBException("Failed to clear collection: ${e.message}", e)
        }
    }
    
    override suspend fun deleteByFilePath(filePath: String) = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            // ChromaDB v2 requires collection ID (UUID) for delete endpoint
            // If we don't have the ID yet, try to get it
            var idToUse = collectionId
            if (idToUse == null) {
                try {
                    val getResponse = client.get("$collectionsBasePath/$collectionName")
                    if (getResponse.status == HttpStatusCode.OK) {
                        val collectionResponse: CollectionResponse = getResponse.body()
                        idToUse = collectionResponse.id
                        collectionId = idToUse
                    }
                } catch (e: Exception) {
                    AppLogger.w("ChromaDBVectorStore", "Could not get collection ID for delete: ${e.message}")
                }
            }
            
            // If we still don't have an ID, skip deletion (best effort)
            if (idToUse == null) {
                AppLogger.w("ChromaDBVectorStore", "Skipping delete for $filePath - collection ID not available")
                return@withContext
            }
            
            // Create JsonObject for where clause
            val whereJson = buildJsonObject {
                put("filePath", JsonPrimitive(filePath))
            }
            
            val request = DeleteRequest(
                ids = null,
                where = whereJson,
                where_document = null
            )
            
            val response = client.post("$collectionsBasePath/$idToUse/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.body<String>()
                // Log warning but don't throw - deletion is best effort for cleanup
                AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file $filePath: ${response.status} - $errorBody")
                return@withContext
            }
            
            AppLogger.d("ChromaDBVectorStore", "Deleted chunks for file: $filePath")
        } catch (e: Exception) {
            // Log warning but don't throw - deletion is best effort for cleanup
            AppLogger.w("ChromaDBVectorStore", "Failed to delete chunks for file: $filePath - ${e.message}")
            // Don't throw exception - this is just cleanup before re-indexing
        }
    }
    
    /**
     * Checks the health of the ChromaDB connection and collection.
     * 
     * @return true if healthy, false otherwise
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check healthcheck endpoint
            val healthcheckResponse = client.get("$baseUrl/api/v2/healthcheck")
            if (healthcheckResponse.status != HttpStatusCode.OK) {
                return@withContext false
            }
            
            // Check if collection exists
            val collectionResponse = client.get("$collectionsBasePath/$collectionName")
            return@withContext collectionResponse.status == HttpStatusCode.OK || 
                    collectionResponse.status == HttpStatusCode.NotFound // NotFound is OK, will be created
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Health check failed: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Gets all file paths that are indexed in the collection.
     * 
     * @return List of unique file paths
     */
    suspend fun getFilesInCollection(): List<String> = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            
            // Query with a dummy embedding to get all documents
            // We'll use a zero vector to get all results
            val zeroVector = FloatArray(384) { 0f } // Assuming 384-dim embeddings (nomic-embed-text)
            
            val collectionIdToUse = collectionId
                ?: throw ChromaDBException("Collection ID not available")
            
            val request = QueryRequest(
                query_embeddings = listOf(zeroVector.toList()),
                n_results = 10000, // Large number to get all files
                where = null,
                where_document = null,
                ids = null,
                include = listOf("metadatas")
            )
            
            val response = client.post("$collectionsBasePath/$collectionIdToUse/query") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status != HttpStatusCode.OK) {
                return@withContext emptyList()
            }
            
            val queryResponse: QueryResponse = response.body()
            val filePaths = mutableSetOf<String>()
            
            queryResponse.metadatas?.get(0)?.forEach { metadata ->
                val filePath = (metadata["filePath"] as? JsonPrimitive)?.content
                if (filePath != null) {
                    filePaths.add(filePath)
                }
            }
            
            return@withContext filePaths.toList()
        } catch (e: Exception) {
            AppLogger.e("ChromaDBVectorStore", "Failed to get files in collection: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Closes the HTTP client and releases resources.
     */
    fun close() {
        client.close()
    }
}

/**
 * Exception thrown when ChromaDB operations fail.
 */
class ChromaDBException(message: String, cause: Throwable? = null) : Exception(message, cause)

// ChromaDB API request/response models

@Serializable
private data class CreateCollectionRequest(
    val name: String,
    val get_or_create: Boolean = false,
    val metadata: Map<String, String>? = null,
    val configuration: Map<String, String>? = null,
    val schema: Map<String, String>? = null
)

@Serializable
private data class AddRequest(
    val ids: List<String>,
    val embeddings: List<List<Float>>,
    val documents: List<String>,
    @Contextual val metadatas: List<JsonObject>,
    val uris: List<String>? = null
)

@Serializable
private data class QueryRequest(
    val query_embeddings: List<List<Float>>,
    val n_results: Int,
    @Contextual val where: JsonObject? = null,
    @Contextual val where_document: JsonObject? = null,
    val ids: List<String>? = null,
    val include: List<String> = listOf("documents", "metadatas", "distances")
)

@Serializable
private data class QueryResponse(
    val ids: List<List<String>>? = null,
    val embeddings: List<List<List<Float>>>? = null,
    val documents: List<List<String>>? = null,
    @Contextual val metadatas: List<List<JsonObject>>? = null,
    val distances: List<List<Float>>? = null
)

@Serializable
private data class CollectionResponse(
    val id: String,
    val name: String,
    val metadata: Map<String, String>? = null
)

@Serializable
private data class DeleteRequest(
    val ids: List<String>? = null,
    @Contextual val where: JsonObject? = null,
    @Contextual val where_document: JsonObject? = null
)

@Serializable
private data class DeleteCollectionRequest(
    val new_name: String? = null,
    val new_metadata: Map<String, String>? = null,
    val new_configuration: Map<String, String>? = null
)

