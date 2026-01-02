package org.krypton.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.krypton.CatppuccinMochaColors

/**
 * Screen for creating a new study goal with topics.
 * 
 * @param onDismiss Callback when dialog is dismissed
 * @param onCreate Callback when goal is created (title, description, topics, targetDate)
 */
@Composable
fun CreateGoalScreen(
    onDismiss: () -> Unit,
    onCreate: (String, String?, List<String>, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf(listOf("")) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Create Study Goal",
                style = MaterialTheme.typography.bodyMedium,
                color = CatppuccinMochaColors.Text
            )
            
            IconButton(onClick = onDismiss) {
                Text(
                    text = "✕",
                    color = CatppuccinMochaColors.Text
                )
            }
        }
        
        // Title input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Goal Title *", color = CatppuccinMochaColors.Subtext1) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CatppuccinMochaColors.Text,
                unfocusedTextColor = CatppuccinMochaColors.Text,
                focusedLabelColor = CatppuccinMochaColors.Subtext1,
                unfocusedLabelColor = CatppuccinMochaColors.Subtext0
            )
        )
        
        // Description input
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)", color = CatppuccinMochaColors.Subtext1) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CatppuccinMochaColors.Text,
                unfocusedTextColor = CatppuccinMochaColors.Text,
                focusedLabelColor = CatppuccinMochaColors.Subtext1,
                unfocusedLabelColor = CatppuccinMochaColors.Subtext0
            )
        )
        
        // Topics section
        Text(
            text = "Topics (optional - each topic becomes a study session)",
            style = MaterialTheme.typography.bodySmall,
            color = CatppuccinMochaColors.Subtext1
        )
        
        topics.forEachIndexed { index, topic ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { newTopic ->
                        topics = topics.toMutableList().apply {
                            this[index] = newTopic
                        }
                    },
                    label = { Text("Topic ${index + 1}", color = CatppuccinMochaColors.Subtext1) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CatppuccinMochaColors.Text,
                        unfocusedTextColor = CatppuccinMochaColors.Text,
                        focusedLabelColor = CatppuccinMochaColors.Subtext1,
                        unfocusedLabelColor = CatppuccinMochaColors.Subtext0
                    )
                )
                
                if (topics.size > 1) {
                    IconButton(
                        onClick = {
                            topics = topics.filterIndexed { i, _ -> i != index }
                        }
                    ) {
                        Text("−")
                    }
                }
            }
        }
        
        // Add topic button
        Button(
            onClick = {
                topics = topics + ""
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CatppuccinMochaColors.Surface1,
                contentColor = CatppuccinMochaColors.Text
            )
        ) {
            Text("+ Add Topic", style = MaterialTheme.typography.bodySmall)
        }
        
        // Target date input
        OutlinedTextField(
            value = targetDate,
            onValueChange = { targetDate = it },
            label = { Text("Target Date (YYYY-MM-DD, optional)", color = CatppuccinMochaColors.Subtext1) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("2024-12-31", color = CatppuccinMochaColors.Subtext0) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CatppuccinMochaColors.Text,
                unfocusedTextColor = CatppuccinMochaColors.Text,
                focusedLabelColor = CatppuccinMochaColors.Subtext1,
                unfocusedLabelColor = CatppuccinMochaColors.Subtext0
            )
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CatppuccinMochaColors.Text
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.bodySmall)
            }
            
            Button(
                onClick = {
                    val validTopics = topics.filter { it.isNotBlank() }
                    if (title.isNotBlank()) {
                        onCreate(
                            title,
                            description.takeIf { it.isNotBlank() },
                            validTopics,
                            targetDate.takeIf { it.isNotBlank() }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CatppuccinMochaColors.Blue,
                    contentColor = CatppuccinMochaColors.Base
                )
            ) {
                Text("Create", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

