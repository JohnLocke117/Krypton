package org.krypton.krypton

/**
 * Centralized typography tokens for consistent text styling across the app.
 * Values are based on VS Code's typography for reference.
 * 
 * Font sizes are Int values that should be converted to sp in platform-specific code:
 * `AppTypography.BaseFontSize.sp`
 */
object AppTypography {
    /** Base font size (13sp) - matches VS Code's default font size */
    const val BaseFontSize = 13
    
    // Font weights (these are conceptual - actual weights come from Material3)
    // Normal weight for body text
    // Medium weight for emphasis
    // Bold weight for strong emphasis
    // Note: Actual font weights are handled by MaterialTheme.typography in jvmMain
}

