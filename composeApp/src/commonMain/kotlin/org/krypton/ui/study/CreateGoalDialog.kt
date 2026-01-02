package org.krypton.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.krypton.CatppuccinMochaColors
import org.krypton.ObsidianThemeValues

/**
 * Dialog for creating a new study goal.
 * 
 * @param onDismiss Callback when dialog is dismissed
 * @param onCreate Callback when goal is created
 */
@Composable
fun CreateGoalDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, targetDate: String?) -> Unit,
    modifier: Modifier = Modifier,
    theme: ObsidianThemeValues? = null,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetDateString by remember { mutableStateOf("") }
    
    // Use theme if provided, otherwise fall back to MaterialTheme
    val textFieldColors = if (theme != null) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = theme.TextPrimary,
            unfocusedTextColor = theme.TextPrimary,
            focusedBorderColor = theme.Accent,
            unfocusedBorderColor = theme.Border,
            disabledBorderColor = theme.Border,
            disabledTextColor = theme.TextSecondary,
            focusedContainerColor = CatppuccinMochaColors.Surface0,
            unfocusedContainerColor = CatppuccinMochaColors.Surface0,
            disabledContainerColor = CatppuccinMochaColors.Surface0,
            cursorColor = theme.Accent,
            focusedLabelColor = theme.Accent,
            unfocusedLabelColor = theme.TextSecondary,
            focusedPlaceholderColor = theme.TextTertiary,
            unfocusedPlaceholderColor = theme.TextTertiary
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CatppuccinMochaColors.Surface0
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create Study Goal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (theme != null) theme.TextPrimary else MaterialTheme.colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(8.dp)
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(8.dp)
                )
                
                OutlinedTextField(
                    value = targetDateString,
                    onValueChange = { targetDateString = it },
                    label = { Text("Target Date (YYYY-MM-DD, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("2024-12-31") },
                    colors = textFieldColors,
                    shape = RoundedCornerShape(8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val targetDate = targetDateString.takeIf { it.isNotBlank() }
                                
                                onCreate(
                                    title,
                                    description.takeIf { it.isNotBlank() },
                                    targetDate
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank()
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

