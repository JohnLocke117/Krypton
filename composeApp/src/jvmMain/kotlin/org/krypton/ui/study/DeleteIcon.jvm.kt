package org.krypton.ui.study

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close

@Composable
actual fun DeleteIcon(
    contentDescription: String?,
    modifier: Modifier,
    tint: Color?
) {
    Image(
        painter = painterResource(Res.drawable.close),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = tint?.let { ColorFilter.tint(it) }
    )
}

