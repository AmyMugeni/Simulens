package com.simulens.app.ui.profile

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.simulens.app.BuildConfig
import com.simulens.app.ui.components.GradientButton
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.components.TimePickerCard
import com.simulens.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

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
    val scope = rememberCoroutineScope()

    val calendarVm: CalendarSyncViewModel = viewModel(factory = CalendarSyncViewModel.factory(context))
    val calendarState by calendarVm.uiState.collectAsState()

    var showAddClassDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email.orEmpty().ifBlank { "Connected Account" }
            val accountObj = account?.account

            scope.launch(Dispatchers.IO) {
                var token = ""
                if (accountObj != null) {
                    try {
                        val scopeStr = "oauth2:https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar"
                        token = GoogleAuthUtil.getToken(context, accountObj, scopeStr)
                    } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                        Log.w("Simulens", "UserRecoverableAuthException during GoogleAuthUtil.getToken", e)
                        withContext(Dispatchers.Main) {
                            context.startActivity(e.intent)
                        }
                        return@launch
                    } catch (e: Exception) {
                        Log.w("Simulens", "GoogleAuthUtil.getToken failed, falling back to idToken", e)
                        token = account.idToken.orEmpty()
                    }
                } else {
                    token = account?.idToken.orEmpty()
                }

                withContext(Dispatchers.Main) {
                    if (token.isNotBlank()) {
                        calendarVm.connectGoogleAccount(token, email)
                    } else {
                        snackbarHostState.showSnackbar("Could not obtain Google authorization token.")
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("Simulens", "Google Sign-In failed with status code ${e.statusCode}", e)
            val msg = when (e.statusCode) {
                10 -> "Google Sign-In Developer Error (Code 10). Ensure 'com.simulens.app' and your debug SHA-1 fingerprint are registered in Google Cloud Console."
                7 -> "Google Sign-In Network Error (Code 7). Check your internet connection."
                12500 -> "Google Sign-In Failed (Code 12500). Please check Google Play Services."
                else -> "Google Sign-In failed (Code ${e.statusCode}): ${e.localizedMessage}"
            }
            scope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        } catch (e: Exception) {
            Log.e("Simulens", "Google Sign-In failed", e)
            scope.launch {
                snackbarHostState.showSnackbar("Google Sign-In failed: ${e.localizedMessage}")
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        text = if (isComplete) "All 4 tasks complete! Ready to start." else "Complete all tasks below to unlock Simulens",
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

        // 3. Google Calendar & Class Schedule Section
        SectionHeader(number = 3, title = "Academic Schedule (Google Calendar)")
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
                            text = if (calendarState.isConnected) "Google Account Connected" else "Google Calendar Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (calendarState.isConnected) {
                                calendarState.accountEmail ?: "Connected"
                            } else {
                                "Offline Mode (Local Storage Only)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    PillBadge(
                        text = if (calendarState.isConnected) "ONLINE" else "OFFLINE",
                        containerColor = if (calendarState.isConnected) colors.primaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (calendarState.isConnected) colors.onPrimaryContainer else colors.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (calendarState.isConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { calendarVm.syncCalendar() },
                            enabled = !calendarState.isSyncing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (calendarState.isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync Now")
                            }
                        }

                        TextButton(
                            onClick = { calendarVm.disconnectGoogleAccount() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Disconnect", color = colors.error)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(
                                    Scope("https://www.googleapis.com/auth/calendar.events"),
                                    Scope("https://www.googleapis.com/auth/calendar")
                                )
                            if (webClientId.isNotBlank()) {
                                gsoBuilder.requestIdToken(webClientId)
                            }
                            val client = GoogleSignIn.getClient(context, gsoBuilder.build())
                            googleSignInLauncher.launch(client.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connect Google Account", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Schedule (${calendarState.events.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )

                    IconButton(
                        onClick = { showAddClassDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.secondaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Class",
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (calendarState.events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    calendarState.events.forEach { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val dayAbbrev = event.dayOfWeek.take(3).uppercase()
                                PillBadge(
                                    text = if (dayAbbrev.isNotBlank()) dayAbbrev else "CLASS",
                                    containerColor = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = event.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )
                                    val locText = if (event.location.isNotBlank()) " • ${event.location}" else ""
                                    Text(
                                        text = "${event.startTime} - ${event.endTime}$locText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { calendarVm.deleteClassEvent(event.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onConfirm = { summary, dayOfWeek, startTime, endTime ->
                calendarVm.addClassEvent(
                    summary = summary,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    onSuccess = { viewModel.updateTimetableStatus(true) }
                )
                showAddClassDialog = false
            }
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onConfirm: (summary: String, dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime) -> Unit
) {
    var summary by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var startTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(11, 0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Class Schedule", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Subject / Class Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Day of Week:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DayOfWeek.entries.toTypedArray()) { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) }
                        )
                    }
                }

                TimePickerCard(title = "Start Time", time = startTime, onTimeChange = { startTime = it })
                TimePickerCard(title = "End Time", time = endTime, onTimeChange = { endTime = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (summary.isNotBlank()) {
                        onConfirm(summary, selectedDay, startTime, endTime)
                    }
                },
                enabled = summary.isNotBlank()
            ) {
                Text("Add Class")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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