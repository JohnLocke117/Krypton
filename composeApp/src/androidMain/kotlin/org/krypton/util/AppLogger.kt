package org.krypton.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Android implementation of timestamp formatter.
 * Returns ISO-8601 formatted timestamp in UTC.
 */
actual fun getCurrentTimestamp(): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.format(Date())
    } catch (e: Exception) {
        // Fallback if datetime formatting fails
        "0000-00-00T00:00:00.000Z"
    }
}

