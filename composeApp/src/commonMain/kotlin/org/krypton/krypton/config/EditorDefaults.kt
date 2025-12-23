package org.krypton.krypton.config

/**
 * Default configuration values for editor functionality.
 * 
 * These defaults are used when user settings are missing or invalid.
 */
object EditorDefaults {
    /**
     * Default autosave interval in seconds.
     */
    const val DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 60
    
    /**
     * Minimum autosave interval in seconds (enforced minimum).
     */
    const val MIN_AUTOSAVE_INTERVAL_SECONDS = 5
    
    /**
     * Maximum number of recent folders to remember.
     */
    const val DEFAULT_RECENT_FOLDERS_LIMIT = 5
    
    /**
     * Maximum number of open tabs allowed.
     */
    const val DEFAULT_MAX_OPEN_TABS = 20
    
    /**
     * Default file extensions that the editor recognizes (for filtering).
     */
    val DEFAULT_SUPPORTED_EXTENSIONS = listOf(".md", ".txt", ".markdown", ".json")
    
    /**
     * Default markdown file extensions for RAG indexing.
     */
    val DEFAULT_MARKDOWN_EXTENSIONS = listOf(".md", ".markdown")
    
    /**
     * Default theme name.
     */
    const val DEFAULT_THEME = "dark"
    
    /**
     * Default font family.
     */
    const val DEFAULT_FONT_FAMILY = "JetBrains Mono"
    
    /**
     * Default font size in pixels.
     */
    const val DEFAULT_FONT_SIZE = 14
    
    /**
     * Default tab size (number of spaces).
     */
    const val DEFAULT_TAB_SIZE = 4
    
    /**
     * Default line height multiplier.
     */
    const val DEFAULT_LINE_HEIGHT = 1.7f
    
    /**
     * Default editor padding in pixels.
     */
    const val DEFAULT_EDITOR_PADDING = 24
    
    /**
     * Default code block font size in pixels.
     */
    const val DEFAULT_CODE_BLOCK_FONT_SIZE = 13
    
    /**
     * Default code span font size in pixels.
     */
    const val DEFAULT_CODE_SPAN_FONT_SIZE = 13
}

