package org.krypton.krypton

import org.krypton.krypton.config.UiDefaults

/**
 * Centralized typography tokens for consistent text styling across the app.
 * Values are based on VS Code's typography for reference.
 * 
 * Font sizes are Int values that should be converted to sp in platform-specific code:
 * `AppTypography.BaseFontSize.sp`
 * 
 * @deprecated Use UiDefaults directly instead. This object is kept for backward compatibility.
 */
@Deprecated("Use UiDefaults directly", ReplaceWith("UiDefaults"))
object AppTypography {
    /** Base font size (13sp) - matches VS Code's default font size */
    const val BaseFontSize = UiDefaults.DEFAULT_TAB_FONT_SIZE
    
    // Font weights (these are conceptual - actual weights come from Material3)
    // Normal weight for body text
    // Medium weight for emphasis
    // Bold weight for strong emphasis
    // Note: Actual font weights are handled by MaterialTheme.typography in jvmMain
}

