package org.krypton.krypton

/**
 * Centralized design dimension tokens for consistent sizing across the app.
 * Values are based on VS Code's Explorer sidebar for reference.
 * 
 * These are Int values that should be converted to Dp in platform-specific code:
 * `AppDimens.IconSizeSmall.dp`
 */
object AppDimens {
    // Icon sizes
    /** Small icon size (16px) - used for sidebar file/folder icons, matching VS Code */
    const val IconSizeSmall = 16
    
    /** Medium icon size (20px) - used for chevrons/arrows in tree views */
    const val IconSizeMedium = 20
    
    /** Large icon size (24px) - used for larger UI elements */
    const val IconSizeLarge = 24
    
    // Sidebar spacing
    /** Fixed row height for sidebar items (30px) - increased to accommodate larger padding */
    const val SidebarItemHeight = 30
    
    /** Indent per tree depth level (12px) - reduced for tighter hierarchy */
    const val SidebarIndentPerLevel = 12
    
    /** Horizontal padding for sidebar items (8px) - left/right padding */
    const val SidebarHorizontalPadding = 8
    
    /** Vertical padding for sidebar items (5px) - top/bottom padding per item */
    const val SidebarVerticalPadding = 5
    
    /** Spacing between icon and text in sidebar (6px) */
    const val SidebarIconTextSpacing = 6
    
    /** Width reserved for chevron/arrow in tree view (20px) */
    const val SidebarChevronWidth = 20
    
    // Separators
    /** Height of separator/divider between sections (1px) */
    const val SidebarSeparatorHeight = 1
}

