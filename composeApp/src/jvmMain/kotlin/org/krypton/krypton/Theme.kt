package org.krypton.krypton

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    // Use AppThemeColors internally for backward compatibility
    private val themeColors = AppThemeColors(settings)
    
    // Colors from theme (with Settings override support)
    val Background: Color get() = themeColors.backgroundColor
    val BackgroundElevated: Color get() = themeColors.elevatedSurfaceColor
    val BackgroundHover: Color get() = themeColors.elevatedSurfaceColor
    
    val TextPrimary: Color get() = themeColors.textPrimaryColor
    val TextSecondary: Color get() = themeColors.textSecondaryColor
    val TextTertiary: Color get() = themeColors.textTertiaryColor
    
    val Accent: Color get() = themeColors.accentColor
    val AccentHover: Color get() = themeColors.accentHoverColor
    val AccentPressed: Color get() = themeColors.accentPressedColor
    
    val Border: Color get() = themeColors.borderColor
    val BorderVariant: Color get() = themeColors.borderVariantColor
    
    val Surface: Color get() = themeColors.surfaceColor
    val SurfaceVariant: Color get() = themeColors.elevatedSurfaceColor
    val SurfaceContainer: Color get() = themeColors.surfaceContainerColor
    
    val Selection: Color get() = themeColors.selectionColor
    val SelectionAccent: Color get() = themeColors.selectionAccentColor
    
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
    
    // Design tokens from AppDimens
    val IconSizeSmall: androidx.compose.ui.unit.Dp get() = AppDimens.IconSizeSmall.dp
    val IconSizeMedium: androidx.compose.ui.unit.Dp get() = AppDimens.IconSizeMedium.dp
    val IconSizeLarge: androidx.compose.ui.unit.Dp get() = AppDimens.IconSizeLarge.dp
    
    val SidebarItemHeight: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarItemHeight.dp
    val SidebarIndentPerLevel: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarIndentPerLevel.dp
    val SidebarHorizontalPadding: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarHorizontalPadding.dp
    val SidebarVerticalPadding: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarVerticalPadding.dp
    val SidebarIconTextSpacing: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarIconTextSpacing.dp
    val SidebarChevronWidth: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarChevronWidth.dp
    val SidebarSeparatorHeight: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarSeparatorHeight.dp
    val SidebarSectionHeight: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarSectionHeight.dp
    val SidebarItemSpacing: androidx.compose.ui.unit.Dp get() = AppDimens.SidebarItemSpacing.dp
    
    // Markdown-specific colors
    val CodeBlockBackground: Color get() = themeColors.codeBlockBackgroundColor
    val CodeBlockBorder: Color get() = themeColors.codeBlockBorderColor
    val CodeSpanBackground: Color get() = themeColors.codeSpanBackgroundColor
    val LinkColor: Color get() = themeColors.linkColor
    val LinkHover: Color get() = themeColors.linkHoverColor
    val BlockquoteBorder: Color get() = themeColors.blockquoteBorderColor
    val BlockquoteBackground: Color get() = themeColors.blockquoteBackgroundColor
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
    val IconSizeSmall: androidx.compose.ui.unit.Dp get() = defaultTheme.IconSizeSmall
    val IconSizeMedium: androidx.compose.ui.unit.Dp get() = defaultTheme.IconSizeMedium
    val IconSizeLarge: androidx.compose.ui.unit.Dp get() = defaultTheme.IconSizeLarge
    val SidebarItemHeight: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarItemHeight
    val SidebarIndentPerLevel: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarIndentPerLevel
    val SidebarHorizontalPadding: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarHorizontalPadding
    val SidebarVerticalPadding: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarVerticalPadding
    val SidebarIconTextSpacing: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarIconTextSpacing
    val SidebarChevronWidth: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarChevronWidth
    val SidebarSeparatorHeight: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarSeparatorHeight
    val SidebarSectionHeight: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarSectionHeight
    val SidebarItemSpacing: androidx.compose.ui.unit.Dp get() = defaultTheme.SidebarItemSpacing
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

/**
 * Alpha value for primary container overlay.
 */
private const val PRIMARY_CONTAINER_ALPHA = 0.2f

/**
 * Light theme color constants (not using Catppuccin as it's a dark theme).
 */
private object LightThemeColors {
    val Background = Color(0xFFFAFAFA)
    val OnBackground = Color(0xFF1A1A1A)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF5F5F5)
    val OnSurfaceVariant = Color(0xFF4A4A4A)
    val SurfaceContainerHighest = Color(0xFFE0E0E0)
    val Outline = Color(0xFFCCCCCC)
    val OutlineVariant = Color(0xFFE5E5E5)
    val Error = Color(0xFFBA1A1A)
    val OnError = Color.White
}

fun buildColorSchemeFromSettings(settings: Settings): ColorScheme {
    val themeColors = AppThemeColors(settings)
    val theme = settings.editor.theme.lowercase()
    
    return when {
        theme == "light" -> lightColorScheme(
            primary = themeColors.accentColor,
            onPrimary = CatppuccinMochaColors.Base,
            primaryContainer = themeColors.accentColor.copy(alpha = PRIMARY_CONTAINER_ALPHA),
            onPrimaryContainer = themeColors.accentColor,
            
            secondary = themeColors.accentColor,
            onSecondary = CatppuccinMochaColors.Base,
            
            background = LightThemeColors.Background,
            onBackground = LightThemeColors.OnBackground,
            
            surface = LightThemeColors.Surface,
            onSurface = LightThemeColors.OnBackground,
            surfaceVariant = LightThemeColors.SurfaceVariant,
            onSurfaceVariant = LightThemeColors.OnSurfaceVariant,
            surfaceContainerHighest = LightThemeColors.SurfaceContainerHighest,
            
            outline = LightThemeColors.Outline,
            outlineVariant = LightThemeColors.OutlineVariant,
            
            error = LightThemeColors.Error,
            onError = LightThemeColors.OnError
        )
        else -> darkColorScheme(
            primary = themeColors.accentColor,
            onPrimary = CatppuccinMochaColors.Base,
            primaryContainer = themeColors.accentColor.copy(alpha = PRIMARY_CONTAINER_ALPHA),
            onPrimaryContainer = themeColors.accentColor,
            
            secondary = themeColors.accentColor,
            onSecondary = CatppuccinMochaColors.Base,
            
            background = CatppuccinMochaColors.Base,
            onBackground = themeColors.textPrimaryColor,
            
            surface = themeColors.surfaceColor,
            onSurface = themeColors.textPrimaryColor,
            surfaceVariant = themeColors.elevatedSurfaceColor,
            onSurfaceVariant = themeColors.textSecondaryColor,
            surfaceContainerHighest = themeColors.surfaceContainerColor,
            
            outline = themeColors.borderColor,
            outlineVariant = themeColors.borderVariantColor,
            
            error = themeColors.errorColor,
            onError = CatppuccinMochaColors.Base
        )
    }
}
