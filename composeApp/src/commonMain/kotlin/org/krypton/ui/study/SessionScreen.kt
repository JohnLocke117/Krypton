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
 * Screen for a study session showing notes with summaries and quiz.
 * 
 * @param session The session to display
 * @param state Current UI state
 * @param onBack Callback when back button is clicked
 * @param onStartQuiz Callback when quiz is started (sessionId, flashcardCount)
 * @param onSubmitAnswer Callback when answer is submitted (sessionId, flashcardIndex, isCorrect)
 * @param onNextQuestion Callback to move to next question (sessionId)
 * @param onCompleteQuiz Callback when quiz is completed
 * @param isFullScreen Whether this is a full-screen view (Android) or split view (Desktop)
 */
@Composable
fun SessionScreen(
    session: StudySession,
    state: StudyUiState,
    onBack: () -> Unit,
    onStartQuiz: (org.krypton.core.domain.study.StudySessionId, Int) -> Unit,
    onSubmitAnswer: (org.krypton.core.domain.study.StudySessionId, Int, Boolean) -> Unit,
    onNextQuestion: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    onCompleteQuiz: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    isFullScreen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (isFullScreen) {
        // Full-screen layout for Android
        FullScreenSessionView(
            session = session,
            state = state,
            onBack = onBack,
            onStartQuiz = onStartQuiz,
            onSubmitAnswer = onSubmitAnswer,
            onNextQuestion = onNextQuestion,
            onCompleteQuiz = onCompleteQuiz,
            modifier = modifier
        )
    } else {
        // Split view for Desktop
        SplitSessionView(
            session = session,
            state = state,
            onBack = onBack,
            onStartQuiz = onStartQuiz,
            onSubmitAnswer = onSubmitAnswer,
            onNextQuestion = onNextQuestion,
            onCompleteQuiz = onCompleteQuiz,
            modifier = modifier
        )
    }
}

@Composable
private fun FullScreenSessionView(
    session: StudySession,
    state: StudyUiState,
    onBack: () -> Unit,
    onStartQuiz: (org.krypton.core.domain.study.StudySessionId, Int) -> Unit,
    onSubmitAnswer: (org.krypton.core.domain.study.StudySessionId, Int, Boolean) -> Unit,
    onNextQuestion: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    onCompleteQuiz: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quizState = state.quizState
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back button
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(
                contentColor = CatppuccinMochaColors.Text
            )
        ) {
            Text("← Back", style = MaterialTheme.typography.bodySmall)
        }
        
        // Topic name
        Text(
            text = session.topic,
            style = MaterialTheme.typography.bodyMedium,
            color = CatppuccinMochaColors.Text
        )
        
        if (quizState == null) {
            // Notes and summaries view
            if (state.preparingSession) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = CatppuccinMochaColors.Blue)
                        Text(
                            "Preparing session...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CatppuccinMochaColors.Text
                        )
                    }
                }
            } else {
            // Notes list with summaries
            Text(
                text = "Notes",
                style = MaterialTheme.typography.bodyMedium,
                color = CatppuccinMochaColors.Text
            )
                
                session.notePaths.forEach { notePath ->
                    val summary = state.sessionSummaries[notePath]
                    NoteSummaryCard(
                        notePath = notePath,
                        summary = summary?.summary
                    )
                }
                
                // Start quiz section
                if (state.sessionFlashcards != null && state.sessionFlashcards.isNotEmpty()) {
                    QuizStartSection(
                        flashcardCount = state.sessionFlashcards.size,
                        onStartQuiz = {
                            onStartQuiz(session.id, 0) // Count will be determined from settings
                        }
                    )
                }
            }
        } else {
            // Quiz view
            QuizView(
                quizState = quizState,
                onSubmitAnswer = { index, isCorrect ->
                    onSubmitAnswer(session.id, index, isCorrect)
                },
                onNextQuestion = {
                    onNextQuestion(session.id)
                },
                onCompleteQuiz = {
                    onCompleteQuiz(session.id)
                }
            )
        }
    }
}

