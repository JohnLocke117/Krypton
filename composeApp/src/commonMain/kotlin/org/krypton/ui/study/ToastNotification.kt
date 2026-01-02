package org.krypton.ui.study

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.krypton.CatppuccinMochaColors

/**
 * Toast notification composable that displays success/error messages.
 * Auto-dismisses after a delay.
 */
@Composable
fun ToastNotification(
    toastMessage: ToastMessage?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = toastMessage != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        toastMessage?.let { toast ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = when (toast.type) {
                    ToastType.SUCCESS -> CatppuccinMochaColors.Green
                    ToastType.ERROR -> CatppuccinMochaColors.Red
                },
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = toast.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Base,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

