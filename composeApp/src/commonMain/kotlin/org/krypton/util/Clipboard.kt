package org.krypton.util

/**
 * Platform-agnostic clipboard interface.
 * 
 * Note: On Android, this requires Context which should be obtained
 * from LocalContext in a composable.
 */
expect fun copyToClipboard(text: String)

