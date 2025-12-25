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
     * Ensures tenant exists, creating it if necessary.
     * 
     * @throws Exception if tenant creation fails
     */
    suspend fun ensureTenant(): Unit = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("IngestionPipeline", "Checking if tenant '$tenant' exists...")
            
            // Check if tenant exists
            val checkResponse = client.get("$baseUrl/api/v2/tenants/$tenant")
            
            if (checkResponse.status == HttpStatusCode.OK) {
                AppLogger.i("IngestionPipeline", "✓ Tenant '$tenant' already exists")
                return@withContext
            }
            
            // Tenant doesn't exist, create it
            if (checkResponse.status == HttpStatusCode.NotFound) {
                AppLogger.i("IngestionPipeline", "Tenant '$tenant' does not exist. Creating...")
                
                val createRequest = CreateTenantRequest(name = tenant)
                val createResponse = client.post("$baseUrl/api/v2/tenants") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }
                
                if (createResponse.status == HttpStatusCode.OK || createResponse.status == HttpStatusCode.Created) {
                    AppLogger.i("IngestionPipeline", "✓ Tenant '$tenant' created successfully")
                } else {
                    val errorBody = createResponse.body<String>()
                    val errorMsg = "Failed to create tenant '$tenant': ${createResponse.status} - $errorBody"
                    AppLogger.e("IngestionPipeline", "✗ $errorMsg")
                    throw Exception(errorMsg)
                }
            } else {
                val errorBody = checkResponse.body<String>()
                val errorMsg = "Failed to check tenant '$tenant': ${checkResponse.status} - $errorBody"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg")
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to ensure tenant '$tenant': ${e.message}"
            AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
            throw Exception(errorMsg, e)
        }
    }
    
    /**
     * Ensures database exists in the tenant, creating it if necessary.
     * 
     * @throws Exception if database creation fails
     */
    suspend fun ensureDatabase(): Unit = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("IngestionPipeline", "Checking if database '$database' exists in tenant '$tenant'...")
            
            // Check if database exists
            val checkResponse = client.get("$baseUrl/api/v2/tenants/$tenant/databases/$database")
            
            if (checkResponse.status == HttpStatusCode.OK) {
                AppLogger.i("IngestionPipeline", "✓ Database '$database' already exists in tenant '$tenant'")
                return@withContext
            }
            
            // Database doesn't exist, create it
            if (checkResponse.status == HttpStatusCode.NotFound) {
                AppLogger.i("IngestionPipeline", "Database '$database' does not exist in tenant '$tenant'. Creating...")
                
                val createRequest = CreateDatabaseRequest(name = database)
                val createResponse = client.post("$baseUrl/api/v2/tenants/$tenant/databases") {
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }
                
                if (createResponse.status == HttpStatusCode.OK || createResponse.status == HttpStatusCode.Created) {
                    AppLogger.i("IngestionPipeline", "✓ Database '$database' created successfully in tenant '$tenant'")
                } else {
                    val errorBody = createResponse.body<String>()
                    val errorMsg = "Failed to create database '$database' in tenant '$tenant': ${createResponse.status} - $errorBody"
                    AppLogger.e("IngestionPipeline", "✗ $errorMsg")
                    throw Exception(errorMsg)
                }
            } else {
                val errorBody = checkResponse.body<String>()
                val errorMsg = "Failed to check database '$database' in tenant '$tenant': ${checkResponse.status} - $errorBody"
                AppLogger.e("IngestionPipeline", "✗ $errorMsg")
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to ensure database '$database' in tenant '$tenant': ${e.message}"
            AppLogger.e("IngestionPipeline", "✗ $errorMsg", e)
            throw Exception(errorMsg, e)
        }
    }
    
    /**
     * Ensures tenant and database exist before starting ingestion.
     * This is a blocking operation - if any step fails, an exception is thrown.
     * 
     * @throws Exception if tenant or database creation/check fails
     */
    suspend fun ensureTenantAndDatabase(): Unit = withContext(Dispatchers.IO) {
        AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
        AppLogger.i("IngestionPipeline", "Ensuring ChromaDB tenant and database exist...")
        AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
        
        try {
            // Step 1: Ensure tenant exists
            ensureTenant()
            
            // Step 2: Ensure database exists (only if tenant was successfully created/verified)
            ensureDatabase()
            
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            AppLogger.i("IngestionPipeline", "✓ Tenant and database verification complete")
            AppLogger.i("IngestionPipeline", "═══════════════════════════════════════════════════════════")
        } catch (e: Exception) {
            AppLogger.e("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            AppLogger.e("IngestionPipeline", "✗ Ingestion Pipeline Failed: ${e.message}")
            AppLogger.e("IngestionPipeline", "═══════════════════════════════════════════════════════════")
            throw e
        }
    }
    
    /**
     * Closes the HTTP client.
     */
    fun close() {
        client.close()
    }
}

// ChromaDB API request models for tenant and database

@Serializable
private data class CreateTenantRequest(
    val name: String
)

@Serializable
private data class CreateDatabaseRequest(
    val name: String
)

