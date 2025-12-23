package org.krypton.krypton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Alpha values for color overlays and selections.
 */
private object ColorAlpha {
    const val PRIMARY_CONTAINER = 0.2f
    const val SELECTION_ACCENT = 0.3f
    const val BLOCKQUOTE_BORDER = 0.6f
}

/**
 * Helper function to parse hex color string to Color.
 */
fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#").trim()
    return try {
        val colorInt = java.lang.Long.parseLong(cleanHex, 16).toInt()
        when (cleanHex.length) {
            6 -> Color(colorInt or 0xFF000000.toInt())
            8 -> Color(colorInt)
            else -> CatppuccinMochaColors.Base
        }
    } catch (e: Exception) {
        CatppuccinMochaColors.Base
    }
}

/**
 * Semantic color mappings that reference Catppuccin Mocha colors.
 * This provides a loosely-coupled layer where semantic names map to theme colors.
 * To change the theme, update the mappings here rather than throughout the codebase.
 */
class AppThemeColors(private val settings: Settings? = null) {
    // Background colors
    // Overall app background - Base
    val backgroundColor: Color
        get() = settings?.colors?.background?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Base
    
    // Editor/main content area - Mantle
    val surfaceColor: Color
        get() = settings?.colors?.surface?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Mantle
    
    // Elevated surfaces - Surface0
    val elevatedSurfaceColor: Color
        get() = settings?.colors?.backgroundElevated?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface0
    
    val surfaceContainerColor: Color
        get() = settings?.colors?.surfaceContainer?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface2
    
    // Text colors
    val textPrimaryColor: Color
        get() = settings?.colors?.textPrimary?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Text
    
    val textSecondaryColor: Color
        get() = settings?.colors?.textSecondary?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Subtext1
    
    val textTertiaryColor: Color
        get() = settings?.colors?.textTertiary?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Subtext0
    
    // Accent colors
    val accentColor: Color
        get() = settings?.colors?.accent?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Blue
    
    val accentHoverColor: Color
        get() = settings?.colors?.accentHover?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Sapphire
    
    val accentPressedColor: Color
        get() = settings?.colors?.accentPressed?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Sky
    
    // Border colors
    val borderColor: Color
        get() = settings?.colors?.border?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface2
    
    val borderVariantColor: Color
        get() = settings?.colors?.borderVariant?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface1
    
    // Error color
    val errorColor: Color
        get() = CatppuccinMochaColors.Red
    
    // Selection colors
    val selectionColor: Color
        get() = settings?.colors?.selection?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface1
    
    val selectionAccentColor: Color
        get() = accentColor.copy(alpha = ColorAlpha.SELECTION_ACCENT)
    
    // Markdown-specific colors
    val codeBlockBackgroundColor: Color
        get() = settings?.colors?.codeBlockBackground?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Mantle
    
    val codeBlockBorderColor: Color
        get() = settings?.colors?.codeBlockBorder?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface2
    
    val codeSpanBackgroundColor: Color
        get() = settings?.colors?.codeSpanBackground?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Surface1
    
    val linkColor: Color
        get() = settings?.colors?.linkColor?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Blue
    
    val linkHoverColor: Color
        get() = settings?.colors?.linkHover?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Sapphire
    
    val blockquoteBackgroundColor: Color
        get() = settings?.colors?.blockquoteBackground?.let { parseHexColor(it) }
            ?: CatppuccinMochaColors.Mantle
    
    val blockquoteBorderColor: Color
        get() = accentColor.copy(alpha = ColorAlpha.BLOCKQUOTE_BORDER)
}

/**
 * Non-Material colors for custom UI components (sidebars, file tree, etc.)
 * These map directly to Catppuccin Mocha colors for a VS Code-like appearance.
 */
data class AppColors(
    val sidebarBackground: Color = CatppuccinMochaColors.Crust,
    val sidebarActiveItem: Color = CatppuccinMochaColors.Blue.copy(alpha = ColorAlpha.SELECTION_ACCENT),
    val sidebarBorder: Color = CatppuccinMochaColors.Surface2,
    val treeHighlight: Color = CatppuccinMochaColors.Surface1,
    val statusBarBackground: Color = CatppuccinMochaColors.Crust,
    val ribbonBackground: Color = CatppuccinMochaColors.Base,
    val hoverBackground: Color = CatppuccinMochaColors.Surface0,
    val editorBackground: Color = CatppuccinMochaColors.Mantle
)

/**
 * CompositionLocal for accessing AppColors throughout the composition tree.
 */
val LocalAppColors = compositionLocalOf { AppColors() }

/**
 * Creates AppColors from AppThemeColors, allowing customization based on theme.
 */
@Composable
fun rememberAppColors(themeColors: AppThemeColors): AppColors {
    return remember(themeColors) {
        AppColors(
            sidebarBackground = CatppuccinMochaColors.Crust,
            sidebarActiveItem = themeColors.selectionAccentColor,
            sidebarBorder = themeColors.borderColor,
            treeHighlight = themeColors.selectionColor,
            statusBarBackground = CatppuccinMochaColors.Crust,
            ribbonBackground = CatppuccinMochaColors.Base,
            hoverBackground = themeColors.elevatedSurfaceColor,
            editorBackground = CatppuccinMochaColors.Mantle
        )
    }
}

