package org.krypton.krypton.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.krypton.krypton.util.AppLogger

/**
 * Service for checking ChromaDB health and connectivity.
 */
class ChromaDBHealthService(
    private val baseUrl: String,
    private val collectionName: String,
    private val httpClientEngine: HttpClientEngine,
    private val tenant: String = "default",
    private val database: String = "defaultDB"
) {
    
    private val collectionsBasePath = "$baseUrl/api/v2/tenants/$tenant/databases/$database/collections"
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }
    
    /**
     * Checks the health of ChromaDB.
     * 
     * @return HealthStatus indicating if ChromaDB is healthy and accessible
     */
    suspend fun checkHealth(): HealthStatus = withContext(Dispatchers.IO) {
        try {
            // First check healthcheck endpoint
            val healthcheckResponse = client.get("$baseUrl/api/v2/healthcheck")
            if (healthcheckResponse.status != HttpStatusCode.OK) {
                AppLogger.w("ChromaDBHealthService", "Healthcheck failed: ${healthcheckResponse.status}")
                return@withContext HealthStatus.UNHEALTHY
            }
            
            // Then check if collection exists and is accessible (get collection ID from name)
            val collectionResponse = client.get("$collectionsBasePath/$collectionName")
            if (collectionResponse.status == HttpStatusCode.OK) {
                AppLogger.d("ChromaDBHealthService", "ChromaDB is healthy")
                return@withContext HealthStatus.HEALTHY
            } else if (collectionResponse.status == HttpStatusCode.NotFound) {
                // Collection doesn't exist yet, but DB is accessible
                // This is still considered healthy (collection will be created on first use)
                AppLogger.d("ChromaDBHealthService", "ChromaDB is healthy (collection will be created on first use)")
                return@withContext HealthStatus.HEALTHY
            } else {
                AppLogger.w("ChromaDBHealthService", "Collection check failed: ${collectionResponse.status}")
                return@withContext HealthStatus.UNHEALTHY
            }
        } catch (e: Exception) {
            AppLogger.e("ChromaDBHealthService", "Health check failed: ${e.message}", e)
            return@withContext HealthStatus.UNHEALTHY
        }
    }
    
    /**
     * Checks if the collection is accessible.
     * 
     * @return true if collection exists and is accessible, false otherwise
     */
    suspend fun isCollectionAccessible(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$collectionsBasePath/$collectionName")
            return@withContext response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            AppLogger.e("ChromaDBHealthService", "Collection accessibility check failed: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Closes the HTTP client.
     */
    fun close() {
        client.close()
    }
}

