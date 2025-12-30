package org.krypton

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

fun validateSettings(settings: Settings): ValidationResult {
    val errors = mutableListOf<String>()

    // Validate editor settings
    if (settings.editor.fontSize < 8 || settings.editor.fontSize > 72) {
        errors.add("Font size must be between 8 and 72")
    }

    if (settings.editor.tabSize < 1 || settings.editor.tabSize > 8) {
        errors.add("Tab size must be between 1 and 8")
    }

    if (settings.editor.fontFamily.isBlank()) {
        errors.add("Font family cannot be empty")
    }

    if (settings.editor.theme.isBlank()) {
        errors.add("Theme cannot be empty")
    }

    if (settings.editor.lineHeight < 1.0f || settings.editor.lineHeight > 3.0f) {
        errors.add("Line height must be between 1.0 and 3.0")
    }

    if (settings.editor.editorPadding < 0 || settings.editor.editorPadding > 48) {
        errors.add("Editor padding must be between 0 and 48")
    }

    if (settings.editor.codeBlockFontSize < 8 || settings.editor.codeBlockFontSize > 24) {
        errors.add("Code block font size must be between 8 and 24")
    }

    if (settings.editor.codeSpanFontSize < 8 || settings.editor.codeSpanFontSize > 24) {
        errors.add("Code span font size must be between 8 and 24")
    }

    // Validate app settings
    if (settings.app.autosaveIntervalSeconds < 0) {
        errors.add("Autosave interval must be non-negative")
    }

    if (settings.app.autosaveIntervalSeconds > 3600) {
        errors.add("Autosave interval must be at most 3600 seconds (1 hour)")
    }

    // Validate UI settings
    if (settings.ui.sidebarMinWidth < 100 || settings.ui.sidebarMinWidth > 500) {
        errors.add("Sidebar min width must be between 100 and 500")
    }

    if (settings.ui.sidebarDefaultWidth < settings.ui.sidebarMinWidth || 
        settings.ui.sidebarDefaultWidth > settings.ui.sidebarMaxWidth) {
        errors.add("Sidebar default width must be between min and max width")
    }

    if (settings.ui.sidebarMaxWidth < settings.ui.sidebarMinWidth || settings.ui.sidebarMaxWidth > 1000) {
        errors.add("Sidebar max width must be between min width and 1000")
    }

    if (settings.ui.tabHeight < 24 || settings.ui.tabHeight > 60) {
        errors.add("Tab height must be between 24 and 60")
    }

    if (settings.ui.tabFontSize < 8 || settings.ui.tabFontSize > 20) {
        errors.add("Tab font size must be between 8 and 20")
    }

    if (settings.ui.tabLabelFontSize < 8 || settings.ui.tabLabelFontSize > 20) {
        errors.add("Tab label font size must be between 8 and 20")
    }

    // Validate version
    if (settings.version < 1) {
        errors.add("Settings version must be at least 1")
    }

    return ValidationResult(
        isValid = errors.isEmpty(),
        errors = errors
    )
}

fun migrateSettings(settings: Settings): Settings {
    var migrated = settings

    // Migrate LLM settings from RagSettings to LlmSettings if needed
    // This handles backward compatibility when old settings have llamaBaseUrl/llamaModel in rag
    val llmSettings = if (settings.llm.provider == LlmProvider.OLLAMA && 
                          settings.llm.ollamaBaseUrl == org.krypton.config.RagDefaults.DEFAULT_LLM.baseUrl &&
                          settings.llm.ollamaModel == org.krypton.config.RagDefaults.DEFAULT_LLM.modelName) {
        // Check if we have old values in rag settings
        val oldBaseUrl = settings.rag.llamaBaseUrl
        val oldModel = settings.rag.llamaModel
        
        if (oldBaseUrl != null || oldModel != null) {
            // Migrate from old rag settings
            settings.llm.copy(
                ollamaBaseUrl = oldBaseUrl ?: settings.llm.ollamaBaseUrl,
                ollamaModel = oldModel ?: settings.llm.ollamaModel
            )
        } else {
            settings.llm
        }
    } else {
        settings.llm
    }
    
    if (llmSettings != settings.llm) {
        migrated = migrated.copy(llm = llmSettings)
    }

    // Handle version migrations
    when (migrated.version) {
        1 -> {
            // Current version, migration above handles LLM settings
        }
        // Future migrations can be added here:
        // 2 -> { ... }
        // 3 -> { ... }
        else -> {
            // Unknown version, apply defaults for missing fields
            // For now, just ensure version is updated
            migrated = migrated.copy(version = 1)
        }
    }

    return migrated
}

