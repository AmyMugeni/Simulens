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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.simulens.app.BuildConfig
import com.simulens.app.domain.bsmas.BsmasBand
import com.simulens.app.domain.bsmas.BsmasScoring
import com.simulens.app.ui.components.GradientButton
import com.simulens.app.ui.components.InkAvatar
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.PencilDoodle
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.components.SectionHeader
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.TapeDoodle
import com.simulens.app.ui.components.TimePickerCard
import com.simulens.app.ui.components.highlighter
import com.simulens.app.ui.theme.*
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
fun ProfileScreen(
    viewModel: OnboardingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppSelection: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val calendarVm: CalendarSyncViewModel = viewModel(factory = CalendarSyncViewModel.factory(context))
    val calendarState by calendarVm.uiState.collectAsState()
    var showAddClassDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Edit Profile",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.highlighter()
                        )
                        Spacer(Modifier.width(8.dp))
                        SquigglyDoodle(color = IceBlue)
                    }
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
            // Ink Avatar Badge
            InkAvatar(
                initial = userProfile.username.ifBlank { "S" },
                size = 92.dp
            )

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

            // Section 3: Google Calendar Academic Schedule
            SectionHeader(title = "Academic Schedule (Google Calendar)")

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
                                        Icons.Default.Delete,
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
                            "Select which apps Simulens tracks",
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

            // Section 5: Assessment Baseline (BSMAS)
            val bsmasAnswers by viewModel.bsmasAnswers.collectAsState()
            val bsmasHistory by viewModel.bsmasHistory.collectAsState()
            var isRetakeExpanded by remember { mutableStateOf(false) }
            var expandedHistoryId by remember { mutableStateOf<Long?>(null) }

            val currentResult = BsmasScoring.score(bsmasAnswers.toList())
            val currentBand = currentResult?.band ?: when {
                userProfile.bsmasScore <= 12 -> BsmasBand.LOWER
                userProfile.bsmasScore <= 18 -> BsmasBand.MODERATE
                else -> BsmasBand.ELEVATED
            }

            SectionHeader(title = "Assessment Baseline (BSMAS)")

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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BSMAS Risk Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bergen Social Media Addiction Scale baseline assessment",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${userProfile.bsmasScore} / 30",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            PillBadge(
                                text = currentBand.name,
                                containerColor = if (currentBand == BsmasBand.ELEVATED) colors.errorContainer else colors.primaryContainer,
                                contentColor = if (currentBand == BsmasBand.ELEVATED) colors.onErrorContainer else colors.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { isRetakeExpanded = !isRetakeExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRetakeExpanded) colors.secondaryContainer else colors.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRetakeExpanded) Icons.Default.ExpandLess else Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isRetakeExpanded) colors.onSecondaryContainer else colors.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRetakeExpanded) "Hide Questionnaire Form" else "Retake Questionnaire Now",
                            fontWeight = FontWeight.Bold,
                            color = if (isRetakeExpanded) colors.onSecondaryContainer else colors.onPrimary
                        )
                    }

                    if (isRetakeExpanded) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = colors.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Answer all 6 questions (1 = Very Rarely, 5 = Very Often):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OnboardingViewModel.BSMAS_QUESTIONS.forEachIndexed { index, question ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                                border = BorderStroke(1.dp, colors.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${index + 1}. $question",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        (1..5).forEach { value ->
                                            FilterChip(
                                                selected = bsmasAnswers.getOrElse(index) { 0 } == value,
                                                onClick = { viewModel.updateBsmasAnswer(index, value) },
                                                label = {
                                                    Text(
                                                        text = value.toString(),
                                                        fontWeight = if (bsmasAnswers.getOrElse(index) { 0 } == value) FontWeight.Bold else FontWeight.Normal
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

                        Spacer(modifier = Modifier.height(16.dp))

                        val calculatedScore = bsmasAnswers.sum()
                        val calculatedResult = BsmasScoring.score(bsmasAnswers.toList())

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Calculated Total: $calculatedScore / 30",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "Risk Band: ${calculatedResult?.band?.name ?: "Incomplete"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    if (bsmasAnswers.all { it in 1..5 }) {
                                        viewModel.recordNewBsmasAssessment(bsmasAnswers) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("New BSMAS assessment recorded & saved!")
                                            }
                                        }
                                        isRetakeExpanded = false
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Please answer all 6 questions before recording.")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = bsmasAnswers.all { it in 1..5 }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Record Assessment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (bsmasHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Assessment History (${bsmasHistory.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        bsmasHistory.forEach { historyEntity ->
                            val scoreObj = BsmasScoring.score(historyEntity.items())
                            val isExpanded = expandedHistoryId == historyEntity.id

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(colors.surfaceContainer, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatEpochMs(historyEntity.takenAtEpochMs),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Score: ${historyEntity.total} / 30",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val band = scoreObj?.band ?: BsmasBand.MODERATE
                                        PillBadge(
                                            text = band.name,
                                            containerColor = if (band == BsmasBand.ELEVATED) colors.errorContainer else colors.primaryContainer,
                                            contentColor = if (band == BsmasBand.ELEVATED) colors.onErrorContainer else colors.onPrimaryContainer
                                        )
                                        IconButton(
                                            onClick = {
                                                expandedHistoryId = if (isExpanded) null else historyEntity.id
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Details",
                                                tint = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = colors.outlineVariant)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    OnboardingViewModel.BSMAS_QUESTIONS.forEachIndexed { idx, qText ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Q${idx + 1}: ${qText.take(35)}...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${historyEntity.items().getOrElse(idx) { 0 }} / 5",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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

            // Section 6: Account Management & Database Reset
            SectionHeader(title = "Account Management")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Sign Out & Reset App Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clears all local database records, sessions, and profile data so you can sign out and sign back in fresh as yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.resetUserDatabaseAndSignOut {
                                onSignOut()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App & Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
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

private fun formatEpochMs(epochMs: Long): String {
    return runCatching {
        val instant = java.time.Instant.ofEpochMilli(epochMs)
        val zone = java.time.ZoneId.systemDefault()
        instant.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm"))
    }.getOrDefault("Date unavailable")
}