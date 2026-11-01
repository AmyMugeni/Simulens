package com.simulens.app.ui.intervention

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner // older Compose: androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.dashboard.formatDuration
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.ui.theme.SimulensTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionScreen(
    viewModel: InterventionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToRag: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(InterventionOverlay.canShow(context)) }

    // The user returns from Settings without a result callback, so re-check on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) overlayOk = InterventionOverlay.canShow(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val simulate: () -> Unit = {
        if (overlayOk) {
            Toast.makeText(context, "Switch to any app. The nudge appears in 5s", Toast.LENGTH_SHORT).show()
            viewModel.simulateIntervention(delaySeconds = 5)
        } else {
            showPermissionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Interventions & Nudges", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = simulate) {
                        Icon(Icons.Default.PlayArrow, "Test nudge", tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!overlayOk) {
                item(key = "overlay_banner") {
                    OverlayPermissionBanner(onEnable = {
                        context.startActivity(InterventionOverlay.permissionIntent(context))
                    })
                }
            }

            item(key = "overview_card") {
                OverviewCard(
                    todayCount = state.todayCount,
                    weekCount = state.weekCount,
                    topAppName = state.topAppName,
                    mostCommonReason = ruleLabel(state.mostCommonReason),
                    onSimulate = simulate
                )
            }

            item(key = "filter_row") {
                Column {
                    Text("Filter by rule type", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(InterventionFilter.entries.toTypedArray()) { option ->
                            FilterChip(
                                selected = state.filter == option,
                                onClick = { viewModel.setFilter(option) },
                                label = { Text(option.label) },
                                leadingIcon = if (state.filter == option) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            item(key = "list_header") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Intervention history (${state.filteredLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                    if (state.logs.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear all", color = colors.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (state.filteredLogs.isEmpty()) {
                item(key = "empty_state") {
                    EmptyInterventionState(hasAnyLogs = state.logs.isNotEmpty(), onTestNudge = simulate)
                }
            } else {
                items(items = state.filteredLogs, key = { it.id }) { log ->
                    InterventionLogCard(
                        log = log,
                        appName = viewModel.getAppName(log.packageName),
                        dateFormat = dateFormat,
                        onDelete = { viewModel.deleteLog(log.id) },
                        onAskRag = onNavigateToRag
                    )
                }
            }
        }
    }

    // Fallback in-app dialog (only used when the overlay could not be shown).
    state.activePreviewNudge?.let { nudge ->
        NudgePreviewDialog(
            nudge = nudge,
            onAccept = { viewModel.dismissPreviewNudge() },
            onSnooze = {
                InterventionActionReceiver.snooze(context) // same snooze store as everything else
                viewModel.dismissPreviewNudge()
            },
            onDismiss = { viewModel.dismissPreviewNudge() },
            onLearnMore = {
                viewModel.dismissPreviewNudge()
                onNavigateToRag()
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Allow display over other apps") },
            text = {
                Text("Simulens needs this permission to show a full-screen pause when you have " +
                        "spent too long in an app. You can turn it off anytime in Settings.")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    context.startActivity(InterventionOverlay.permissionIntent(context))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Not now") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear intervention history?") },
            text = { Text("This removes all ${state.logs.size} entries. It can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { showClearConfirm = false; viewModel.clearAllLogs() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ------------------------------------------------------------------------------------------

/** Turns a stored triggerReason into a short readable rule name. */
internal fun ruleLabel(reason: String): String = when {
    reason.startsWith("SNOOZED", true) -> "Snoozed"
    reason.startsWith("DISMISSED", true) -> "Dismissed"
    reason.contains("SCHEDULE", true) -> "In class"
    reason.contains("SLEEP_WINDOW", true) -> "Sleep window"
    reason.contains("WIND_DOWN", true) -> "Wind-down"
    reason.contains("MORNING", true) -> "Morning"
    reason.contains("HIGH_SESSION", true) -> "Long visit"
    reason.contains("HIGH_DAILY", true) -> "Heavy day"
    reason.isBlank() || reason == "No interventions yet" -> "None yet"
    else -> "Nudge"
}

@Composable
private fun OverlayPermissionBanner(onEnable: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Full-screen nudges are off", fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("Nudges will arrive as notifications only.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}

@Composable
private fun OverviewCard(
    todayCount: Int,
    weekCount: Int,
    topAppName: String?,
    mostCommonReason: String,
    onSimulate: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(42.dp).background(
                            Brush.linearGradient(listOf(colors.primary, colors.secondary)), CircleShape)
                    ) {
                        Icon(Icons.Default.Shield, null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Active nudge protection", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text("Circadian & focus rule engine", style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant)
                    }
                }
                PillBadge("ACTIVE", containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPill(Modifier.weight(1f), "Today", todayCount.toString(),
                    colors.primaryContainer, colors.onPrimaryContainer)
                StatPill(Modifier.weight(1f), "7 days", weekCount.toString(),
                    colors.secondaryContainer, colors.onSecondaryContainer)
                StatPill(Modifier.weight(1f), "Top app", topAppName ?: "None",
                    colors.tertiaryContainer, colors.onTertiaryContainer)
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainer
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Most frequent: $mostCommonReason",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSimulate,
                    modifier = Modifier.size(36.dp).background(colors.primaryContainer, CircleShape)
                ) {
                    Icon(Icons.Default.PlayArrow, "Test nudge", tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    modifier: Modifier,
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = containerColor) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun InterventionLogCard(
    log: InterventionLogEntity,
    appName: String,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit,
    onAskRag: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val detail = log.triggerReason.substringAfter(": ", log.triggerReason)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = log.packageName, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text(dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                // Legacy snooze/dismiss rows store 0, so only show a real duration.
                if (log.sessionDurationMins > 0f) {
                    PillBadge(
                        text = formatDuration(log.sessionDurationMins.toDouble()),
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Delete log", tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.errorContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.2f))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = colors.error,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(ruleLabel(log.triggerReason), style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = colors.onSurface,
                            modifier = Modifier.weight(1f))
                        if (log.wasDismissed) {
                            PillBadge("DISMISSED", containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    TextButton(
                        onClick = onAskRag,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                    ) {
                        Text("Ask AI \uD83E\uDD16", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInterventionState(hasAnyLogs: Boolean, onTestNudge: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).background(colors.surfaceContainerHigh, CircleShape)
            ) {
                Icon(Icons.Default.NotificationsActive, null, tint = colors.primary,
                    modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasAnyLogs) "No interventions match this filter" else "No interventions triggered yet",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasAnyLogs) "Try switching filter options above."
                else "Simulens watches your tracked apps and steps in when a boundary is crossed.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onTestNudge, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Test sample nudge")
            }
        }
    }
}

@Composable
private fun NudgePreviewDialog(
    nudge: NudgePreview,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).background(
                    Brush.linearGradient(listOf(colors.primary, colors.secondary)), CircleShape)
            ) {
                Icon(Icons.Default.Shield, null, tint = colors.onPrimary, modifier = Modifier.size(30.dp))
            }
        },
        title = { Text(nudge.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    AppIcon(packageName = nudge.packageName, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(nudge.appName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.3f))
                ) {
                    Text(nudge.message, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onLearnMore, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Learn more (ask AI chatbot)", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = colors.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept, shape = RoundedCornerShape(12.dp)) {
                Text("Accept (take break)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSnooze) { Text("Snooze 15m") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colors.surfaceContainerLow
    )
}