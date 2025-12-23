package org.krypton.krypton.config

/**
 * Default configuration values for search functionality.
 * 
 * These defaults are used when search is initialized.
 */
object SearchDefaults {
    /**
     * Default case sensitivity setting.
     */
    const val DEFAULT_MATCH_CASE = false
    
    /**
     * Default whole words only setting.
     */
    const val DEFAULT_WHOLE_WORDS = false
    
    /**
     * Default regex mode setting.
     */
    const val DEFAULT_USE_REGEX = false
    
    /**
     * Default maximum number of search results to highlight.
     */
    const val DEFAULT_MAX_HIGHLIGHT_RESULTS = 1000
}

