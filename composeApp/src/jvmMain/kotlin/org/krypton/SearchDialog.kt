package org.krypton

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
import org.krypton.ui.state.SearchStateHolder
import org.krypton.ui.state.EditorStateHolder

@Composable
fun SearchDialog(
    searchStateHolder: SearchStateHolder,
    editorStateHolder: EditorStateHolder,
    theme: ObsidianThemeValues,
    onReplace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchState by searchStateHolder.state.collectAsState()
    val activeDocument by editorStateHolder.activeDocument.collectAsState()
    
    val currentSearchState = searchState ?: return
    val documentText = activeDocument?.text ?: ""

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
                    value = currentSearchState.searchQuery,
                    onValueChange = { query ->
                        searchStateHolder.updateSearchState(documentText) { currentState ->
                            currentState.copy(searchQuery = query)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Enter -> {
                                        searchStateHolder.findNext()
                                        true
                                    }
                                    Key.Escape -> {
                                        searchStateHolder.closeSearchDialog()
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
                if (currentSearchState.hasMatches) {
                    Text(
                        text = currentSearchState.currentMatchText,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Navigation buttons
                IconButton(
                    onClick = { searchStateHolder.findPrevious() },
                    enabled = currentSearchState.hasMatches
                ) {
                    // Use chevron_right rotated or a simple up arrow
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentSearchState.hasMatches) theme.TextPrimary else theme.TextTertiary
                    )
                }

                IconButton(
                    onClick = { searchStateHolder.findNext() },
                    enabled = currentSearchState.hasMatches
                ) {
                    Image(
                        painter = painterResource(Res.drawable.keyboard_arrow_down),
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(
                            if (currentSearchState.hasMatches) theme.TextPrimary else theme.TextTertiary
                        )
                    )
                }

                // Close button
                IconButton(onClick = { searchStateHolder.closeSearchDialog() }) {
                    Image(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(theme.TextSecondary)
                    )
                }
            }

            // Replace row (if showReplace is true)
            if (currentSearchState.showReplace) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentSearchState.replaceQuery,
                        onValueChange = { replace ->
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(replaceQuery = replace)
                            }
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
                            if (activeDocument != null && currentSearchState.currentMatchIndex >= 0) {
                                val newText = searchStateHolder.replaceCurrent(documentText)
                                onReplace(newText)
                                // Update matches after replace
                                searchStateHolder.findMatches(newText)
                            }
                        },
                        enabled = currentSearchState.hasMatches && currentSearchState.currentMatchIndex >= 0
                    ) {
                        Text("Replace")
                    }

                    TextButton(
                        onClick = {
                            if (activeDocument != null) {
                                val newText = searchStateHolder.replaceAll(documentText)
                                onReplace(newText)
                                searchStateHolder.closeSearchDialog()
                            }
                        },
                        enabled = currentSearchState.hasMatches
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
                        checked = currentSearchState.matchCase,
                        onCheckedChange = { matchCase ->
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(matchCase = matchCase)
                            }
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
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(matchCase = !currentState.matchCase)
                            }
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = currentSearchState.wholeWords,
                        onCheckedChange = { wholeWords ->
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(wholeWords = wholeWords)
                            }
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
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(wholeWords = !currentState.wholeWords)
                            }
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = currentSearchState.useRegex,
                        onCheckedChange = { useRegex ->
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(useRegex = useRegex)
                            }
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
                            searchStateHolder.updateSearchState(documentText) { currentState ->
                                currentState.copy(useRegex = !currentState.useRegex)
                            }
                        }
                    )
                }
            }
        }
    }
}

