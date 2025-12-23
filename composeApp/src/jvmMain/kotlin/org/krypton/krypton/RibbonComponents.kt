package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class RibbonOrientation {
    Vertical,
    Horizontal
}

@Composable
internal fun RibbonSlot(
    content: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    orientation: RibbonOrientation,
    ribbonSize: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = modifier
            .then(
                if (orientation == RibbonOrientation.Vertical) {
                    Modifier
                        .height(AppDimens.RibbonUnit.dp)
                        .width(ribbonSize)
                } else {
                    Modifier
                        .width(AppDimens.RibbonUnit.dp)
                        .height(ribbonSize)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content?.invoke()
    }
}

@Composable
fun Ribbon(
    orientation: RibbonOrientation,
    slots: List<@Composable (() -> Unit)?>,
    modifier: Modifier = Modifier,
    ribbonSize: androidx.compose.ui.unit.Dp = 40.dp
) {
    val backgroundModifier = if (orientation == RibbonOrientation.Vertical) {
        Modifier
            .fillMaxHeight()
            .width(ribbonSize)
    } else {
        Modifier
            .fillMaxWidth()
            .height(ribbonSize)
    }
    
    when (orientation) {
        RibbonOrientation.Vertical -> {
            Column(
                modifier = modifier
                    .then(backgroundModifier)
                    .background(CatppuccinMochaColors.Base),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { slotContent ->
                    RibbonSlot(
                        content = slotContent,
                        orientation = orientation,
                        ribbonSize = ribbonSize
                    )
                }
            }
        }
        RibbonOrientation.Horizontal -> {
            Row(
                modifier = modifier
                    .then(backgroundModifier)
                    .background(CatppuccinMochaColors.Base),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slots.forEach { slotContent ->
                    RibbonSlot(
                        content = slotContent,
                        orientation = orientation,
                        ribbonSize = ribbonSize
                    )
                }
            }
        }
    }
}

@Composable
fun TopRibbon(
    modifier: Modifier = Modifier
) {
    Ribbon(
        orientation = RibbonOrientation.Horizontal,
        slots = listOf(null), // Empty slot
        modifier = modifier,
        ribbonSize = 40.dp
    )
}

@Composable
fun BottomRibbon(
    modifier: Modifier = Modifier
) {
    Ribbon(
        orientation = RibbonOrientation.Horizontal,
        slots = listOf(null), // Empty slot
        modifier = modifier,
        ribbonSize = 40.dp
    )
}

@Composable
internal fun RibbonToggleButton(
    iconOpen: DrawableResource,
    iconClose: DrawableResource,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Image(
            painter = painterResource(if (isOpen) iconClose else iconOpen),
            contentDescription = if (isOpen) "Close panel" else "Open panel",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant)
        )
    }
}

enum class CardFacingEdge {
    Start,  // Left for vertical, Top for horizontal
    End,    // Right for vertical, Bottom for horizontal
    Top,    // Top for vertical
    Bottom  // Bottom for vertical
}

@Composable
internal fun RibbonIconButton(
    icon: DrawableResource,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardFacingEdge: CardFacingEdge = CardFacingEdge.End,
    cardFacingPadding: androidx.compose.ui.unit.Dp = 4.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val activePadding = if (isActive) {
        // Add padding on both card-facing edge and screen-facing edge
        val cardPadding = when (cardFacingEdge) {
            CardFacingEdge.Start -> Modifier.padding(start = cardFacingPadding)
            CardFacingEdge.End -> Modifier.padding(end = cardFacingPadding)
            CardFacingEdge.Top -> Modifier.padding(top = cardFacingPadding)
            CardFacingEdge.Bottom -> Modifier.padding(bottom = cardFacingPadding)
        }
        // Screen-facing edge is opposite of card-facing edge
        val screenPadding = when (cardFacingEdge) {
            CardFacingEdge.Start -> Modifier.padding(end = cardFacingPadding)
            CardFacingEdge.End -> Modifier.padding(start = cardFacingPadding)
            CardFacingEdge.Top -> Modifier.padding(bottom = cardFacingPadding)
            CardFacingEdge.Bottom -> Modifier.padding(top = cardFacingPadding)
        }
        cardPadding.then(screenPadding)
    } else {
        Modifier
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(activePadding)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isActive) {
                        colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                )
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center),
                colorFilter = if (isActive) {
                    ColorFilter.tint(colorScheme.primary)
                } else {
                    ColorFilter.tint(colorScheme.onSurfaceVariant)
                }
            )
        }
    }
}