@Composable
private fun SplitSessionView(
    session: StudySession,
    state: StudyUiState,
    onBack: () -> Unit,
    onStartQuiz: (org.krypton.core.domain.study.StudySessionId, Int) -> Unit,
    onSubmitAnswer: (org.krypton.core.domain.study.StudySessionId, Int, Boolean) -> Unit,
    onNextQuestion: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    onCompleteQuiz: (org.krypton.core.domain.study.StudySessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        // Left panel: Notes and summaries
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back button
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = CatppuccinMochaColors.Text
                )
            ) {
                Text("← Back", style = MaterialTheme.typography.bodySmall)
            }
            
            // Topic name
            Text(
                text = session.topic,
                style = MaterialTheme.typography.bodyMedium,
                color = CatppuccinMochaColors.Text
            )
            
            if (state.preparingSession) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = CatppuccinMochaColors.Blue)
                        Text(
                            "Preparing session...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CatppuccinMochaColors.Text
                        )
                    }
                }
            } else {
                // Notes list
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatppuccinMochaColors.Text
                )
                
                session.notePaths.forEach { notePath ->
                    val summary = state.sessionSummaries[notePath]
                    NoteSummaryCard(
                        notePath = notePath,
                        summary = summary?.summary
                    )
                }
            }
        }
        
        // Divider
        VerticalDivider(color = CatppuccinMochaColors.Surface2)
        
        // Right panel: Quiz
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quiz",
                style = MaterialTheme.typography.bodyMedium,
                color = CatppuccinMochaColors.Text
            )
            
            val quizState = state.quizState
            if (quizState == null) {
                // Quiz start section
                if (state.sessionFlashcards != null && state.sessionFlashcards.isNotEmpty()) {
                    QuizStartSection(
                        flashcardCount = state.sessionFlashcards.size,
                        onStartQuiz = {
                            onStartQuiz(session.id, 0) // Count will be determined from settings
                        }
                    )
                } else {
                    Text(
                        "Prepare session to start quiz",
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext0
                    )
                }
            } else {
                // Quiz view
                QuizView(
                    quizState = quizState,
                    onSubmitAnswer = { index, isCorrect ->
                        onSubmitAnswer(session.id, index, isCorrect)
                    },
                    onNextQuestion = {
                        onNextQuestion(session.id)
                    },
                    onCompleteQuiz = {
                        onCompleteQuiz(session.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun NoteSummaryCard(
    notePath: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CatppuccinMochaColors.Surface0
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = notePath.substringAfterLast("/"),
                style = MaterialTheme.typography.bodySmall,
                color = CatppuccinMochaColors.Text
            )
            
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = CatppuccinMochaColors.Subtext1
                )
            } else {
                Text(
                    text = "Summary not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = CatppuccinMochaColors.Subtext0
                )
            }
        }
    }
}

@Composable
private fun QuizStartSection(
    flashcardCount: Int,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Available: $flashcardCount flashcards",
            style = MaterialTheme.typography.bodySmall,
            color = CatppuccinMochaColors.Subtext1
        )
        
        Button(
            onClick = onStartQuiz,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CatppuccinMochaColors.Blue,
                contentColor = CatppuccinMochaColors.Base
            )
        ) {
            Text("Start Quiz", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuizView(
    quizState: QuizState,
    onSubmitAnswer: (Int, Boolean) -> Unit,
    onNextQuestion: () -> Unit,
    onCompleteQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFlashcard = quizState.flashcards[quizState.currentIndex]
    var showAnswer by remember(quizState.currentIndex) { mutableStateOf(false) }
    var answered by remember(quizState.currentIndex) { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Progress
        Text(
            text = "Question ${quizState.currentIndex + 1} of ${quizState.flashcards.size}",
            style = MaterialTheme.typography.bodySmall,
            color = CatppuccinMochaColors.Subtext1
        )
        
        LinearProgressIndicator(
            progress = { (quizState.currentIndex + 1).toFloat() / quizState.flashcards.size },
            modifier = Modifier.fillMaxWidth(),
            color = CatppuccinMochaColors.Blue
        )
        
        // Question card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CatppuccinMochaColors.Surface1
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Question",
                    style = MaterialTheme.typography.labelSmall,
                    color = CatppuccinMochaColors.Subtext0
                )
                
                Text(
                    text = currentFlashcard.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatppuccinMochaColors.Text
                )
                
                if (showAnswer) {
                    HorizontalDivider(color = CatppuccinMochaColors.Surface2)
                    
                    Text(
                        text = "Answer",
                        style = MaterialTheme.typography.labelSmall,
                        color = CatppuccinMochaColors.Subtext0
                    )
                    
                    Text(
                        text = currentFlashcard.answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = CatppuccinMochaColors.Subtext1
                    )
                }
            }
        }
        
        // Answer buttons
        if (!showAnswer) {
            Button(
                onClick = { showAnswer = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CatppuccinMochaColors.Blue,
                    contentColor = CatppuccinMochaColors.Base
                )
            ) {
                Text("Show Answer", style = MaterialTheme.typography.bodySmall)
            }
        } else if (!answered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSubmitAnswer(quizState.currentIndex, false)
                        answered = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CatppuccinMochaColors.Red
                    )
                ) {
                    Text("Incorrect", style = MaterialTheme.typography.bodySmall)
                }
                
                Button(
                    onClick = {
                        onSubmitAnswer(quizState.currentIndex, true)
                        answered = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CatppuccinMochaColors.Green,
                        contentColor = CatppuccinMochaColors.Base
                    )
                ) {
                    Text("Correct", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Navigation
        if (answered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (quizState.currentIndex < quizState.flashcards.size - 1) {
                    Button(
                        onClick = {
                            onNextQuestion()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CatppuccinMochaColors.Blue,
                            contentColor = CatppuccinMochaColors.Base
                        )
                    ) {
                        Text("Next Question", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Button(
                        onClick = onCompleteQuiz,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CatppuccinMochaColors.Green,
                            contentColor = CatppuccinMochaColors.Base
                        )
                    ) {
                        Text("Complete Quiz", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

