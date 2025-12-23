package org.krypton.krypton

import org.krypton.krypton.config.UiDefaults

/**
 * Centralized design dimension tokens for consistent sizing across the app.
 * Values are based on VS Code's Explorer sidebar for reference.
 * 
 * These are Int values that should be converted to Dp in platform-specific code:
 * `AppDimens.IconSizeSmall.dp`
 * 
 * @deprecated Use UiDefaults directly instead. This object is kept for backward compatibility.
 */
@Deprecated("Use UiDefaults directly", ReplaceWith("UiDefaults"))
object AppDimens {
    // Icon sizes
    /** Small icon size (16px) - used for sidebar file/folder icons, matching VS Code */
    const val IconSizeSmall = UiDefaults.DEFAULT_ICON_SIZE_SMALL
    
    /** Medium icon size (20px) - used for chevrons/arrows in tree views */
    const val IconSizeMedium = UiDefaults.DEFAULT_ICON_SIZE_MEDIUM
    
    /** Large icon size (24px) - used for larger UI elements */
    const val IconSizeLarge = UiDefaults.DEFAULT_ICON_SIZE_LARGE
    
    // Sidebar spacing
    /** Fixed row height for sidebar items (30px) - increased to accommodate larger padding */
    const val SidebarItemHeight = UiDefaults.DEFAULT_SIDEBAR_ITEM_HEIGHT
    
    /** Indent per tree depth level (12px) - reduced for tighter hierarchy */
    const val SidebarIndentPerLevel = UiDefaults.DEFAULT_SIDEBAR_INDENT_PER_LEVEL
    
    /** Horizontal padding for sidebar items (8px) - left/right padding */
    const val SidebarHorizontalPadding = UiDefaults.DEFAULT_SIDEBAR_HORIZONTAL_PADDING
    
    /** Vertical padding for sidebar items (5px) - top/bottom padding per item */
    const val SidebarVerticalPadding = UiDefaults.DEFAULT_SIDEBAR_VERTICAL_PADDING
    
    /** Spacing between icon and text in sidebar (6px) */
    const val SidebarIconTextSpacing = UiDefaults.DEFAULT_SIDEBAR_ICON_TEXT_SPACING
    
    /** Width reserved for chevron/arrow in tree view (20px) */
    const val SidebarChevronWidth = UiDefaults.DEFAULT_SIDEBAR_CHEVRON_WIDTH
    
    // Separators
    /** Height of separator/divider between sections (1px) */
    const val SidebarSeparatorHeight = UiDefaults.DEFAULT_SIDEBAR_SEPARATOR_HEIGHT
    
    // Sidebar section bars
    /** Fixed height for top and bottom sidebar bars (40px) */
    const val SidebarSectionHeight = UiDefaults.DEFAULT_SIDEBAR_SECTION_HEIGHT
    
    /** Vertical spacing between items within sidebar bars (8px) */
    const val SidebarItemSpacing = UiDefaults.DEFAULT_SIDEBAR_ITEM_SPACING
    
    // Ribbon spacing
    /** Ribbon unit size (32px) - represents one vertical slot, equals icon size (24px) + top padding (4px) + bottom padding (4px) */
    const val RibbonUnit = UiDefaults.DEFAULT_RIBBON_UNIT
}

