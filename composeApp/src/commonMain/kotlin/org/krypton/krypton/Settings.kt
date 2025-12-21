package org.krypton.krypton

import kotlinx.serialization.Serializable

@Serializable
data class EditorSettings(
    val theme: String = "dark",
    val fontFamily: String = "JetBrains Mono",
    val fontSize: Int = 14,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val tabSize: Int = 4,
    val lineHeight: Float = 1.7f,
    val editorPadding: Int = 24,
    val codeBlockFontSize: Int = 13,
    val codeSpanFontSize: Int = 13
)

@Serializable
data class UISettings(
    val ribbonWidth: Int = 48,
    val sidebarMinWidth: Int = 200,
    val sidebarDefaultWidth: Int = 280,
    val sidebarMaxWidth: Int = 400,
    val tabHeight: Int = 36,
    val tabPadding: Int = 12,
    val tabCornerRadius: Int = 6,
    val panelBorderWidth: Int = 1,
    val panelPadding: Int = 8,
    val tabFontSize: Int = 13,
    val tabLabelFontSize: Int = 11,
    val fileExplorerFontSize: Int = 14
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
    val autosaveIntervalSeconds: Int = 60,
    val telemetryEnabled: Boolean = false
)

@Serializable
data class Settings(
    val version: Int = 1,
    val editor: EditorSettings = EditorSettings(),
    val ui: UISettings = UISettings(),
    val colors: ColorSettings = ColorSettings(),
    val app: AppSettings = AppSettings()
)

