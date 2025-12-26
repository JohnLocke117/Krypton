package org.krypton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.krypton.VectorBackend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagSettings(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onReindex: () -> Unit,
    theme: ObsidianThemeValues
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vector Backend
        Text(
            text = "Vector Backend",
            style = MaterialTheme.typography.bodyLarge,
            color = theme.TextPrimary
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
                    focusedTextColor = theme.TextPrimary,
                    unfocusedTextColor = theme.TextPrimary,
                    focusedBorderColor = theme.Accent,
                    unfocusedBorderColor = theme.Border
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

        Divider(color = theme.Border)

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
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
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
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        Divider(color = theme.Border)

        // ChromaDB Settings
        Text(
            text = "ChromaDB Settings",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )

        OutlinedTextField(
            value = settings.rag.chromaBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaBaseUrl = newValue)
                    )
                )
            },
            label = { Text("ChromaDB Base URL") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        OutlinedTextField(
            value = settings.rag.chromaCollectionName,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaCollectionName = newValue)
                    )
                )
            },
            label = { Text("ChromaDB Collection Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        OutlinedTextField(
            value = settings.rag.chromaTenant,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaTenant = newValue)
                    )
                )
            },
            label = { Text("ChromaDB Tenant") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        OutlinedTextField(
            value = settings.rag.chromaDatabase,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaDatabase = newValue)
                    )
                )
            },
            label = { Text("ChromaDB Database") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Number of chunks to retrieve: ${settings.rag.topK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.rag.topK}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
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

        Divider(color = theme.Border)

        // Similarity Threshold
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Similarity Threshold",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "Minimum similarity score: ${String.format("%.2f", settings.rag.similarityThreshold)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = String.format("%.2f", settings.rag.similarityThreshold),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.rag.similarityThreshold,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(similarityThreshold = newValue)
                    )
                )
            },
            valueRange = 0f..1f,
            steps = 99, // 0.01 increments
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Max-K
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Max-K",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "Maximum chunks to retrieve: ${settings.rag.maxK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.rag.maxK}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.rag.maxK.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(maxK = newValue.toInt())
                    )
                )
            },
            valueRange = 1f..50f,
            steps = 48, // 1 increment
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Display-K
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Display-K",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "Number of chunks to display: ${settings.rag.displayK}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Text(
                text = "${settings.rag.displayK}",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Slider(
            value = settings.rag.displayK.toFloat(),
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(displayK = newValue.toInt())
                    )
                )
            },
            valueRange = 1f..20f,
            steps = 18, // 1 increment
            modifier = Modifier.fillMaxWidth()
        )

        Divider(color = theme.Border)

        // Query Rewriting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Query Rewriting",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "Enable query rewriting for better retrieval",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Switch(
                checked = settings.rag.queryRewritingEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            rag = settings.rag.copy(queryRewritingEnabled = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = theme.Border)

        // Multi-Query
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Multi-Query",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.TextPrimary
                )
                Text(
                    text = "Enable multi-query generation",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
            Switch(
                checked = settings.rag.multiQueryEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange(
                        settings.copy(
                            rag = settings.rag.copy(multiQueryEnabled = enabled)
                        )
                    )
                }
            )
        }

        Divider(color = theme.Border)

        // Reranker Model
        OutlinedTextField(
            value = settings.rag.rerankerModel ?: "",
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(rerankerModel = newValue.ifBlank { null })
                    )
                )
            },
            label = { Text("Reranker Model (optional)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )

        Divider(color = theme.Border)

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
                    color = theme.TextPrimary
                )
                Text(
                    text = "Enable RAG for chat responses",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
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

        Divider(color = theme.Border)

        // Reindex Button
        Button(
            onClick = {
                coroutineScope.launch {
                    onReindex()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.Accent
            )
        ) {
            Text("Reindex Notes")
        }
    }
}

