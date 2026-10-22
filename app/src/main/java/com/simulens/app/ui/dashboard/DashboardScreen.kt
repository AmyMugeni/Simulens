package com.simulens.app.ui.dashboard

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.ui.components.PillBadge
import java.util.Locale

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
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
                    label = { Text("Stats", fontWeight = FontWeight.SemiBold) }
                )
                // Insights and Assistant open their own screens, so they don't change
                // selectedTab. Otherwise coming back would land on an empty tab.
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToInsights,
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Insights") },
                    label = { Text("Insights", fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToRag,
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Assistant") },
                    label = { Text("Assistant", fontWeight = FontWeight.SemiBold) }
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
            if (selectedTab == 0) {
                // ==================== DASHBOARD VIEW ====================

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

                Spacer(modifier = Modifier.height(20.dp))

                // Wellness score, today vs baseline, screen time, session frequency, day strip.
                // Today-only: the range chips live on the Stats tab.
                WellnessDashboardSection(state = state)

                Spacer(modifier = Modifier.height(28.dp))

                // Most Used App Spotlight
                MostUsedAppCard(
                    mostUsedApp = analytics.mostUsedApp,
                    topApps = analytics.topApps,
                    onConfigureApps = onNavigateToAppSelection
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
                                    imageVector = if (status.isOngoing) Icons.Default.School else Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (status.isOngoing) colors.onPrimaryContainer else colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (status.isOngoing) "Active Class" else "Academic Schedule",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (status.isOngoing) colors.onPrimaryContainer else colors.onSurface
                                )
                            }

                            PillBadge(
                                text = if (status.isOngoing) "ONGOING"
                                else if (status.todayClassesCount > 0) "${status.todayClassesCount} CLASSES TODAY"
                                else "FREE DAY",
                                containerColor = if (status.isOngoing) colors.primary else colors.surfaceContainer,
                                contentColor = if (status.isOngoing) colors.onPrimary else colors.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val ongoingClass = status.todayClasses.find { it.isOngoing }
                        if (ongoingClass != null) {
                            Text(
                                text = ongoingClass.summary,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer
                            )
                            val locText = if (ongoingClass.location.isNotBlank()) " • ${ongoingClass.location}" else ""
                            Text(
                                text = "${ongoingClass.startTimeStr} - ${ongoingClass.endTimeStr}$locText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { ongoingClass.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = colors.primary,
                                trackColor = colors.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${ongoingClass.remainingMinutes} mins remaining",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = status.displayScheduleText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }

                        if (status.todayClasses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(
                                color = if (status.isOngoing) colors.onPrimaryContainer.copy(alpha = 0.2f) else colors.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Today's Agenda:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (status.isOngoing) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            status.todayClasses.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (item.isOngoing) colors.primary.copy(alpha = 0.15f)
                                            else if (item.isCompleted) colors.surfaceContainer
                                            else colors.surfaceContainerHigh,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isCompleted) Icons.Default.CheckCircle else if (item.isOngoing) Icons.Default.PlayCircle else Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = if (item.isOngoing) colors.primary else if (item.isCompleted) colors.outline else colors.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = item.summary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (item.isOngoing) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (item.isCompleted) colors.onSurface.copy(alpha = 0.6f) else colors.onSurface
                                            )
                                            val locText = if (item.location.isNotBlank()) " • ${item.location}" else ""
                                            Text(
                                                text = "${item.startTimeStr} - ${item.endTimeStr}$locText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }

                                    PillBadge(
                                        text = if (item.isOngoing) "ONGOING" else if (item.isCompleted) "DONE" else "UPCOMING",
                                        containerColor = if (item.isOngoing) colors.primary else if (item.isCompleted) colors.surfaceContainerHighest else colors.secondaryContainer,
                                        contentColor = if (item.isOngoing) colors.onPrimary else if (item.isCompleted) colors.onSurfaceVariant else colors.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
            } else if (selectedTab == 1) {
                // ==================== STATS VIEW ====================

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stats & Trends",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Weekly usage trends & circadian patterns",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        color = colors.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Time-Range Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.selectedTimeRange == range,
                            onClick = { viewModel.setTimeRange(range) },
                            label = { Text(range.label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Weekly Usage Trends Bar Chart
                WeeklyUsageGraphCard(
                    trends = analytics.weeklyTrends,
                    totalWeeklyMins = analytics.totalWeeklyMinutes,
                    avgDuration = analytics.avgSessionDurationMins
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Time of Day Usage Breakdown
                TimeOfDayInsightsCard(
                    peakLabel = analytics.peakUsageTimeLabel,
                    stats = analytics.timeOfDayStats
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tracked Applications Shortcut Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
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
                                text = "Tracked Applications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.monitoredCount} apps currently monitored",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onNavigateToAppSelection,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Manage Apps", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CSV Export Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
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
                                text = "CSV Data Export",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Export behavioral sessions & tracking data for ML forecasting",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        val isExporting by viewModel.isExportingCsv.collectAsState()
                        val context = LocalContext.current

                        Button(
                            onClick = {
                                viewModel.exportCsv(context) { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Exporting...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Data to CSV", fontWeight = FontWeight.Bold)
                            }
                        }
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
                        Text(
                            text = if (trend.totalMinutes > 0) String.format(Locale.US, "%.0fm", trend.totalMinutes) else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (trend.isToday) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

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