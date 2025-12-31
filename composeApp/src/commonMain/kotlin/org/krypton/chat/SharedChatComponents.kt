package org.krypton.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.krypton.ObsidianThemeValues
import org.krypton.VectorBackend
import org.krypton.LlmProvider
import org.krypton.rag.SyncStatus
import org.krypton.ui.AppIconWithTooltip
import org.krypton.ui.TooltipPosition
import org.krypton.ui.state.UiStatus

/**
 * Retrieval mode selector component.
 * 
 * @param ragEnabled Whether RAG mode is enabled
 * @param webEnabled Whether web search mode is enabled
 * @param tavilyAvailable Whether Tavily API is available
 * @param onRagToggle Callback when RAG toggle is clicked
 * @param onWebToggle Callback when web search toggle is clicked
 * @param onTavilyError Callback to show Tavily error message
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun RetrievalModeSelector(
    ragEnabled: Boolean,
    webEnabled: Boolean,
    tavilyAvailable: Boolean,
    onRagToggle: () -> Unit,
    onWebToggle: () -> Unit,
    onTavilyError: (String) -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Web search toggle (Tavily)
        AppIconWithTooltip(
            tooltip = "Web Search",
            modifier = Modifier.size(24.dp),
            enabled = tavilyAvailable,
            position = TooltipPosition.ABOVE,
            onClick = {
                if (tavilyAvailable) {
                    onWebToggle()
                } else {
                    onTavilyError("Tavily API key not found. Please add TAVILLY_API_KEY to local.secrets.properties")
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Web Search (Tavily)",
                modifier = Modifier.size(20.dp),
                tint = when {
                    !tavilyAvailable -> theme.TextTertiary
                    webEnabled -> theme.Accent
                    else -> theme.TextSecondary
                }
            )
        }
    }
}

/**
 * RAG toggle button component.
 * 
 * @param ragEnabled Whether RAG is enabled
 * @param syncStatus Current sync status
 * @param onToggle Callback when toggle is clicked
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun RagToggleButton(
    ragEnabled: Boolean,
    syncStatus: SyncStatus?,
    onToggle: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    AppIconWithTooltip(
        tooltip = "RAG",
        modifier = modifier.size(24.dp),
        enabled = syncStatus != SyncStatus.UNAVAILABLE,
        position = TooltipPosition.ABOVE,
        onClick = onToggle
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "RAG",
            modifier = Modifier.size(20.dp),
            tint = when {
                syncStatus == SyncStatus.UNAVAILABLE -> theme.TextTertiary
                ragEnabled -> theme.Accent
                else -> theme.TextSecondary
            }
        )
    }
}

/**
 * Sync status indicator component.
 * 
 * @param syncStatus Current sync status
 * @param ragEnabled Whether RAG is enabled
 * @param modifier Modifier to apply
 */
@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus?,
    ragEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    syncStatus?.let { status ->
        if (ragEnabled) {
            Box(
                modifier = modifier
                    .size(8.dp)
                    .background(
                        when (status) {
                            SyncStatus.SYNCED -> Color(0xFF4CAF50) // Green
                            SyncStatus.OUT_OF_SYNC, SyncStatus.NOT_INDEXED -> Color(0xFFFFC107) // Yellow
                            SyncStatus.UNAVAILABLE -> Color(0xFFF44336) // Red
                        },
                        CircleShape
                    )
            )
        }
    }
    if (!ragEnabled) {
        // Outlined red circle when RAG is off
        Box(
            modifier = modifier
                .size(8.dp)
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFF44336), // Red
                    shape = CircleShape
                )
        )
    }
}

