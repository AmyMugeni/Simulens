package com.simulens.app.ui.profile

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.simulens.app.BuildConfig
import com.simulens.app.ui.components.*
import com.simulens.app.ui.onboarding.OnboardingViewModel
import com.simulens.app.ui.theme.*
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

    // 0: Identity & Sleep, 1: Academic Schedule, 2: Monitored Apps, 3: Habit Assessment (BSMAS)
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = currentStep > 0) {
        currentStep -= 1
    }

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

    LaunchedEffect(calendarState.events) {
        if (calendarState.events.isNotEmpty()) {
            viewModel.updateTimetableStatus(true)
        }
    }

    val bsmasQuestions = listOf(
        "You spend a lot of time thinking about social media or planning how to use it.",
        "You feel an urge to use social media more and more.",
        "You use social media in order to forget about personal problems.",
        "You have tried to cut down on the use of social media without success.",
        "You become restless or troubled if you are prohibited from using social media.",
        "You use social media so much that it has had a negative impact on your job/studies."
    )

    // Step Validation flags
    val isStep1Valid = userProfile.username.trim().length >= 3 && userProfile.isSleepConfigured
    val isStep2Valid = true // Schedule can be synced or skipped/empty initially
    val isStep3Valid = true // Monitored apps defaults exist
    val isStep4Valid = bsmasAnswers.all { it in 1..5 }

    val canProceed = when (currentStep) {
        0 -> isStep1Valid
        1 -> isStep2Valid
        2 -> isStep3Valid
        3 -> isStep4Valid
        else -> true
    }

    Scaffold(
        containerColor = InkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentStep > 0) {
                            IconButton(
                                onClick = { currentStep -= 1 },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(InkSurfaceHigh)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = InkTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                text = "Step ${currentStep + 1} of 4",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (currentStep) {
                                    0 -> "Your Rhythm"
                                    1 -> "Academic Schedule"
                                    2 -> "Focus Boundaries"
                                    else -> "Self-Reflection"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = InkTextPrimary,
                                modifier = Modifier.highlighter()
                            )
                        }
                    }

                    InkAvatar(
                        initial = userProfile.username.ifBlank { "S" },
                        size = 36.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented Progress Bar (4 steps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..3).forEach { index ->
                        val isCurrent = currentStep == index
                        val isPassed = currentStep > index
                        val barColor = when {
                            isPassed -> RiskLow
                            isCurrent -> IceBlue
                            else -> InkSurfaceHigh
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = InkSurfaceLow,
                border = BorderStroke(1.dp, InkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, InkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = InkTextSecondary)
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = { if (canProceed) currentStep += 1 },
                            enabled = canProceed,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceBlue,
                                contentColor = OnIceBlue,
                                disabledContainerColor = InkSurfaceHigh,
                                disabledContentColor = InkTextMuted
                            )
                        ) {
                            Text("Continue", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        GradientButton(
                            text = "Complete Profile",
                            onClick = { viewModel.completeOnboarding(onComplete) },
                            enabled = canProceed,
                            icon = Icons.Default.Check,
                            modifier = Modifier.widthIn(min = 180.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(300)) { width -> width } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(300)) { width -> -width } + fadeOut(tween(300)))
                } else {
                    (slideInHorizontally(tween(300)) { width -> -width } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(300)) { width -> width } + fadeOut(tween(300)))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "StepTransition"
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (step) {
                    0 -> Step1IdentityAndSleep(
                        username = userProfile.username,
                        wakeUpTime = userProfile.wakeUpTime,
                        sleepTime = userProfile.sleepTime,
                        onUsernameChange = viewModel::updateUsername,
                        onWakeUpTimeChange = viewModel::updateWakeUpTime,
                        onSleepTimeChange = viewModel::updateSleepTime
                    )
                    1 -> Step2AcademicSchedule(
                        calendarState = calendarState,
                        onConnectGoogle = {
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
                        onSync = { calendarVm.syncCalendar() },
                        onDisconnect = { calendarVm.disconnectGoogleAccount() },
                        onAddClassClick = { showAddClassDialog = true },
                        onDeleteClass = { calendarVm.deleteClassEvent(it) }
                    )
                    2 -> Step3MonitoredApps(
                        onSelectApps = onNavigateToAppSelection
                    )
                    3 -> Step4BsmasAssessment(
                        questions = bsmasQuestions,
                        answers = bsmasAnswers,
                        onScoreChange = viewModel::updateBsmasAnswer
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
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

// -------------------------------------------------------------------------------------------------
// Step 1: Personal Identification & Circadian Boundaries
// -------------------------------------------------------------------------------------------------
@Composable
private fun Step1IdentityAndSleep(
    username: String,
    wakeUpTime: LocalTime,
    sleepTime: LocalTime,
    onUsernameChange: (String) -> Unit,
    onWakeUpTimeChange: (LocalTime) -> Unit,
    onSleepTimeChange: (LocalTime) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = IceBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Personal Identification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter your preferred name or student identifier. This stays on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username / Student ID") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InkSurfaceHigh,
                    unfocusedContainerColor = InkSurfaceHigh,
                    focusedBorderColor = IceBlue,
                    unfocusedBorderColor = InkBorder,
                    focusedLabelColor = IceBlue,
                    unfocusedLabelColor = InkTextMuted,
                    focusedTextColor = InkTextPrimary,
                    unfocusedTextColor = InkTextPrimary
                )
            )
            if (username.trim().isNotEmpty() && username.trim().length < 3) {
                Text(
                    text = "Must be at least 3 characters",
                    color = RiskHigh,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = InkAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Circadian Rhythm Boundaries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Simulens protects your wind-down time before bed and the first half hour after waking.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            TimePickerCard(
                title = "Target Bedtime",
                time = sleepTime,
                onTimeChange = onSleepTimeChange
            )
            Spacer(modifier = Modifier.height(12.dp))
            TimePickerCard(
                title = "Target Wake-Up Time",
                time = wakeUpTime,
                onTimeChange = onWakeUpTimeChange
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Step 2: Academic Schedule & Google Calendar
// -------------------------------------------------------------------------------------------------
@Composable
private fun Step2AcademicSchedule(
    calendarState: CalendarSyncUiState,
    onConnectGoogle: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onAddClassClick: () -> Unit,
    onDeleteClass: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                        color = InkTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (calendarState.isConnected) {
                            calendarState.accountEmail ?: "Connected"
                        } else {
                            "Sync lecture hours to pause distractions in class"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextSecondary
                    )
                }

                PillBadge(
                    text = if (calendarState.isConnected) "ONLINE" else "LOCAL",
                    containerColor = if (calendarState.isConnected) IceBlueContainer else InkSurfaceHigh,
                    contentColor = if (calendarState.isConnected) IceBlueLight else InkTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (calendarState.isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSync,
                        enabled = !calendarState.isSyncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, IceBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IceBlue)
                    ) {
                        if (calendarState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = IceBlue)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now")
                        }
                    }

                    TextButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect", color = RiskHigh)
                    }
                }
            } else {
                Button(
                    onClick = onConnectGoogle,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IceBlue, contentColor = OnIceBlue)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Google Calendar", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = InkBorder)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Classes (${calendarState.events.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )

                IconButton(
                    onClick = onAddClassClick,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(InkAmber)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Class",
                        tint = InkOnAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (calendarState.events.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No classes added yet. You can sync via Google Calendar or tap '+' to manually add your lectures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextMuted
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                calendarState.events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(InkSurfaceHigh, RoundedCornerShape(12.dp))
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
                                containerColor = IceBlueContainer,
                                contentColor = IceBlueLight
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = event.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary
                                )
                                val locText = if (event.location.isNotBlank()) " • ${event.location}" else ""
                                Text(
                                    text = "${event.startTime} - ${event.endTime}$locText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkTextSecondary
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteClass(event.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = RiskHigh,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Step 3: Monitored Applications
// -------------------------------------------------------------------------------------------------
@Composable
private fun Step3MonitoredApps(
    onSelectApps: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = IceBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tracked Applications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose the feeds, short-form video apps, and messaging platforms you want Simulens to safeguard.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = InkSurfaceHigh,
                border = BorderStroke(1.dp, InkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apps Configuration",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkTextPrimary
                        )
                        Text(
                            text = "Default social media apps selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTextSecondary
                        )
                    }

                    Button(
                        onClick = onSelectApps,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IceBlue, contentColor = OnIceBlue)
                    ) {
                        Text("Select Apps", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Step 4: Baseline Assessment (BSMAS)
// -------------------------------------------------------------------------------------------------
@Composable
private fun Step4BsmasAssessment(
    questions: List<String>,
    answers: IntArray,
    onScoreChange: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = InkAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Habit Assessment (BSMAS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The Bergen Social Media Addiction Scale calibrates initial intervention sensitivity. Rate each statement over the past year:",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 = Very Rarely", style = MaterialTheme.typography.labelSmall, color = InkTextMuted)
                Text("5 = Very Often", style = MaterialTheme.typography.labelSmall, color = InkTextMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))

            questions.forEachIndexed { index, question ->
                BsmasWizardQuestionItem(
                    index = index + 1,
                    question = question,
                    score = answers[index],
                    onScoreChange = { newScore -> onScoreChange(index, newScore) }
                )
                if (index < questions.size - 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BsmasWizardQuestionItem(
    index: Int,
    question: String,
    score: Int,
    onScoreChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = InkSurfaceHigh,
        border = BorderStroke(1.dp, if (score in 1..5) IceBlue.copy(alpha = 0.5f) else InkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = if (score in 1..5) IceBlue else InkSurfaceLow,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (score in 1..5) OnIceBlue else InkTextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { value ->
                    val isSelected = score == value
                    Surface(
                        onClick = { onScoreChange(value) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) IceBlue else InkSurfaceLow,
                        border = BorderStroke(1.dp, if (isSelected) IceBlue else InkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) OnIceBlue else InkTextPrimary
                            )
                        }
                    }
                }
            }
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
        title = { Text("Add Class Schedule", fontWeight = FontWeight.Bold, color = InkTextPrimary) },
        containerColor = InkSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Subject / Class Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InkSurfaceHigh,
                        unfocusedContainerColor = InkSurfaceHigh,
                        focusedBorderColor = IceBlue,
                        unfocusedBorderColor = InkBorder,
                        focusedTextColor = InkTextPrimary,
                        unfocusedTextColor = InkTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Day of Week:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InkTextPrimary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DayOfWeek.entries.toTypedArray()) { day ->
                        val isSelected = selectedDay == day
                        Surface(
                            onClick = { selectedDay = day },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IceBlue else InkSurfaceHigh,
                            border = BorderStroke(1.dp, if (isSelected) IceBlue else InkBorder)
                        ) {
                            Text(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                color = if (isSelected) OnIceBlue else InkTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
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
                enabled = summary.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = IceBlue, contentColor = OnIceBlue)
            ) {
                Text("Add Class")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkTextSecondary) }
        }
    )
}
