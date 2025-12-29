package org.krypton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.krypton.VectorBackend

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
        InlineTextField(
            label = "Vector Backend",
            value = settings.rag.vectorBackend.name,
            onValueChange = { newValue ->
                VectorBackend.values().find { it.name.equals(newValue, ignoreCase = true) }?.let { backend ->
                    onSettingsChange(
                        settings.copy(
                            rag = settings.rag.copy(vectorBackend = backend)
                        )
                    )
                }
            },
            description = "Vector backend type (CHROMADB)",
            theme = theme
        )

        InlineTextField(
            label = "Generator Model",
            value = settings.rag.llamaModel,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(llamaModel = newValue)
                    )
                )
            },
            description = "Ollama generator model name (e.g., llama3.2:1b)",
            theme = theme
        )

        InlineTextField(
            label = "Embedding Model",
            value = settings.rag.embeddingModel,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(embeddingModel = newValue)
                    )
                )
            },
            description = "Ollama embedding model name (e.g., nomic-embed-text:v1.5)",
            theme = theme
        )

        Divider(color = theme.Border)

        InlineTextField(
            label = "Llama Base URL",
            value = settings.rag.llamaBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(llamaBaseUrl = newValue)
                    )
                )
            },
            theme = theme
        )

        InlineTextField(
            label = "Embedding Base URL",
            value = settings.rag.embeddingBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(embeddingBaseUrl = newValue)
                    )
                )
            },
            theme = theme
        )

        Divider(color = theme.Border)

        Text(
            text = "ChromaDB Settings",
            style = MaterialTheme.typography.titleMedium,
            color = theme.TextPrimary
        )

        InlineTextField(
            label = "ChromaDB Base URL",
            value = settings.rag.chromaBaseUrl,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaBaseUrl = newValue)
                    )
                )
            },
            theme = theme
        )

        InlineTextField(
            label = "ChromaDB Collection Name",
            value = settings.rag.chromaCollectionName,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaCollectionName = newValue)
                    )
                )
            },
            theme = theme
        )

        InlineTextField(
            label = "ChromaDB Tenant",
            value = settings.rag.chromaTenant,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaTenant = newValue)
                    )
                )
            },
            theme = theme
        )

        InlineTextField(
            label = "ChromaDB Database",
            value = settings.rag.chromaDatabase,
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(chromaDatabase = newValue)
                    )
                )
            },
            theme = theme
        )

        Divider(color = theme.Border)

        InlineTextField(
            label = "Top-K",
            value = settings.rag.topK.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 1 && intValue <= 20) {
                        onSettingsChange(
                            settings.copy(
                                rag = settings.rag.copy(topK = intValue)
                            )
                        )
                    }
                }
            },
            description = "Number of chunks to retrieve (1-20)",
            theme = theme
        )

        InlineFloatField(
            label = "Similarity Threshold",
            value = settings.rag.similarityThreshold,
            onValueChange = { newValue ->
                if (newValue >= 0f && newValue <= 1f) {
                    onSettingsChange(
                        settings.copy(
                            rag = settings.rag.copy(similarityThreshold = newValue)
                        )
                    )
                }
            },
            description = "Minimum similarity score (0.0-1.0)",
            theme = theme
        )

        InlineTextField(
            label = "Max-K",
            value = settings.rag.maxK.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 1 && intValue <= 50) {
                        onSettingsChange(
                            settings.copy(
                                rag = settings.rag.copy(maxK = intValue)
                            )
                        )
                    }
                }
            },
            description = "Maximum chunks to retrieve (1-50)",
            theme = theme
        )

        InlineTextField(
            label = "Display-K",
            value = settings.rag.displayK.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue >= 1 && intValue <= 20) {
                        onSettingsChange(
                            settings.copy(
                                rag = settings.rag.copy(displayK = intValue)
                            )
                        )
                    }
                }
            },
            description = "Number of chunks to display (1-20)",
            theme = theme
        )

        InlineBooleanField(
            label = "Query Rewriting",
            value = settings.rag.queryRewritingEnabled,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(queryRewritingEnabled = enabled)
                    )
                )
            },
            description = "Enable query rewriting for better retrieval",
            theme = theme
        )

        InlineBooleanField(
            label = "Multi-Query",
            value = settings.rag.multiQueryEnabled,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(multiQueryEnabled = enabled)
                    )
                )
            },
            description = "Enable multi-query generation",
            theme = theme
        )

        InlineBooleanField(
            label = "Reranking",
            value = settings.rag.rerankingEnabled,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(rerankingEnabled = enabled)
                    )
                )
            },
            description = "Enable reranking of retrieved chunks",
            theme = theme
        )

        InlineTextField(
            label = "Reranker Model",
            value = settings.rag.rerankerModel ?: "",
            onValueChange = { newValue ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(rerankerModel = newValue.ifBlank { null })
                    )
                )
            },
            description = "Reranker model name (optional)",
            theme = theme
        )

        InlineBooleanField(
            label = "RAG Enabled",
            value = settings.rag.ragEnabled,
            onValueChange = { enabled ->
                onSettingsChange(
                    settings.copy(
                        rag = settings.rag.copy(ragEnabled = enabled)
                    )
                )
            },
            description = "Enable RAG for chat responses",
            theme = theme
        )

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

@Composable
private fun InlineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

@Composable
private fun InlineBooleanField(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                when (newValue.lowercase()) {
                    "true" -> onValueChange(true)
                    "false" -> onValueChange(false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

@Composable
private fun InlineFloatField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String? = null,
    theme: ObsidianThemeValues
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.TextPrimary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextSecondary
                )
            }
        }
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                newValue.toFloatOrNull()?.let { floatValue ->
                    onValueChange(floatValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.TextPrimary,
                unfocusedTextColor = theme.TextPrimary,
                focusedBorderColor = theme.Accent,
                unfocusedBorderColor = theme.Border
            )
        )
    }
}

