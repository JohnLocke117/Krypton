package org.krypton.krypton

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Helper function to parse hex color string to Color
 */
fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#").trim()
    return try {
        val colorInt = java.lang.Long.parseLong(cleanHex, 16).toInt()
        when (cleanHex.length) {
            6 -> Color(colorInt or 0xFF000000.toInt())
            8 -> Color(colorInt)
            else -> Color(0xFF202020) // Default fallback
        }
    } catch (e: Exception) {
        Color(0xFF202020) // Default fallback on error
    }
}

/**
 * Theme values derived from Settings
 * Use this composable to get theme values that react to settings changes
 */
@Composable
fun rememberObsidianTheme(settings: Settings): ObsidianThemeValues {
    return remember(settings) {
        ObsidianThemeValues(settings)
    }
}

class ObsidianThemeValues(private val settings: Settings) {
    // Colors from settings
    val Background: Color get() = parseHexColor(settings.colors.background)
    val BackgroundElevated: Color get() = parseHexColor(settings.colors.backgroundElevated)
    val BackgroundHover: Color get() = parseHexColor(settings.colors.backgroundHover)
    
    val TextPrimary: Color get() = parseHexColor(settings.colors.textPrimary)
    val TextSecondary: Color get() = parseHexColor(settings.colors.textSecondary)
    val TextTertiary: Color get() = parseHexColor(settings.colors.textTertiary)
    
    val Accent: Color get() = parseHexColor(settings.colors.accent)
    val AccentHover: Color get() = parseHexColor(settings.colors.accentHover)
    val AccentPressed: Color get() = parseHexColor(settings.colors.accentPressed)
    
    val Border: Color get() = parseHexColor(settings.colors.border)
    val BorderVariant: Color get() = parseHexColor(settings.colors.borderVariant)
    
    val Surface: Color get() = parseHexColor(settings.colors.surface)
    val SurfaceVariant: Color get() = parseHexColor(settings.colors.surfaceVariant)
    val SurfaceContainer: Color get() = parseHexColor(settings.colors.surfaceContainer)
    
    val Selection: Color get() = parseHexColor(settings.colors.selection)
    val SelectionAccent: Color get() = Accent.copy(alpha = 0.3f)
    
    // Spacing from settings
    val RibbonWidth: androidx.compose.ui.unit.Dp get() = settings.ui.ribbonWidth.dp
    val SidebarMinWidth: androidx.compose.ui.unit.Dp get() = settings.ui.sidebarMinWidth.dp
    val SidebarDefaultWidth: androidx.compose.ui.unit.Dp get() = settings.ui.sidebarDefaultWidth.dp
    val SidebarMaxWidth: androidx.compose.ui.unit.Dp get() = settings.ui.sidebarMaxWidth.dp
    
    val TabHeight: androidx.compose.ui.unit.Dp get() = settings.ui.tabHeight.dp
    val TabPadding: androidx.compose.ui.unit.Dp get() = settings.ui.tabPadding.dp
    val TabCornerRadius: androidx.compose.ui.unit.Dp get() = settings.ui.tabCornerRadius.dp
    
    val EditorPadding: androidx.compose.ui.unit.Dp get() = settings.editor.editorPadding.dp
    val EditorLineHeight: Float get() = settings.editor.lineHeight
    
    val PanelBorderWidth: androidx.compose.ui.unit.Dp get() = settings.ui.panelBorderWidth.dp
    val PanelPadding: androidx.compose.ui.unit.Dp get() = settings.ui.panelPadding.dp
    
    // Markdown-specific colors
    val CodeBlockBackground: Color get() = parseHexColor(settings.colors.codeBlockBackground)
    val CodeBlockBorder: Color get() = parseHexColor(settings.colors.codeBlockBorder)
    val CodeSpanBackground: Color get() = parseHexColor(settings.colors.codeSpanBackground)
    val LinkColor: Color get() = parseHexColor(settings.colors.linkColor)
    val LinkHover: Color get() = parseHexColor(settings.colors.linkHover)
    val BlockquoteBorder: Color get() = Accent.copy(alpha = 0.6f)
    val BlockquoteBackground: Color get() = parseHexColor(settings.colors.blockquoteBackground)
    val HeadingColor: Color get() = TextPrimary
    val HeadingColorSecondary: Color get() = TextSecondary
}