/**
 * LLM provider selector component.
 * 
 * @param selectedProvider Currently selected LLM provider
 * @param availableProviders List of available providers (for filtering on Android)
 * @param expanded Whether dropdown is expanded
 * @param onExpandedChange Callback when expansion state changes
 * @param onProviderSelected Callback when provider is selected
 * @param onError Callback to show error message
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun LlmProviderSelector(
    selectedProvider: LlmProvider,
    availableProviders: List<LlmProvider> = LlmProvider.values().toList(),
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (LlmProvider) -> Unit,
    onError: ((String) -> Unit)? = null,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AppIconWithTooltip(
            tooltip = when (selectedProvider) {
                LlmProvider.OLLAMA -> "Ollama"
                LlmProvider.GEMINI -> "Gemini API"
            },
            modifier = Modifier.size(24.dp),
            enabled = true,
            position = TooltipPosition.ABOVE,
            onClick = { onExpandedChange(!expanded) }
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "LLM Provider",
                modifier = Modifier.size(20.dp),
                tint = theme.TextPrimary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .background(theme.BackgroundElevated)
                .widthIn(max = 150.dp)
        ) {
            availableProviders.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (provider) {
                                LlmProvider.OLLAMA -> "Ollama"
                                LlmProvider.GEMINI -> "Gemini API"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextPrimary
                        )
                    },
                    onClick = {
                        onProviderSelected(provider)
                        onExpandedChange(false)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = theme.TextPrimary
                    )
                )
            }
        }
    }
}

/**
 * Vector store selector component.
 * 
 * @param selectedBackend Currently selected vector backend
 * @param availableBackends List of available backends (for filtering on Android)
 * @param ragEnabled Whether RAG is enabled
 * @param expanded Whether dropdown is expanded
 * @param onExpandedChange Callback when expansion state changes
 * @param onBackendSelected Callback when backend is selected
 * @param onError Callback to show error message
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun VectorStoreSelector(
    selectedBackend: VectorBackend,
    availableBackends: List<VectorBackend> = VectorBackend.values().toList(),
    ragEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBackendSelected: (VectorBackend) -> Unit,
    onError: ((String) -> Unit)? = null,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    fun getBackendDisplayName(backend: VectorBackend): String {
        return when (backend) {
            VectorBackend.CHROMADB -> "ChromaDB"
            VectorBackend.CHROMA_CLOUD -> "ChromaDB Cloud"
        }
    }
    
    Box(modifier = modifier) {
        AppIconWithTooltip(
            tooltip = getBackendDisplayName(selectedBackend),
            modifier = Modifier.size(24.dp),
            enabled = ragEnabled,
            position = TooltipPosition.ABOVE,
            onClick = {
                if (ragEnabled) {
                    onExpandedChange(!expanded)
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Vector DB",
                modifier = Modifier.size(20.dp),
                tint = if (ragEnabled) theme.TextPrimary else theme.TextSecondary.copy(alpha = 0.5f)
            )
        }
        DropdownMenu(
            expanded = expanded && ragEnabled,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .background(theme.BackgroundElevated)
                .widthIn(max = 150.dp)
        ) {
            availableBackends.forEach { backend ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = getBackendDisplayName(backend),
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextPrimary
                        )
                    },
                    onClick = {
                        onBackendSelected(backend)
                        onExpandedChange(false)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = theme.TextPrimary
                    )
                )
            }
        }
    }
}

/**
 * Multi-query toggle component.
 * 
 * @param enabled Whether multi-query is enabled
 * @param ragEnabled Whether RAG is enabled
 * @param onToggle Callback when toggle is clicked
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun MultiQueryToggle(
    enabled: Boolean,
    ragEnabled: Boolean,
    onToggle: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    AppIconWithTooltip(
        tooltip = "Multi-Query",
        modifier = modifier.size(24.dp),
        enabled = ragEnabled,
        position = TooltipPosition.ABOVE,
        onClick = {
            if (ragEnabled) {
                onToggle()
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.CallSplit,
            contentDescription = "Multi-Query",
            modifier = Modifier.size(20.dp),
            tint = if (ragEnabled && enabled) {
                theme.Accent
            } else {
                theme.TextSecondary.copy(alpha = if (ragEnabled) 1f else 0.5f)
            }
        )
    }
}

/**
 * Reranking toggle component.
 * 
 * @param enabled Whether reranking is enabled
 * @param ragEnabled Whether RAG is enabled
 * @param onToggle Callback when toggle is clicked
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun RerankingToggle(
    enabled: Boolean,
    ragEnabled: Boolean,
    onToggle: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    AppIconWithTooltip(
        tooltip = "Reranking",
        modifier = modifier.size(24.dp),
        enabled = ragEnabled,
        position = TooltipPosition.ABOVE,
        onClick = {
            if (ragEnabled) {
                onToggle()
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Reranking",
            modifier = Modifier.size(20.dp),
            tint = if (ragEnabled && enabled) {
                theme.Accent
            } else {
                theme.TextSecondary.copy(alpha = if (ragEnabled) 1f else 0.5f)
            }
        )
    }
}

/**
 * Reindex controls component.
 * 
 * @param enabled Whether reindex button is enabled
 * @param loading Whether reindex operation is in progress
 * @param syncStatus Current sync status
 * @param onReindex Callback when reindex button is clicked
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun ReindexControls(
    enabled: Boolean,
    loading: Boolean,
    syncStatus: SyncStatus?,
    onReindex: () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    AppIconWithTooltip(
        tooltip = "Rebuild Vector Database",
        modifier = modifier.size(24.dp),
        enabled = enabled && !loading,
        position = TooltipPosition.ABOVE,
        onClick = {
            if (enabled && !loading) {
                onReindex()
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Rebuild Vector Database",
            modifier = Modifier.size(20.dp),
            tint = when {
                syncStatus == SyncStatus.UNAVAILABLE || loading -> theme.TextSecondary
                else -> theme.TextPrimary
            }
        )
    }
}

/**
 * RAG controls container component.
 * Wraps RAG-related controls in a bordered box.
 * 
 * @param ragAvailable Whether RAG components are available
 * @param content Content to display inside the container
 * @param theme Theme values
 * @param modifier Modifier to apply
 */
@Composable
fun RagControlsContainer(
    ragAvailable: Boolean,
    content: @Composable () -> Unit,
    theme: ObsidianThemeValues,
    modifier: Modifier = Modifier
) {
    if (ragAvailable) {
        Surface(
            modifier = modifier
                .border(
                    width = 1.dp,
                    color = theme.Border,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    } else {
        Spacer(modifier = Modifier.size(24.dp))
    }
}

