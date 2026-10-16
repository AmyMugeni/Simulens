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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognilens.app.data.repositories.ImportState
import com.cognilens.app.ui.components.GradientButton
import com.cognilens.app.ui.components.PillBadge
import com.cognilens.app.ui.components.SectionHeader
import com.cognilens.app.ui.components.TimePickerCard
import com.cognilens.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: OnboardingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppSelection: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Timetable Management ViewModel
    val importVm: TimetableImportViewModel = viewModel(factory = TimetableImportViewModel.factory(context))
    val importState by importVm.state.collectAsState()
    val classCount by importVm.entryCount.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importVm.uploadAndParsePdf(uri, context) {
                viewModel.updateTimetableStatus(true)
                scope.launch {
                    snackbarHostState.showSnackbar("Timetable updated successfully")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Profile",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onSurface
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveProfile {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Profile updated successfully")
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Save",
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar Surface
            Surface(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = colors.primaryContainer,
                border = BorderStroke(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(colors = listOf(colors.primary, colors.secondary))
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile Avatar",
                        modifier = Modifier.size(50.dp),
                        tint = colors.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Identification
            SectionHeader(title = "Personal Identification")

            OutlinedTextField(
                value = userProfile.username,
                onValueChange = viewModel::updateUsername,
                label = { Text("Username / Student ID") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerLow,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section 2: Circadian Rhythm
            SectionHeader(title = "Circadian Rhythm")

            TimePickerCard(
                title = "Wake-Up Time",
                time = userProfile.wakeUpTime,
                onTimeChange = { newTime: LocalTime -> viewModel.updateWakeUpTime(newTime) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimePickerCard(
                title = "Target Bedtime",
                time = userProfile.sleepTime,
                onTimeChange = { newTime: LocalTime -> viewModel.updateSleepTime(newTime) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section 3: Timetable Management
            SectionHeader(title = "Academic Timetable")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Timetable Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            PillBadge(
                                text = if (userProfile.isTimetableConfigured) "Configured ($classCount days)" else "Not Configured",
                                containerColor = if (userProfile.isTimetableConfigured) colors.primaryContainer else colors.errorContainer,
                                contentColor = if (userProfile.isTimetableConfigured) colors.onPrimaryContainer else colors.onErrorContainer
                            )
                        }

                        Row {
                            IconButton(
                                onClick = { filePicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer)
                            ) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = "Replace Timetable",
                                    tint = colors.onPrimaryContainer
                                )
                            }
                            if (userProfile.isTimetableConfigured) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        importVm.clearTimetable {
                                            viewModel.updateTimetableStatus(false)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Timetable cleared")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.errorContainer)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Clear Timetable",
                                        tint = colors.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = importState is ImportState.Loading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            color = colors.primary
                        )
                    }

                    if (importState is ImportState.Error) {
                        Text(
                            text = (importState as ImportState.Error).message,
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 4: Monitored Applications
            SectionHeader(title = "Monitored Applications")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tracked Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select which apps CogniLens tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onNavigateToAppSelection,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("Configure", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 5: Assessment Baseline
            SectionHeader(title = "Assessment Baseline")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    ProfileReadOnlyItem("BSMAS Baseline Score", userProfile.bsmasScore.toString())
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            GradientButton(
                text = "Save All Changes",
                onClick = {
                    viewModel.saveProfile {
                        scope.launch {
                            snackbarHostState.showSnackbar("Changes saved successfully")
                        }
                    }
                },
                icon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProfileReadOnlyItem(label: String, value: String) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Surface(
            shape = CircleShape,
            color = colors.secondaryContainer
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
