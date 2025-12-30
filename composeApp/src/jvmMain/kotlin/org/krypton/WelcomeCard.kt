package org.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.polyline

@Composable
fun WelcomeCard(
    onNewFile: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .widthIn(max = 600.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant // Surface0 for welcome card
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App logo/icon
            Image(
                painter = painterResource(Res.drawable.polyline),
                contentDescription = "Krypton",
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Welcome to Krypton",
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "A modern text editor with Obsidian-inspired design",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Button(
                onClick = onOpenFolder,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Open Folder",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onPrimary
                )
            }
            
            OutlinedButton(
                onClick = onNewFile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.onSurface
                )
            ) {
                Text(
                    text = "New File",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

