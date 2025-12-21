package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close
import krypton.composeapp.generated.resources.keyboard_arrow_down

@Composable
fun SearchDialog(
    state: EditorState,
    theme: ObsidianThemeValues,
    onSearchUpdate: (SearchState) -> Unit,
    onReplace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchState = state.searchState ?: return

    Card(
        modifier = modifier
            .widthIn(min = 400.dp, max = 600.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.BackgroundElevated
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search input
                OutlinedTextField(
                    value = searchState.searchQuery,
                    onValueChange = { query ->
                        val matches = SearchEngine.findMatches(
                            text = state.getActiveTab()?.text ?: "",
                            query = query,
                            matchCase = searchState.matchCase,
                            wholeWords = searchState.wholeWords,
                            useRegex = searchState.useRegex
                        )
                        onSearchUpdate(
                            searchState.copy(
                                searchQuery = query,
                                matches = matches,
                                currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Enter -> {
                                        state.findNext()
                                        true
                                    }
                                    Key.Escape -> {
                                        state.closeSearchDialog()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.TextPrimary,
                        unfocusedTextColor = theme.TextPrimary,
                        focusedBorderColor = theme.Accent,
                        unfocusedBorderColor = theme.Border
                    )
                )

                // Match counter
                if (searchState.hasMatches) {
                    Text(
                        text = searchState.currentMatchText,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Navigation buttons
                IconButton(
                    onClick = { state.findPrevious() },
                    enabled = searchState.hasMatches
                ) {
                    // Use chevron_right rotated or a simple up arrow
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (searchState.hasMatches) theme.TextPrimary else theme.TextTertiary
                    )
                }

                IconButton(
                    onClick = { state.findNext() },
                    enabled = searchState.hasMatches
                ) {
                    Image(
                        painter = painterResource(Res.drawable.keyboard_arrow_down),
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(
                            if (searchState.hasMatches) theme.TextPrimary else theme.TextTertiary
                        )
                    )
                }

                // Close button
                IconButton(onClick = { state.closeSearchDialog() }) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(theme.TextSecondary)
                    )
                }
            }

            // Replace row (if showReplace is true)
            if (searchState.showReplace) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchState.replaceQuery,
                        onValueChange = { replace ->
                            onSearchUpdate(searchState.copy(replaceQuery = replace))
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Replace") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.TextPrimary,
                            unfocusedTextColor = theme.TextPrimary,
                            focusedBorderColor = theme.Accent,
                            unfocusedBorderColor = theme.Border
                        )
                    )

                    TextButton(
                        onClick = {
                            val activeDoc = state.getActiveTab()
                            if (activeDoc != null && searchState.currentMatchIndex >= 0) {
                                val match = searchState.matches[searchState.currentMatchIndex]
                                val newText = SearchEngine.replaceMatch(
                                    activeDoc.text,
                                    match,
                                    searchState.replaceQuery
                                )
                                onReplace(newText)
                                // Update matches after replace
                                val updatedMatches = SearchEngine.findMatches(
                                    text = newText,
                                    query = searchState.searchQuery,
                                    matchCase = searchState.matchCase,
                                    wholeWords = searchState.wholeWords,
                                    useRegex = searchState.useRegex
                                )
                                onSearchUpdate(
                                    searchState.copy(
                                        matches = updatedMatches,
                                        currentMatchIndex = if (updatedMatches.isNotEmpty()) {
                                            minOf(searchState.currentMatchIndex, updatedMatches.size - 1)
                                        } else -1
                                    )
                                )
                            }
                        },
                        enabled = searchState.hasMatches && searchState.currentMatchIndex >= 0
                    ) {
                        Text("Replace")
                    }

                    TextButton(
                        onClick = {
                            val activeDoc = state.getActiveTab()
                            if (activeDoc != null) {
                                val newText = SearchEngine.replaceAll(
                                    activeDoc.text,
                                    searchState.searchQuery,
                                    searchState.replaceQuery,
                                    searchState.matchCase,
                                    searchState.wholeWords,
                                    searchState.useRegex
                                )
                                onReplace(newText)
                                state.closeSearchDialog()
                            }
                        },
                        enabled = searchState.hasMatches
                    ) {
                        Text("Replace All")
                    }
                }
            }

            // Options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = searchState.matchCase,
                        onCheckedChange = { matchCase ->
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = matchCase,
                                    wholeWords = searchState.wholeWords,
                                    useRegex = searchState.useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    matchCase = matchCase,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = theme.Accent
                        )
                    )
                    Text(
                        text = "Match case",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.TextSecondary,
                        modifier = Modifier.clickable {
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = !searchState.matchCase,
                                    wholeWords = searchState.wholeWords,
                                    useRegex = searchState.useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    matchCase = !searchState.matchCase,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = searchState.wholeWords,
                        onCheckedChange = { wholeWords ->
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = searchState.matchCase,
                                    wholeWords = wholeWords,
                                    useRegex = searchState.useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    wholeWords = wholeWords,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = theme.Accent
                        )
                    )
                    Text(
                        text = "Whole words",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.TextSecondary,
                        modifier = Modifier.clickable {
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = searchState.matchCase,
                                    wholeWords = !searchState.wholeWords,
                                    useRegex = searchState.useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    wholeWords = !searchState.wholeWords,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = searchState.useRegex,
                        onCheckedChange = { useRegex ->
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = searchState.matchCase,
                                    wholeWords = searchState.wholeWords,
                                    useRegex = useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    useRegex = useRegex,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = theme.Accent
                        )
                    )
                    Text(
                        text = "Regex",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.TextSecondary,
                        modifier = Modifier.clickable {
                            val activeDoc = state.getActiveTab()
                            val matches = if (activeDoc != null) {
                                SearchEngine.findMatches(
                                    text = activeDoc.text,
                                    query = searchState.searchQuery,
                                    matchCase = searchState.matchCase,
                                    wholeWords = searchState.wholeWords,
                                    useRegex = !searchState.useRegex
                                )
                            } else emptyList()
                            onSearchUpdate(
                                searchState.copy(
                                    useRegex = !searchState.useRegex,
                                    matches = matches,
                                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

