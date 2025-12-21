package org.krypton.krypton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import krypton.composeapp.generated.resources.Res
import krypton.composeapp.generated.resources.close

@Composable
fun SettingsDialog(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    if (state.settingsDialogOpen) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = { state.closeSettingsDialog() }),
            contentAlignment = Alignment.Center
        ) {
            // Dialog content
            Card(
                modifier = modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ObsidianTheme.BackgroundElevated
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ObsidianTheme.SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                color = ObsidianTheme.TextPrimary
                            )
                            IconButton(
                                onClick = { state.closeSettingsDialog() }
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(ObsidianTheme.TextSecondary)
                                )
                            }
                        }
                    }

                    // Content - Two column layout
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left column - Categories
                        Surface(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight(),
                            color = ObsidianTheme.Background
                        ) {
                            SettingsCategoryList(
                                selectedCategory = state.selectedSettingsCategory,
                                onCategorySelected = { state.selectSettingsCategory(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Divider(
                            color = ObsidianTheme.Border,
                            modifier = Modifier.width(1.dp)
                        )

                        // Right column - Settings content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(ObsidianTheme.BackgroundElevated)
                        ) {
                            SettingsContent(
                                category = state.selectedSettingsCategory,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp)
                            )
                        }
                    }

                    // Footer - Action buttons
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ObsidianTheme.SurfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { state.closeSettingsDialog() }
                            ) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    // TODO: Save settings
                                    state.closeSettingsDialog()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianTheme.Accent
                                )
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryList(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsCategory.values().forEach { category ->
            SettingsCategoryItem(
                category = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            ObsidianTheme.SelectionAccent
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                ObsidianTheme.Accent
            } else {
                ObsidianTheme.TextPrimary
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SettingsContent(
    category: SettingsCategory,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.headlineSmall,
            color = ObsidianTheme.TextPrimary
        )

        when (category) {
            SettingsCategory.General -> {
                GeneralSettings()
            }
            SettingsCategory.Editor -> {
                EditorSettings()
            }
            SettingsCategory.Appearance -> {
                AppearanceSettings()
            }
            SettingsCategory.Keybindings -> {
                KeybindingsSettings()
            }
        }
    }
}

@Composable
private fun GeneralSettings() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "General settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
    }
}

@Composable
private fun EditorSettings() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Editor settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
    }
}

@Composable
private fun AppearanceSettings() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Appearance settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
    }
}

@Composable
private fun KeybindingsSettings() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Keybindings settings coming soon...",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianTheme.TextSecondary
        )
    }
}

