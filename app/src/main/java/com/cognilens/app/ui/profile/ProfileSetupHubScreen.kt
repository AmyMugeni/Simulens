package com.cognilens.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognilens.app.data.repositories.ImportState
import com.cognilens.app.ui.onboarding.OnboardingViewModel
import com.cognilens.app.domain.model.UserProfile
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupHubScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToManualTimetable: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val bsmasAnswers by viewModel.bsmasAnswers.collectAsState()
    val context = LocalContext.current

    val importVm: TimetableImportViewModel = viewModel(factory = TimetableImportViewModel.factory(context))
    val importState by importVm.state.collectAsState()
    val classCount by importVm.entryCount.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importVm.uploadAndParsePdf(uri, context) {
                viewModel.updateTimetableStatus(true)
            }
        }
    }

    val progress = viewModel.calculateCompletionPercentage()
    val isComplete = progress >= 1.0f
    val colors = MaterialTheme.colorScheme

    val bsmasQuestions = listOf(
        "You spend a lot of time thinking about social media or planning how to use it.",
        "You feel an urge to use social media more and more.",
        "You use social media in order to forget about personal problems.",
        "You have tried to cut down on the use of social media without success.",
        "You become restless or troubled if you are prohibited from using social media.",
        "You use social media so much that it has had a negative impact on your job/studies."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToProfile) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "View Profile",
                    tint = colors.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (isComplete) colors.primaryContainer else colors.surfaceContainerLow
            ),
            border = BorderStroke(
                1.dp,
                if (isComplete) colors.primary else colors.outlineVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile Completion",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isComplete) colors.onPrimaryContainer else colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isComplete) "All requirements met!" else "Complete all 4 tasks to unlock CogniLens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isComplete) colors.onPrimaryContainer else colors.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        color = colors.primary,
                        trackColor = if (isComplete) colors.surface else colors.surfaceVariant,
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round
                    )
                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 1. Username
        SectionHeader(number = 1, title = "Personal Identification")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = userProfile.username,
            onValueChange = viewModel::updateUsername,
            label = { Text("Username / Student ID") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 2. Sleep schedule
        SectionHeader(number = 2, title = "Circadian Boundaries")
        Spacer(modifier = Modifier.height(12.dp))
        TimePickerCard(
            title = "Wake-Up Time",
            time = userProfile.wakeUpTime,
            onTimeChange = { newTime -> viewModel.updateWakeUpTime(newTime) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        TimePickerCard(
            title = "Target Bedtime",
            time = userProfile.sleepTime,
            onTimeChange = { newTime -> viewModel.updateSleepTime(newTime) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Timetable Card Section
        SectionHeader(number = 3, title = "Academic Timetable")
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Import Timetable Document", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (classCount > 0) "Status: Loaded ($classCount days in database)" else "Status: Missing PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (classCount > 0) colors.primary else colors.error
                        )
                    }

                    Button(
                        onClick = { filePicker.launch(arrayOf("application/pdf")) },
                        enabled = importState !is ImportState.Loading
                    ) {
                        if (importState is ImportState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                        } else {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select PDF")
                        }
                    }
                }
                AnimatedVisibility(visible = importState is ImportState.Error) {
                    val errMsg = (importState as? ImportState.Error)?.message ?: "Extraction connection lost"
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(colors.errorContainer, MaterialTheme.shapes.small)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = colors.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errMsg, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
                    }
                }
                AnimatedVisibility(visible = importState is ImportState.Success) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(colors.primaryContainer, MaterialTheme.shapes.small)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Timetable loaded securely inside local storage!", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 4. BSMAS Section
        SectionHeader(number = 4, title = "Baseline Assessment (BSMAS)")
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Rate your social media use over the last year. 1 = Very Rarely, 5 = Very Often.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        bsmasQuestions.forEachIndexed { index, question ->
            BsmasQuestionItem(
                question = question,
                score = bsmasAnswers[index],
                onScoreChange = { newScore ->
                    viewModel.updateBsmasAnswer(index, newScore)
                }
            )
            if (index < bsmasQuestions.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { viewModel.completeOnboarding(onComplete) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isComplete,
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Complete Profile", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SectionHeader(number: Int, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Text(
                text = number.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerCard(
    title: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = time.format(formatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                    showDialog = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}

@Composable
fun BsmasQuestionItem(
    question: String,
    score: Int,
    onScoreChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = question, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { value ->
                    FilterChip(
                        selected = score == value,
                        onClick = { onScoreChange(value) },
                        label = { Text(value.toString()) }
                    )
                }
            }
        }
    }
}
