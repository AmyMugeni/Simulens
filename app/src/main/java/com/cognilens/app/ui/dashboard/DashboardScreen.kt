package com.cognilens.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognilens.app.ui.components.PillBadge
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import com.cognilens.app.data.local.entity.BehavioralSession

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

    val screenTime = state.todayMinutes
    val sessionCount = state.todaySessions
    val analytics = remember(state) { buildAnalytics(state) }

    val colors = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.surfaceContainerLow,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onNavigateToRag()
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "RAG Assistant") },
                    label = { Text("Assistant", fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onNavigateToInsights()
                    },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Insights") },
                    label = { Text("Insights", fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        onNavigateToMode()
                    },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Mode") },
                    label = { Text("Mode", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = viewModel.getGreeting(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userProfile.username.ifBlank { "Student" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                Surface(
                    onClick = onNavigateToProfile,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    border = BorderStroke(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(colors.primary, colors.secondary)
                        )
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stat Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Screen Time",
                    value = String.format(Locale.US, "%.0f min", screenTime),
                    icon = Icons.Default.Timer,
                    badgeText = "Today",
                    iconContainerColor = colors.primaryContainer,
                    iconTint = colors.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Sessions",
                    value = sessionCount.toString(),
                    icon = Icons.Default.Repeat,
                    badgeText = "Loops",
                    iconContainerColor = colors.secondaryContainer,
                    iconTint = colors.secondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Most Used App Spotlight
            MostUsedAppCard(
                mostUsedApp = analytics.mostUsedApp,
                topApps = analytics.topApps,
                onConfigureApps = onNavigateToAppSelection
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Weekly Usage Trends Bar Chart
            WeeklyUsageGraphCard(
                trends = analytics.weeklyTrends,
                totalWeeklyMins = analytics.totalWeeklyMinutes,
                avgDuration = analytics.avgSessionDurationMins
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Time of Day Usage Breakdown
            TimeOfDayInsightsCard(
                peakLabel = analytics.peakUsageTimeLabel,
                stats = analytics.timeOfDayStats
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Current Academic Schedule Section
            Text(
                text = "Academic Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.isOngoing) colors.primaryContainer else colors.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (status.isOngoing) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (status.isOngoing) Icons.Default.School else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (status.isOngoing) colors.onPrimaryContainer else colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (status.isOngoing) "Active Session" else "Timetable Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (status.isOngoing) colors.onPrimaryContainer else colors.onSurface
                            )
                        }

                        PillBadge(
                            text = if (status.isOngoing) "ONGOING" else "IDLE",
                            containerColor = if (status.isOngoing) colors.primary else colors.surfaceContainer,
                            contentColor = if (status.isOngoing) colors.onPrimary else colors.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (status.isOngoing) {
                            status.currentActivity ?: "Academic Lecture"
                        } else {
                            "No active class scheduled at this hour"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (status.isOngoing) colors.onPrimaryContainer else colors.onSurface
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    HorizontalDivider(
                        color = if (status.isOngoing) colors.onPrimaryContainer.copy(alpha = 0.2f) else colors.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (status.nextActivity == "Sleep") Icons.Default.Bedtime else Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (status.isOngoing) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Next: ${status.nextActivity} at ${status.nextActivityTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (status.isOngoing) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    badgeText: String,
    iconContainerColor: Color,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconContainerColor, CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                PillBadge(
                    text = badgeText,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            .background(colors.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Most Used App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                Surface(
                    onClick = onConfigureApps,
                    shape = CircleShape,
                    color = colors.secondaryContainer
                ) {
                    Text(
                        text = "Manage Apps",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (mostUsedApp != null && mostUsedApp.totalMinutes > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = mostUsedApp.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f mins total usage", mostUsedApp.totalMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.primaryContainer
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.0f%%", mostUsedApp.percentage),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown list of top apps
                topApps.take(4).forEach { app ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f min (%.0f%%)", app.totalMinutes, app.percentage),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { app.percentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = colors.primary,
                            trackColor = colors.surfaceContainerHigh
                        )
                    }
                }
            } else {
                Text(
                    text = "No monitored app usage recorded yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WeeklyUsageGraphCard(
    trends: List<DailyUsageTrend>,
    totalWeeklyMins: Double,
    avgDuration: Double
) {
    val colors = MaterialTheme.colorScheme
    val maxMins = trends.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(30.0) ?: 60.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            .background(colors.secondaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Weekly Trends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "Total: %.0f min • Avg: %.1f min/session", totalWeeklyMins, avgDuration),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                PillBadge(
                    text = "7 Days",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bar Chart Canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
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
                        // Duration label above bar
                        Text(
                            text = if (trend.totalMinutes > 0) String.format(Locale.US, "%.0fm", trend.totalMinutes) else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (trend.isToday) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Animated / proportional Bar
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(heightRatio)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (trend.isToday) {
                                        Brush.verticalGradient(
                                            colors = listOf(colors.primary, colors.secondary)
                                        )
                                    } else if (trend.totalMinutes > 0) {
                                        SolidColor(colors.secondary.copy(alpha = 0.6f))
                                    } else {
                                        SolidColor(colors.surfaceContainerHigh)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Day label below bar
                        Text(
                            text = trend.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (trend.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (trend.isToday) colors.primary else colors.onSurfaceVariant
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
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            .background(colors.tertiaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Time of Day Pattern",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                PillBadge(
                    text = "Circadian",
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.tertiaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.3f))
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
                        tint = colors.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Peak Usage Window: $peakLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
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
                            color = colors.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f min (%d sessions)", stat.totalMinutes, stat.sessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { stat.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = colors.tertiary,
                        trackColor = colors.surfaceContainerHigh
                    )
                }
            }
        }
    }
}


// -----------------------------------------------------------------------------------------
// Analytics helpers (top-level, file-private)
// -----------------------------------------------------------------------------------------

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
    val zone = ZoneId.systemDefault()
    val totalSessions = s.weekly.sumOf { it.sessionCount }.coerceAtLeast(1)
    val totalToday = s.todayMinutes.coerceAtLeast(1.0)

    // Split today's sessions into 4 day-parts by the hour they ended
    val parts = listOf("Morning", "Afternoon", "Evening", "Night")
    val buckets = parts.map { mutableListOf<BehavioralSession>() }

    for (session in s.todaySessionsList) {
        val hour = Instant.ofEpochMilli(session.timestamp).atZone(zone).hour
        val idx = when (hour) {
            in 5..11 -> 0
            in 12..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
        buckets[idx].add(session)
    }

    val timeOfDayStats = parts.mapIndexed { i, label ->
        val mins = buckets[i].sumOf { it.elapsedDuration }
        TimeOfDayStat(
            periodLabel = label,
            totalMinutes = mins,
            sessionCount = buckets[i].size,
            percentage = (mins / totalToday * 100.0).toFloat()
        )
    }

    val peak = timeOfDayStats.maxByOrNull { it.totalMinutes }

    return DashboardAnalytics(
        mostUsedApp = s.appsToday.firstOrNull(),
        topApps = s.appsToday,
        weeklyTrends = s.weekly,
        totalWeeklyMinutes = s.weeklyTotalMinutes,
        avgSessionDurationMins = s.weeklyTotalMinutes / totalSessions,
        peakUsageTimeLabel = peak
            ?.takeIf { it.totalMinutes > 0 }
            ?.let { stat ->
                val mins = "%.0f".format(stat.totalMinutes)
                "${stat.periodLabel} (~$mins min)"
            }
            ?: "Not enough data yet",
        timeOfDayStats = timeOfDayStats
    )
}