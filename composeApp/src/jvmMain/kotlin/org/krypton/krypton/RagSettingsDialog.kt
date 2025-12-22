package org.krypton.krypton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.krypton.krypton.VectorBackend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onReindex: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vector Backend
        Text(
            text = "Vector Backend",
            style = MaterialTheme.typography.bodyLarge,
            color = ObsidianTheme.TextPrimary
        )
        var expanded by remember { mutableStateOf(false) }
        val backends = VectorBackend.values()
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = settings.rag.vectorBackend.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Vector Backend") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ObsidianTheme.TextPrimary,
                    unfocusedTextColor = ObsidianTheme.TextPrimary,
                    focusedBorderColor = ObsidianTheme.Accent,
                    unfocusedBorderColor = ObsidianTheme.Border
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                backends.forEach { backend ->
                    DropdownMenuItem(
                        text = { Text(backend.name) },
                        onClick = {
                            onSettingsChange(
                                settings.copy(
                                    rag = settings.rag.copy(vectorBackend = backend)
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }

        Divider(color = ObsidianTheme.Border)

        // Llama Base URL
        OutlinedTextField(
            value = settings.rag.llamaBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(llamaBaseUrl = newValue)
                    )
                )
            },
            label = { Text("Llama Base URL") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ObsidianTheme.TextPrimary,
                unfocusedTextColor = ObsidianTheme.TextPrimary,
                focusedBorderColor = ObsidianTheme.Accent,
                unfocusedBorderColor = ObsidianTheme.Border
            )
        )

        // Embedding Base URL
        OutlinedTextField(
            value = settings.rag.embeddingBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(embeddingBaseUrl = newValue)
                    )
                )
            },
            label = { Text("Embedding Base URL") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ObsidianTheme.TextPrimary,
                unfocusedTextColor = ObsidianTheme.TextPrimary,
                focusedBorderColor = ObsidianTheme.Accent,
                unfocusedBorderColor = ObsidianTheme.Border
            )
        )

        Divider(color = ObsidianTheme.Border)

        // Top-K
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Top-K",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Number of chunks to retrieve: ${settings.rag.topK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Text(
                text = "${settings.rag.topK}",
                style = MaterialTheme.typography.bodyMedium,
                color = ObsidianTheme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.rag.topK.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(topK = newValue.toInt())
                    )
                )
            },
            valueRange = 1f..20f,
            steps = 18, // 1 increment
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = ObsidianTheme.Border)

        // RAG Enabled
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RAG Enabled",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ObsidianTheme.TextPrimary
                )
                Text(
                    text = "Enable RAG for chat responses",
                    style = MaterialTheme.typography.bodySmall,
                    color = ObsidianTheme.TextSecondary
                )
            }
            Switch(
                checked = settings.rag.ragEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            rag = settings.rag.copy(ragEnabled = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = ObsidianTheme.Border)

        // Reindex Button
        Button(
            onClick = {
                coroutineScope.launch {
                    onReindex()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = ObsidianTheme.Accent
            )
        ) {
            Text("Reindex Notes")
        }
    }
}

