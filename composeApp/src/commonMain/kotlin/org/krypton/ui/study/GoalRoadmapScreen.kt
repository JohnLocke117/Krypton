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
import org.krypton.core.domain.study.StudySession
import org.krypton.core.domain.study.SessionStatus

/**
 * Screen showing the roadmap for a goal with sessions.
 * 
 * @param goal The goal to display roadmap for
 * @param sessions List of sessions for this goal
 * @param onBack Callback when back button is clicked
 * @param onStartSession Callback when a session is started
 * @param onCreateStudyPlan Callback to trigger study plan creation (if not already planned)
 */
@Composable
fun GoalRoadmapScreen(
    goal: org.krypton.core.domain.study.StudyGoal,
    sessions: List<StudySession>,
    onBack: () -> Unit,
    onStartSession: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    onCreateStudyPlan: () -> Unit,
    planningInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = CatppuccinMochaColors.Text
                )
            ) {
                Text("← Back to Goals", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Goal title
        Text(
            text = goal.title,
            style = MaterialTheme.typography.bodyMedium,
            color = CatppuccinMochaColors.Text
        )
        
        // Goal description
        goal.description?.let { desc ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CatppuccinMochaColors.Surface0
                )
            ) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = CatppuccinMochaColors.Subtext1,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        // Roadmap (if available)
        goal.roadmap?.let { roadmap ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CatppuccinMochaColors.Surface0
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Study Roadmap",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatppuccinMochaColors.Text
                    )
                    Text(
                        text = roadmap,
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext1
                    )
                }
            }
        }
        
        // Create Sessions button (if no sessions yet)
        if (sessions.isEmpty() && !planningInProgress) {
            Button(
                onClick = onCreateStudyPlan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CatppuccinMochaColors.Blue,
                    contentColor = CatppuccinMochaColors.Base
                )
            ) {
                Text("Create Sessions", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Planning indicator
        if (planningInProgress) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CatppuccinMochaColors.Surface0
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = CatppuccinMochaColors.Blue
                    )
                    Text(
                        "Creating sessions...",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Text
                    )
                }
            }
        }
        
        // Sessions list
        if (sessions.isNotEmpty()) {
            Text(
                text = "Study Sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = CatppuccinMochaColors.Text
            )
            
            sessions.forEach { session ->
                SessionCard(
                    session = session,
                    onClick = { onStartSession(session.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(
    session: StudySession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (session.status) {
                SessionStatus.COMPLETED -> CatppuccinMochaColors.Surface1
                SessionStatus.PENDING -> CatppuccinMochaColors.Surface0
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Session title
            Text(
                text = "Session ${session.order}: ${session.topic}",
                style = MaterialTheme.typography.bodySmall,
                color = CatppuccinMochaColors.Text
            )
            
            // Status badge (below title)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when (session.status) {
                    SessionStatus.COMPLETED -> CatppuccinMochaColors.Green
                    SessionStatus.PENDING -> CatppuccinMochaColors.Surface1
                },
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = session.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = when (session.status) {
                        SessionStatus.COMPLETED -> CatppuccinMochaColors.Base
                        SessionStatus.PENDING -> CatppuccinMochaColors.Subtext1
                    }
                )
            }
            
            Text(
                text = "${session.notePaths.size} notes",
                style = MaterialTheme.typography.bodySmall,
                color = CatppuccinMochaColors.Subtext0
            )
            
            if (session.status == SessionStatus.PENDING) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CatppuccinMochaColors.Blue,
                        contentColor = CatppuccinMochaColors.Base
                    )
                ) {
                    Text("Start Session", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

