package org.krypton.krypton.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * JVM implementation of timestamp formatter.
 * Returns ISO-8601 formatted timestamp in UTC.
 */
actual fun getCurrentTimestamp(): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
        formatter.format(Instant.now())
    } catch (e: Exception) {
        // Fallback if datetime formatting fails
        "0000-00-00T00:00:00.000Z"
    }
}

