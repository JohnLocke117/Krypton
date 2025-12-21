package org.krypton.krypton

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ObsidianTheme {
    // Obsidian-inspired color palette
    val Background = Color(0xFF202020)
    val BackgroundElevated = Color(0xFF252525)
    val BackgroundHover = Color(0xFF2A2A2A)
    
    val TextPrimary = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFFB0B0B0)
    val TextTertiary = Color(0xFF808080)
    
    val Accent = Color(0xFF7F6DF2) // Purple accent
    val AccentHover = Color(0xFF8F7DFF)
    val AccentPressed = Color(0xFF6F5DE2)
    
    val Border = Color(0xFF2D2D2D)
    val BorderVariant = Color(0xFF1A1A1A)
    
    val Surface = Color(0xFF252525)
    val SurfaceVariant = Color(0xFF2A2A2A)
    val SurfaceContainer = Color(0xFF2D2D2D)
    
    val Selection = Color(0xFF3A3A3A)
    val SelectionAccent = Accent.copy(alpha = 0.3f)
    
    // Spacing constants
    val RibbonWidth = 48.dp
    val SidebarMinWidth = 200.dp
    val SidebarDefaultWidth = 280.dp
    val SidebarMaxWidth = 400.dp
    
    val TabHeight = 36.dp
    val TabPadding = 12.dp
    val TabCornerRadius = 6.dp
    
    val EditorPadding = 24.dp
    val EditorLineHeight = 1.7f
    
    val PanelBorderWidth = 1.dp
    val PanelPadding = 8.dp
}

fun obsidianDarkColorScheme() = darkColorScheme(
    primary = ObsidianTheme.Accent,
    onPrimary = Color.White,
    primaryContainer = ObsidianTheme.Accent.copy(alpha = 0.2f),
    onPrimaryContainer = ObsidianTheme.Accent,
    
    secondary = ObsidianTheme.Accent,
    onSecondary = Color.White,
    
    background = ObsidianTheme.Background,
    onBackground = ObsidianTheme.TextPrimary,
    
    surface = ObsidianTheme.Surface,
    onSurface = ObsidianTheme.TextPrimary,
    surfaceVariant = ObsidianTheme.SurfaceVariant,
    onSurfaceVariant = ObsidianTheme.TextSecondary,
    surfaceContainerHighest = ObsidianTheme.SurfaceContainer,
    
    outline = ObsidianTheme.Border,
    outlineVariant = ObsidianTheme.BorderVariant,
    
    error = Color(0xFFCF6679),
    onError = Color.White
)

