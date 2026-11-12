package com.simulens.app.ui.intervention

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.data.local.entity.InterventionLogEntity
import androidx.compose.ui.draw.rotate
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.InkAvatar
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.PencilDoodle
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.highlighter
import com.simulens.app.ui.dashboard.formatDuration
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionScreen(
    viewModel: InterventionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToRag: () -> Unit = {},
    showBackButton: Boolean = true,
    username: String = "S"
) {
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(InterventionOverlay.canShow(context)) }

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
        containerColor = InkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Your gentle reminders",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Nudges",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary,
                                    modifier = Modifier.highlighter()
                                )
                                Spacer(Modifier.width(6.dp))
                                PencilDoodle(modifier = Modifier.rotate(15f))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            SquigglyDoodle(color = IceBlue, modifier = Modifier.padding(end = 12.dp))
                            InkAvatar(initial = username.ifBlank { "S" }, size = 34.dp)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = simulate) {
                                Icon(Icons.Default.PlayArrow, "Test nudge", tint = InkAmber)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = InkTextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InkBackground,
                    titleContentColor = InkTextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(InkBackground),
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

            item(key = "profile_rule_card") {
                ProfileToneRuleCard()
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
                    Text(
                        "Intervention history",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(InterventionFilter.entries.toTypedArray()) { option ->
                            FilterChip(
                                selected = state.filter == option,
                                onClick = { viewModel.setFilter(option) },
                                label = { Text(option.label) },
                                leadingIcon = if (state.filter == option) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IceBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = InkSurface,
                                    labelColor = InkTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = InkBorder,
                                    selectedBorderColor = IceBlue,
                                    enabled = true,
                                    selected = state.filter == option
                                )
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
                    Text(
                        "Showing ${state.filteredLogs.size} of ${state.logs.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkTextSecondary
                    )
                    if (state.logs.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear all", color = RiskHigh, style = MaterialTheme.typography.labelMedium)
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

    // Fallback in-app dialog
    state.activePreviewNudge?.let { nudge ->
        NudgePreviewDialog(
            nudge = nudge,
            onAccept = { viewModel.dismissPreviewNudge() },
            onSnooze = {
                InterventionActionReceiver.snooze(context)
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
            containerColor = InkSurface,
            title = { Text("Allow display over other apps", color = InkTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Simulens shows mindful intervention dialogs over monitored apps when focus limits are reached.",
                    color = InkTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        context.startActivity(InterventionOverlay.permissionIntent(context))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = InkOnAmber)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Later", color = InkTextSecondary)
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = InkSurface,
            title = { Text("Clear all intervention logs?", color = InkTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove your recorded focus history.", color = InkTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllLogs()
                    showClearConfirm = false
                }) {
                    Text("Clear All", color = RiskHigh, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = InkTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ProfileToneRuleCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "FROM YOUR PROFILE",
                style = MaterialTheme.typography.labelSmall,
                color = InkTextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gentle tone · Monitored apps · Phone-free lectures",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = InkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Active",
                    color = RiskLow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun ruleLabel(reason: String): String = when {
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiskHighBg,
        border = BorderStroke(1.dp, RiskHigh.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Full-screen nudges are off", fontWeight = FontWeight.Bold, color = InkTextPrimary)
                Text("Nudges will arrive as notifications only.",
                    style = MaterialTheme.typography.bodySmall, color = InkTextSecondary)
            }
            TextButton(onClick = onEnable) { Text("Enable", color = IceBlue) }
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
    var protectionOn by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nudge protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (protectionOn) "On. Watching monitored apps during class & sleep hours."
                        else "Paused. Nudges currently muted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextSecondary
                    )
                }

                Switch(
                    checked = protectionOn,
                    onCheckedChange = { protectionOn = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = InkOnAmber,
                        checkedTrackColor = InkAmber,
                        uncheckedTrackColor = InkSurfaceHigh
                    )
                )
            }

            Spacer(Modifier.height(18.dp))

            // 3-Column Stats Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(Modifier.weight(1f), "TODAY", "$todayCount nudges", IceBlueContainer, IceBlue)
                StatPill(Modifier.weight(1f), "7 DAYS", "$weekCount nudges", InkSurfaceHigh, InkAmber)
                StatPill(Modifier.weight(1f), "TOP APP", topAppName ?: "None", InkSurfaceHigh, InkTextPrimary)
            }
        }
    }
}

@Composable
private fun StatPill(
    modifier: Modifier,
    label: String,
    value: String,
    bg: Color,
    fg: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = InkTextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = log.packageName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )
                    Text(
                        "${ruleLabel(log.triggerReason)} · ${dateFormat.format(Date(log.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTextSecondary
                    )
                }

                Surface(
                    onClick = onAskRag,
                    shape = RoundedCornerShape(12.dp),
                    color = InkSurfaceHigh,
                    border = BorderStroke(1.dp, IceBlue.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Ask AI",
                        color = IceBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        "Delete log",
                        tint = InkTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInterventionState(hasAnyLogs: Boolean, onTestNudge: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(InkSurfaceHigh, CircleShape)
                    .border(1.dp, InkBorder, CircleShape)
            ) {
                Icon(Icons.Default.Shield, null, tint = IceBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasAnyLogs) "No interventions match this filter" else "No interventions triggered yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkTextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasAnyLogs) "Try switching filter options above."
                else "Simulens watches your tracked apps and steps in when a boundary is crossed.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onTestNudge,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = InkOnAmber)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Test sample nudge", fontWeight = FontWeight.Bold)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkSurface,
        title = {
            Text(nudge.title, fontWeight = FontWeight.Bold, color = InkTextPrimary)
        },
        text = {
            Column {
                Text(nudge.message, color = InkTextSecondary)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onLearnMore) {
                    Text("Chat with Sidekick \uD83E\uDD16", color = IceBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = InkOnAmber)
            ) {
                Text("Take Break", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onSnooze) {
                Text("Snooze 15m", color = InkTextSecondary)
            }
        }
    )
}
