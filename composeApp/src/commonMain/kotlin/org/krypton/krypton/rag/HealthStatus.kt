package org.krypton.krypton.rag

/**
 * Represents the health status of the vector database.
 */
enum class HealthStatus {
    /**
     * Database is healthy and accessible.
     */
    HEALTHY,
    
    /**
     * Database is unhealthy or inaccessible.
     */
    UNHEALTHY
}

