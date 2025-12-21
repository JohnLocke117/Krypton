package org.krypton.krypton

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
import krypton.composeapp.generated.resources.Atom

@Composable
fun WelcomeCard(
    onNewFile: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(max = 600.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianTheme.BackgroundElevated
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
                painter = painterResource(Res.drawable.Atom),
                contentDescription = "Krypton",
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Welcome to Krypton",
                style = MaterialTheme.typography.headlineMedium,
                color = ObsidianTheme.TextPrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "A modern text editor with Obsidian-inspired design",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Button(
                onClick = onOpenFolder,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianTheme.Accent
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Open Folder",
                    style = MaterialTheme.typography.labelLarge,
                    color = ObsidianTheme.TextPrimary
                )
            }
            
            OutlinedButton(
                onClick = onNewFile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ObsidianTheme.TextPrimary
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

