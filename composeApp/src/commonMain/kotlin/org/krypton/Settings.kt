package org.krypton

import kotlinx.serialization.Serializable
import org.krypton.config.EditorDefaults

@Serializable
data class EditorSettings(
    val theme: String = EditorDefaults.DEFAULT_THEME,
    val fontFamily: String = EditorDefaults.DEFAULT_FONT_FAMILY,
    val fontSize: Int = EditorDefaults.DEFAULT_FONT_SIZE,
    val lineNumbers: Boolean = false,
    val wordWrap: Boolean = true,
    val tabSize: Int = EditorDefaults.DEFAULT_TAB_SIZE,
    val lineHeight: Float = EditorDefaults.DEFAULT_LINE_HEIGHT,
    val editorPadding: Int = EditorDefaults.DEFAULT_EDITOR_PADDING,
    val codeBlockFontSize: Int = EditorDefaults.DEFAULT_CODE_BLOCK_FONT_SIZE,
    val codeSpanFontSize: Int = EditorDefaults.DEFAULT_CODE_SPAN_FONT_SIZE
)

@Serializable
data class UISettings(
    val ribbonWidth: Int = org.krypton.config.UiDefaults.DEFAULT_RIBBON_WIDTH,
    val sidebarMinWidth: Int = org.krypton.config.UiDefaults.DEFAULT_SIDEBAR_MIN_WIDTH,
    val sidebarDefaultWidth: Int = org.krypton.config.UiDefaults.DEFAULT_SIDEBAR_DEFAULT_WIDTH,
    val sidebarMaxWidth: Int = org.krypton.config.UiDefaults.DEFAULT_SIDEBAR_MAX_WIDTH,
    val tabHeight: Int = org.krypton.config.UiDefaults.DEFAULT_TAB_HEIGHT,
    val tabPadding: Int = org.krypton.config.UiDefaults.DEFAULT_TAB_PADDING,
    val tabCornerRadius: Int = org.krypton.config.UiDefaults.DEFAULT_TAB_CORNER_RADIUS,
    val panelBorderWidth: Int = org.krypton.config.UiDefaults.DEFAULT_PANEL_BORDER_WIDTH,
    val panelPadding: Int = org.krypton.config.UiDefaults.DEFAULT_PANEL_PADDING,
    val tabFontSize: Int = org.krypton.config.UiDefaults.DEFAULT_TAB_FONT_SIZE,
    val tabLabelFontSize: Int = org.krypton.config.UiDefaults.DEFAULT_TAB_LABEL_FONT_SIZE,
    val fileExplorerFontSize: Int = org.krypton.config.UiDefaults.DEFAULT_FILE_EXPLORER_FONT_SIZE
)

@Serializable
data class ColorSettings(
    val background: String = "#202020",
    val backgroundElevated: String = "#252525",
    val backgroundHover: String = "#2A2A2A",
    val textPrimary: String = "#E0E0E0",
    val textSecondary: String = "#B0B0B0",
    val textTertiary: String = "#808080",
    val accent: String = "#7F6DF2",
    val accentHover: String = "#8F7DFF",
    val accentPressed: String = "#6F5DE2",
    val border: String = "#2D2D2D",
    val borderVariant: String = "#1A1A1A",
    val surface: String = "#252525",
    val surfaceVariant: String = "#2A2A2A",
    val surfaceContainer: String = "#2D2D2D",
    val selection: String = "#3A3A3A",
    val codeBlockBackground: String = "#1E1E1E",
    val codeBlockBorder: String = "#3A3A3A",
    val codeSpanBackground: String = "#2A2A2A",
    val linkColor: String = "#7F9DF2",
    val linkHover: String = "#8FAEFF",
    val blockquoteBackground: String = "#1F1F1F"
)

@Serializable
data class AppSettings(
    val recentFolders: List<String> = emptyList(),
    val autosaveIntervalSeconds: Int = EditorDefaults.DEFAULT_AUTOSAVE_INTERVAL_SECONDS,
    val telemetryEnabled: Boolean = false
)

@Serializable
enum class VectorBackend {
    CHROMADB,
    CHROMA_CLOUD
}

@Serializable
enum class LlmProvider {
    OLLAMA,
    GEMINI
}

@Serializable
data class LlmSettings(
    val provider: LlmProvider = LlmProvider.OLLAMA,
    val ollamaBaseUrl: String = org.krypton.config.RagDefaults.DEFAULT_LLM.baseUrl,
    val ollamaModel: String = org.krypton.config.RagDefaults.DEFAULT_LLM.modelName,
    val geminiModel: String = "gemini-2.5-flash"
)

@Serializable
data class RagSettings(
    val vectorBackend: VectorBackend = org.krypton.config.RagDefaults.DEFAULT_VECTOR_BACKEND,
    @Deprecated("Use Settings.llm.ollamaBaseUrl instead")
    val llamaBaseUrl: String? = null, // Deprecated, kept for backward compatibility
    val embeddingBaseUrl: String = org.krypton.config.RagDefaults.Embedding.DEFAULT_BASE_URL,
    val chromaBaseUrl: String = org.krypton.config.RagDefaults.ChromaDb.DEFAULT_BASE_URL,
    val chromaCollectionName: String = org.krypton.config.RagDefaults.ChromaDb.DEFAULT_COLLECTION_NAME,
    val chromaTenant: String = org.krypton.config.RagDefaults.ChromaDb.DEFAULT_TENANT,
    val chromaDatabase: String = org.krypton.config.RagDefaults.ChromaDb.DEFAULT_DATABASE,
    val ragEnabled: Boolean = true,
    @Deprecated("Use Settings.llm.ollamaModel instead")
    val llamaModel: String? = null, // Deprecated, kept for backward compatibility
    val embeddingModel: String = org.krypton.config.RagDefaults.Embedding.DEFAULT_MODEL,
    val topK: Int = org.krypton.config.RagDefaults.Retrieval.DEFAULT_TOP_K,
    val similarityThreshold: Float = org.krypton.config.RagDefaults.Retrieval.DEFAULT_SIMILARITY_THRESHOLD,
    val maxK: Int = org.krypton.config.RagDefaults.Retrieval.DEFAULT_MAX_K,
    val displayK: Int = org.krypton.config.RagDefaults.Retrieval.DEFAULT_DISPLAY_K,
    val queryRewritingEnabled: Boolean = false,
    val multiQueryEnabled: Boolean = false,
    val rerankingEnabled: Boolean = false,
    val rerankerModel: String? = org.krypton.config.RagDefaults.DEFAULT_RERANKER_MODEL
)

@Serializable
data class Settings(
    val version: Int = 1,
    val editor: EditorSettings = EditorSettings(),
    val ui: UISettings = UISettings(),
    val colors: ColorSettings = ColorSettings(),
    val app: AppSettings = AppSettings(),
    val rag: RagSettings = RagSettings(),
    val llm: LlmSettings = LlmSettings()
)