// Compatibility object for components that haven't been migrated yet
// Uses default settings
object ObsidianTheme {
    private val defaultSettings = Settings()
    private val defaultTheme = ObsidianThemeValues(defaultSettings)
    
    val Background: Color get() = defaultTheme.Background
    val BackgroundElevated: Color get() = defaultTheme.BackgroundElevated
    val BackgroundHover: Color get() = defaultTheme.BackgroundHover
    val TextPrimary: Color get() = defaultTheme.TextPrimary
    val TextSecondary: Color get() = defaultTheme.TextSecondary
    val TextTertiary: Color get() = defaultTheme.TextTertiary
    val Accent: Color get() = defaultTheme.Accent
    val AccentHover: Color get() = defaultTheme.AccentHover
    val AccentPressed: Color get() = defaultTheme.AccentPressed
    val Border: Color get() = defaultTheme.Border
    val BorderVariant: Color get() = defaultTheme.BorderVariant
    val Surface: Color get() = defaultTheme.Surface
    val SurfaceVariant: Color get() = defaultTheme.SurfaceVariant
    val SurfaceContainer: Color get() = defaultTheme.SurfaceContainer
    val Selection: Color get() = defaultTheme.Selection
    val SelectionAccent: Color get() = defaultTheme.SelectionAccent
    val RibbonWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.RibbonWidth
    val SidebarMinWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarMinWidth
    val SidebarDefaultWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarDefaultWidth
    val SidebarMaxWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarMaxWidth
    val TabHeight: androidx.compose.ui.unit.Dp get() = defaultTheme.TabHeight
    val TabPadding: androidx.compose.ui.unit.Dp get() = defaultTheme.TabPadding
    val TabCornerRadius: androidx.compose.ui.unit.Dp get() = defaultTheme.TabCornerRadius
    val EditorPadding: androidx.compose.ui.unit.Dp get() = defaultTheme.EditorPadding
    val EditorLineHeight: Float get() = defaultTheme.EditorLineHeight
    val PanelBorderWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.PanelBorderWidth
    val PanelPadding: androidx.compose.ui.unit.Dp get() = defaultTheme.PanelPadding
    val CodeBlockBackground: Color get() = defaultTheme.CodeBlockBackground
    val CodeBlockBorder: Color get() = defaultTheme.CodeBlockBorder
    val CodeSpanBackground: Color get() = defaultTheme.CodeSpanBackground
    val LinkColor: Color get() = defaultTheme.LinkColor
    val LinkHover: Color get() = defaultTheme.LinkHover
    val BlockquoteBorder: Color get() = defaultTheme.BlockquoteBorder
    val BlockquoteBackground: Color get() = defaultTheme.BlockquoteBackground
    val HeadingColor: Color get() = defaultTheme.HeadingColor
    val HeadingColorSecondary: Color get() = defaultTheme.HeadingColorSecondary
}

fun buildColorSchemeFromSettings(settings: Settings): ColorScheme {
    val colors = ObsidianThemeValues(settings)
    val theme = settings.editor.theme.lowercase()
    
    return when {
        theme == "light" -> lightColorScheme(
            primary = colors.Accent,
            onPrimary = Color.White,
            primaryContainer = colors.Accent.copy(alpha = 0.2f),
            onPrimaryContainer = colors.Accent,
            
            secondary = colors.Accent,
            onSecondary = Color.White,
            
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF1A1A1A),
            
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF4A4A4A),
            surfaceContainerHighest = Color(0xFFE0E0E0),
            
            outline = Color(0xFFCCCCCC),
            outlineVariant = Color(0xFFE5E5E5),
            
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
        else -> darkColorScheme(
            primary = colors.Accent,
            onPrimary = Color.White,
            primaryContainer = colors.Accent.copy(alpha = 0.2f),
            onPrimaryContainer = colors.Accent,
            
            secondary = colors.Accent,
            onSecondary = Color.White,
            
            background = colors.Background,
            onBackground = colors.TextPrimary,
            
            surface = colors.Surface,
            onSurface = colors.TextPrimary,
            surfaceVariant = colors.SurfaceVariant,
            onSurfaceVariant = colors.TextSecondary,
            surfaceContainerHighest = colors.SurfaceContainer,
            
            outline = colors.Border,
            outlineVariant = colors.BorderVariant,
            
            error = Color(0xFFCF6679),
            onError = Color.White
        )
    }
}
