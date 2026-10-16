package com.cognilens.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognilens.app.data.repositories.ImportState
import com.cognilens.app.ui.components.GradientButton
import com.cognilens.app.ui.components.PillBadge
import com.cognilens.app.ui.components.TimePickerCard
import com.cognilens.app.ui.onboarding.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupHubScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToManualTimetable: () -> Unit = {},
    onNavigateToAppSelection: () -> Unit = {}
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile Setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )

            IconButton(
                onClick = onNavigateToProfile,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "View Profile",
                    tint = colors.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isComplete) colors.primaryContainer else colors.surfaceContainerLow
            ),
            border = BorderStroke(
                1.dp,
                if (isComplete) colors.primary else colors.outlineVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Completion Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isComplete) colors.onPrimaryContainer else colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isComplete) "All 4 tasks complete! Ready to start." else "Complete all tasks below to unlock CogniLens",
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
                        trackColor = if (isComplete) colors.surface else colors.surfaceContainer,
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
                            fontWeight = FontWeight.Bold,
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
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
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Timetable PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PillBadge(
                            text = if (classCount > 0) "Loaded ($classCount days)" else "PDF Missing",
                            containerColor = if (classCount > 0) colors.primaryContainer else colors.errorContainer,
                            contentColor = if (classCount > 0) colors.onPrimaryContainer else colors.onErrorContainer
                        )
                    }

                    Button(
                        onClick = { filePicker.launch(arrayOf("application/pdf")) },
                        enabled = importState !is ImportState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        if (importState is ImportState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                        } else {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                AnimatedVisibility(visible = importState is ImportState.Error) {
                    val errMsg = (importState as? ImportState.Error)?.message ?: "Extraction issue"
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(colors.errorContainer, RoundedCornerShape(12.dp))
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
                            .background(colors.primaryContainer, RoundedCornerShape(12.dp))
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

        // 4. Monitored Applications
        SectionHeader(number = 4, title = "Monitored Applications")
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tracked Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select feeds and apps to monitor",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onNavigateToAppSelection,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Select Apps", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 5. BSMAS Section
        SectionHeader(number = 5, title = "Baseline Assessment (BSMAS)")
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Rate your social media usage over the last year (1 = Very Rarely, 5 = Very Often):",
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

        Spacer(modifier = Modifier.height(36.dp))

        GradientButton(
            text = "Complete Profile",
            onClick = { viewModel.completeOnboarding(onComplete) },
            enabled = isComplete,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SectionHeader(number: Int, title: String) {
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.linearGradient(colors = listOf(colors.primary, colors.secondary)),
                    shape = CircleShape
                )
        ) {
            Text(
                text = number.toString(),
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
    }
}

@Composable
fun BsmasQuestionItem(
    question: String,
    score: Int,
    onScoreChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
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
                        label = {
                            Text(
                                text = value.toString(),
                                fontWeight = if (score == value) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary
                        )
                    )
                }
            }
        }
    }
}
