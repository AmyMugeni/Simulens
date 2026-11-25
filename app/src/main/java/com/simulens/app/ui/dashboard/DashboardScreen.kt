package com.simulens.app.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.rag.ChatScreen
import com.simulens.app.ui.components.ActiveSessionBanner
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.InkAvatar
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.TapeDoodle
import com.simulens.app.ui.components.highlighter
import com.simulens.app.ui.intervention.InterventionScreen
import com.simulens.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToProfile: () -> Unit,
    onNavigateToRag: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToMode: () -> Unit = {},
    onNavigateToAppSelection: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val status by viewModel.activityStatus.collectAsState()
    val state by viewModel.state.collectAsState()
    val bsmasResult by viewModel.bsmasResult.collectAsState()
    val anchor by viewModel.anchor.collectAsState()
    val riskPrediction by viewModel.riskPrediction.collectAsState()
    val chatLaunchVm: com.simulens.app.rag.ChatLaunchViewModel = viewModel()
    val context = LocalContext.current
    var isBatteryUnrestricted by remember {
        mutableStateOf(com.simulens.app.data.tracker.PermissionUtils.isIgnoringBatteryOptimizations(context))
    }
    var batteryBannerDismissed by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryUnrestricted = com.simulens.app.data.tracker.PermissionUtils.isIgnoringBatteryOptimizations(context)
                viewModel.syncUsageData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val analytics = remember(state) { buildAnalytics(state) }
    val colors = MaterialTheme.colorScheme

    // 5-Tab Navigation State: 0: Home, 1: Schedule, 2: Trends, 3: Nudges, 4: Buddy
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val dateFormatted = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))
    }

    Scaffold(
        containerColor = InkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = InkSurfaceLow,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, "Home", Icons.Default.Home),
                    Triple(1, "Agenda", Icons.Default.CalendarToday),
                    Triple(2, "Trends", Icons.Default.BarChart),
                    Triple(3, "Nudges", Icons.Default.Shield),
                    Triple(4, "Buddy", Icons.AutoMirrored.Filled.Chat)
                )

                navItems.forEach { (index, label, icon) ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) IceBlue else InkTextMuted
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) IceBlue else InkTextMuted,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = InkSurfaceHigh,
                            selectedIconColor = IceBlue,
                            unselectedIconColor = InkTextMuted,
                            selectedTextColor = IceBlue,
                            unselectedTextColor = InkTextMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            3 -> {
                // ==================== TAB 3: NUDGES ====================
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    InterventionScreen(
                        showBackButton = false,
                        onNavigateToRag = { selectedTab = 4 },
                        username = userProfile.username
                    )
                }
            }

            4 -> {
                // ==================== TAB 4: Buddy (CHATBOT) ====================
                val chatViewModel: com.simulens.app.rag.ChatViewModel = viewModel()
                val pendingSeed by chatLaunchVm.pending.collectAsState()

                LaunchedEffect(pendingSeed) {
                    pendingSeed?.let { seed ->
                        chatViewModel.startFromSeed(seed)
                    }
                }

                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        showBackButton = false,
                        onNavigateBack = { selectedTab = 0 },
                        username = userProfile.username
                    )
                }
            }

            else -> {
                // ==================== TABS 0, 1, 2: SCROLLABLE BODY ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(InkBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
                ) {
                    if (selectedTab == 0) {
                        // ==================== 0: HOME / DASHBOARD ====================
                        var isDeepDive by rememberSaveable { mutableStateOf(false) }

                        // 1. Top Utility Row: Date on Left, Squiggle + Avatar on Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                SquigglyDoodle(color = IceBlue, modifier = Modifier.padding(end = 12.dp))
                                InkAvatar(
                                    initial = userProfile.username.ifBlank { "S" },
                                    size = 38.dp,
                                    onClick = onNavigateToProfile
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Greeting Header: "Good Evening," top line, Username bottom line with exact highlighter width
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${viewModel.getGreeting()},",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile.username.ifBlank { "Student" },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = InkTextPrimary,
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .highlighter()
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 3. Active Session / Lecture Banner
                        val ongoing = status.todayClasses.find { it.isOngoing }
                        if (ongoing != null) {
                            ActiveSessionBanner(
                                title = "● In class · ${formatDuration(ongoing.remainingMinutes.toDouble())} left",
                                subtitle = "${ongoing.summary} >",
                                onClick = { selectedTab = 1 }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (status.todayClasses.isNotEmpty()) {
                            ActiveSessionBanner(
                                title = "Academic schedule · ${status.todayClassesCount} classes today",
                                subtitle = "Next: ${status.nextActivity} at ${status.nextActivityTime} >",
                                onClick = { selectedTab = 1 }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 3b. Battery Optimization Warning (prevents Oppo/Samsung background freezing)
                        if (!isBatteryUnrestricted && !batteryBannerDismissed) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = InkSurface,
                                border = BorderStroke(1.dp, InkAmber.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryAlert,
                                        contentDescription = null,
                                        tint = InkAmber,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "Background tracking is restricted",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = InkTextPrimary
                                        )
                                        Text(
                                            text = "Allow unrestricted battery so live usage stays synced with Digital Wellbeing.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkTextSecondary
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            com.simulens.app.data.tracker.PermissionUtils.requestIgnoreBatteryOptimizations(context)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Allow", color = InkAmber, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { batteryBannerDismissed = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = InkTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 4. View Mode Switcher: [ Overview | Deep Dive ]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(InkSurfaceLow, RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf(
                                false to "Overview",
                                true to "Deep Dive Analytics"
                            )
                            modes.forEach { (modeVal, label) ->
                                val selected = isDeepDive == modeVal
                                Surface(
                                    onClick = { isDeepDive = modeVal },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) IceBlue else Color.Transparent,
                                    border = BorderStroke(1.5.dp, if (selected) IceBlue else InkBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) InkBackground else InkTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Behavioral Wellness Cards (Score, Risk Level, Timeline)
                        WellnessDashboardSection(state = state, deepDive = isDeepDive)

                        Spacer(modifier = Modifier.height(20.dp))

                        val currentTrajectory = remember(anchor, state.recent, bsmasResult) {
                            val a = anchor
                            val r = state.recent
                            if (a != null && r != null) {
                                val start = com.simulens.app.domain.rules.intervention.PeriodStats(
                                    days = com.simulens.app.domain.rules.intervention.AnchorCalculator.WINDOW_DAYS,
                                    activeDays = a.activeDays,
                                    medianDailyMins = a.medianDailyMins,
                                    medianDailySessions = a.medianDailySessions,
                                    medianSessionMins = a.medianSessionMins,
                                    lateNightShare = a.lateNightShare
                                )
                                com.simulens.app.domain.rules.intervention.AnchorCalculator.compare(
                                    start, r, a.bsmasTotal, bsmasResult?.total
                                ).trajectory
                            } else null
                        }

                        MLClassifierCard(
                            prediction = riskPrediction,
                            trajectory = currentTrajectory,
                            onNavigateToRag = {
                                val seed = com.simulens.app.domain.chat.ChatSeed(
                                    id = "ml_risk_${System.currentTimeMillis()}",
                                    type = com.simulens.app.domain.rules.intervention.InterventionType.HIGH_DAILY_DEVIATION,
                                    appName = analytics.mostUsedApp?.displayName ?: "monitored apps",
                                    packageName = analytics.mostUsedApp?.packageName ?: "",
                                    riskProbability = riskPrediction?.probability
                                )
                                chatLaunchVm.offer(seed)
                                selectedTab = 4
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Most Used App Spotlight
                        MostUsedAppCard(
                            mostUsedApp = analytics.mostUsedApp,
                            topApps = analytics.topApps,
                            onConfigureApps = onNavigateToAppSelection
                        )
                    } else if (selectedTab == 1) {
                        // ==================== 1: ACADEMIC SCHEDULE ====================

                        // 1. Top Utility Row: Date on Left, Squiggle + Avatar on Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                SquigglyDoodle(color = IceBlue, modifier = Modifier.padding(end = 12.dp))
                                InkAvatar(
                                    initial = userProfile.username.ifBlank { "S" },
                                    size = 38.dp,
                                    onClick = onNavigateToProfile
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Full-Width Title directly BELOW avatar row
                        Text(
                            text = "Academic schedule",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkTextPrimary,
                            modifier = Modifier
                                .wrapContentWidth()
                                .highlighter()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Active / Happening Now Card
                        val ongoingClass = status.todayClasses.find { it.isOngoing }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (ongoingClass != null) IceBlueBanner else InkSurface
                            ),
                            border = BorderStroke(1.dp, if (ongoingClass != null) IceBlue else InkBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                if (ongoingClass != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = InkAmber,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "HAPPENING NOW",
                                            color = InkOnAmber,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = ongoingClass.summary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val locText = if (ongoingClass.location.isNotBlank()) "${ongoingClass.location} | " else ""
                                    Text(
                                        text = "$locText${ongoingClass.startTimeStr} - ${ongoingClass.endTimeStr}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LinearProgressIndicator(
                                        progress = { ongoingClass.progressPercent },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.3f)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "In session",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "${formatDuration(ongoingClass.remainingMinutes.toDouble())} remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Gentle nudges are on for this class.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = status.displayScheduleText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = InkTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (status.todayClasses.isNotEmpty())
                                            "Next class: ${status.nextActivity} at ${status.nextActivityTime}"
                                        else "No academic classes currently scheduled for today.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Today's Agenda Header
                        val doneCount = status.todayClasses.count { it.isCompleted }
                        val nowCount = status.todayClasses.count { it.isOngoing }
                        val toGoCount = status.todayClasses.count { it.isUpcoming }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's agenda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = InkTextPrimary
                            )

                            if (status.todayClasses.isNotEmpty()) {
                                Text(
                                    text = "$doneCount done · $nowCount now · $toGoCount to go",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (status.todayClasses.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = InkSurface),
                                border = BorderStroke(1.dp, InkBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.EventAvailable, null, tint = IceBlue, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Free Day", style = MaterialTheme.typography.titleMedium, color = InkTextPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("You have no scheduled lectures for today.", style = MaterialTheme.typography.bodySmall, color = InkTextSecondary)
                                }
                            }
                        } else {
                            status.todayClasses.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (item.isOngoing) InkSurfaceHigh else InkSurface,
                                    border = BorderStroke(1.dp, if (item.isOngoing) InkAmber.copy(alpha = 0.6f) else InkBorder),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        color = if (item.isOngoing) InkAmber else if (item.isCompleted) RiskLow else IceBlue,
                                                        shape = CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "${item.startTimeStr} - ${item.endTimeStr}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = InkTextSecondary
                                                )
                                                Text(
                                                    text = item.summary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = InkTextPrimary
                                                )
                                                if (item.location.isNotBlank()) {
                                                    Text(
                                                        text = item.location,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = InkTextMuted
                                                    )
                                                }
                                            }
                                        }

                                        val (badgeText, badgeBg, badgeFg) = when {
                                            item.isOngoing -> Triple("NOW", InkAmber, InkOnAmber)
                                            item.isCompleted -> Triple("DONE", InkSurfaceHigh, InkTextSecondary)
                                            else -> Triple("UPCOMING", IceBlueContainer, IceBlueLight)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = badgeText,
                                                color = badgeFg,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedTab == 2) {
                        // ==================== 2: STATS & TRENDS ====================

                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Your phone habits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Stats & trends",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.highlighter()
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                SquigglyDoodle(color = IceBlue, modifier = Modifier.padding(end = 12.dp))
                                InkAvatar(
                                    initial = userProfile.username.ifBlank { "S" },
                                    size = 36.dp,
                                    onClick = onNavigateToProfile
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Interactive Day and Metric Selector + Baseline Analytics
                        BaselineSection(
                            baselines = state.baselines,
                            spanDays = state.baselineSpanDays
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AnchorCard(
                            anchor = anchor,
                            recent = state.recent,
                            weeklyTotals = state.weeklyTotals,
                            bsmasNow = bsmasResult?.total,
                            spanDays = state.baselineSpanDays,
                            onReset = { viewModel.resetAnchor() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        BsmasBaselineCard(
                            result = bsmasResult,
                            prediction = riskPrediction,
                            baselines = state.baselines,
                            onRetake = onNavigateToProfile,
                            onTalk = {
                                val seed = com.simulens.app.domain.chat.ChatSeed(
                                    id = "bsmas_align_${System.currentTimeMillis()}",
                                    type = com.simulens.app.domain.rules.intervention.InterventionType.HIGH_DAILY_DEVIATION,
                                    appName = analytics.mostUsedApp?.displayName ?: "monitored apps",
                                    packageName = analytics.mostUsedApp?.packageName ?: "",
                                    bsmasScore = bsmasResult?.total
                                )
                                chatLaunchVm.offer(seed)
                                selectedTab = 4
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // CSV Export Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = InkSurface),
                            border = BorderStroke(1.dp, InkBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "CSV Data Export",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Export and view historical behavioral sessions & tracking data for ML forecasting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkTextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val isExporting by viewModel.isExportingCsv.collectAsState()
                                val context = LocalContext.current

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.exportCsv(context) { message ->
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = !isExporting,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = InkAmber,
                                            contentColor = InkOnAmber
                                        )
                                    ) {
                                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export CSV", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.shareExportedCsv(context) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, IceBlue.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = IceBlue, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", color = IceBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
fun MostUsedAppCard(
    mostUsedApp: AppUsageStat?,
    topApps: List<AppUsageStat>,
    onConfigureApps: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            border = BorderStroke(1.dp, colors.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .background(colors.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Spotlight",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Monitored app usage today",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Yellow Manage Apps Button BELOW App Spotlight title
                Button(
                    onClick = onConfigureApps,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAmber,
                        contentColor = Color(0xFF0B1220)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF0B1220),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manage Monitored Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B1220)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (mostUsedApp != null && mostUsedApp.totalMinutes > 0) {
                    // Hero #1 Top App Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(1.dp, InkAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                AppIcon(packageName = mostUsedApp.packageName, size = 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mostUsedApp.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatDuration(mostUsedApp.totalMinutes)} total today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = InkAmber.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", mostUsedApp.percentage),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = InkAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Top Monitored Usage",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    topApps.take(4).forEachIndexed { idx, app ->
                        val barColor = if (idx == 0) InkAmber else IceBlue
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    AppIcon(packageName = app.packageName, size = 22.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = app.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${formatDuration(app.totalMinutes)} (${String.format(Locale.US, "%.0f%%", app.percentage)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            LinearProgressIndicator(
                                progress = { (app.percentage / 100f).coerceIn(0.02f, 1.0f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = barColor,
                                trackColor = colors.surfaceContainerHigh
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainer, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No monitored app usage recorded yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        PaperclipDoodle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = (-10).dp)
                .rotate(12f)
        )
    }
}

@Composable
fun WeeklyUsageGraphCard(
    trends: List<DailyUsageTrend>,
    totalWeeklyMins: Double,
    avgDuration: Double
) {
    val maxMins = trends.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(30.0) ?: 60.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(InkSurfaceHigh, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = IceBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Weekly Trends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkTextPrimary
                        )
                        Text(
                            text = "Total: ${formatDuration(totalWeeklyMins)} • Avg: ${formatDuration(avgDuration)}/session",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InkSurfaceHigh
                ) {
                    Text(
                        text = "7 Days",
                        color = IceBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                trends.forEach { trend ->
                    val heightRatio = (trend.totalMinutes / maxMins).toFloat().coerceIn(0.08f, 1.0f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = if (trend.totalMinutes > 0) formatDuration(trend.totalMinutes) else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (trend.isToday) InkAmber else InkTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight(heightRatio)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (trend.isToday) SolidColor(InkAmber)
                                    else if (trend.totalMinutes > 0) SolidColor(IceBlue)
                                    else SolidColor(InkSurfaceHigh)
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = trend.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (trend.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (trend.isToday) InkAmber else InkTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeOfDayInsightsCard(
    peakLabel: String,
    stats: List<TimeOfDayStat>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(InkSurfaceHigh, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = IceBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Time of Day Pattern",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InkSurfaceHigh
                ) {
                    Text(
                        text = "Circadian",
                        color = IceBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = InkSurfaceHigh,
                border = BorderStroke(1.dp, InkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = InkAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Peak Usage Window: $peakLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            stats.forEach { stat ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stat.periodLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = InkTextPrimary
                        )
                        Text(
                            text = "${formatDuration(stat.totalMinutes)} (${stat.sessionCount} sessions)",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { stat.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = IceBlue,
                        trackColor = InkSurfaceHigh
                    )
                }
            }
        }
    }
}

data class DashboardAnalytics(
    val mostUsedApp: AppUsageStat? = null,
    val topApps: List<AppUsageStat> = emptyList(),
    val weeklyTrends: List<DailyUsageTrend> = emptyList(),
    val totalWeeklyMinutes: Double = 0.0,
    val avgSessionDurationMins: Double = 0.0,
    val peakUsageTimeLabel: String = "Not enough data yet",
    val timeOfDayStats: List<TimeOfDayStat> = emptyList()
)

data class TimeOfDayStat(
    val periodLabel: String,
    val totalMinutes: Double,
    val sessionCount: Int,
    val percentage: Float
)

private fun buildAnalytics(s: DashboardState): DashboardAnalytics {
    val totalSessions = s.weekly.sumOf { it.sessionCount }.coerceAtLeast(1)
    val totalToday = s.todayMinutes.coerceAtLeast(1.0)

    val topApps = s.appsToday
    val mostUsed = topApps.firstOrNull()

    val totalWeeklyMins = s.weeklyTotalMinutes
    val avgDuration = if (totalSessions > 0) totalWeeklyMins / totalSessions else 0.0

    val morningSessions = s.todaySessionsList.filter { it.timeOfDay in 6..11 }
    val afternoonSessions = s.todaySessionsList.filter { it.timeOfDay in 12..17 }
    val eveningSessions = s.todaySessionsList.filter { it.timeOfDay in 18..22 }
    val nightSessions = s.todaySessionsList.filter { it.timeOfDay in 23..23 || it.timeOfDay in 0..5 }

    val morningMins = morningSessions.sumOf { it.elapsedDuration }
    val afternoonMins = afternoonSessions.sumOf { it.elapsedDuration }
    val eveningMins = eveningSessions.sumOf { it.elapsedDuration }
    val nightMins = nightSessions.sumOf { it.elapsedDuration }

    val todStats = listOf(
        TimeOfDayStat("Morning (6 AM - 12 PM)", morningMins, morningSessions.size, (morningMins / totalToday * 100f).toFloat().coerceIn(0f, 100f)),
        TimeOfDayStat("Afternoon (12 PM - 6 PM)", afternoonMins, afternoonSessions.size, (afternoonMins / totalToday * 100f).toFloat().coerceIn(0f, 100f)),
        TimeOfDayStat("Evening (6 PM - 11 PM)", eveningMins, eveningSessions.size, (eveningMins / totalToday * 100f).toFloat().coerceIn(0f, 100f)),
        TimeOfDayStat("Night / Late (11 PM - 6 AM)", nightMins, nightSessions.size, (nightMins / totalToday * 100f).toFloat().coerceIn(0f, 100f))
    )

    val peakStat = todStats.maxByOrNull { it.totalMinutes }
    val peakLabel = if (peakStat != null && peakStat.totalMinutes > 0) peakStat.periodLabel else "No usage recorded yet"

    return DashboardAnalytics(
        mostUsedApp = mostUsed,
        topApps = topApps,
        weeklyTrends = s.weekly,
        totalWeeklyMinutes = totalWeeklyMins,
        avgSessionDurationMins = avgDuration,
        peakUsageTimeLabel = peakLabel,
        timeOfDayStats = todStats
    )
}
