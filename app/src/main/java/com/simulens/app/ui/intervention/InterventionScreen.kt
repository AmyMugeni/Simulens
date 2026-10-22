package com.simulens.app.ui.intervention

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.PillBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterventionScreen(
    viewModel: InterventionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Interventions & Nudges",
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
                    IconButton(onClick = { viewModel.simulateIntervention() }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Nudge",
                            tint = colors.primary
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = colors.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Simulate Nudge") },
                                leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.simulateIntervention()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear History") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearAllLogs()
                                }
                            )
                        }
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
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overview Banner Card
                item(key = "overview_card") {
                    OverviewCard(
                        todayCount = state.todayCount,
                        weekCount = state.weekCount,
                        topAppName = state.topAppName,
                        mostCommonReason = state.mostCommonReason,
                        onSimulate = { viewModel.simulateIntervention() }
                    )
                }

                // Filter Row Section
                item(key = "filter_row") {
                    Column {
                        Text(
                            text = "Filter by Rule Type",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(InterventionFilter.entries.toTypedArray()) { filterOption ->
                                FilterChip(
                                    selected = state.filter == filterOption,
                                    onClick = { viewModel.setFilter(filterOption) },
                                    label = { Text(filterOption.label) },
                                    leadingIcon = if (state.filter == filterOption) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Section Title
                item(key = "list_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intervention History (${state.filteredLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        if (state.logs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearAllLogs() }) {
                                Text("Clear All", color = colors.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Empty State or Log Items
                if (state.filteredLogs.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyInterventionState(
                            hasAnyLogs = state.logs.isNotEmpty(),
                            onTestNudge = { viewModel.simulateIntervention() }
                        )
                    }
                } else {
                    items(
                        items = state.filteredLogs,
                        key = { it.id }
                    ) { log ->
                        InterventionLogCard(
                            log = log,
                            appName = viewModel.getAppName(log.packageName),
                            dateFormat = dateFormat,
                            onDelete = { viewModel.deleteLog(log.id) }
                        )
                    }
                }
            }
        }
    }

    // Active Preview Nudge Dialog Overlay
    val nudge = state.activePreviewNudge
    if (nudge != null) {
        NudgePreviewDialog(
            nudge = nudge,
            onDismiss = { viewModel.dismissPreviewNudge() },
            onLearnMore = {
                viewModel.dismissPreviewNudge()
            }
        )
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
                            .size(42.dp)
                            .background(
                                brush = Brush.linearGradient(colors = listOf(colors.primary, colors.secondary)),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Active Nudge Protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Circadian & Focus Rule Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                PillBadge(
                    text = "ACTIVE",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stat Counter Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPill(
                    modifier = Modifier.weight(1f),
                    label = "Today",
                    value = todayCount.toString(),
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    label = "7 Days",
                    value = weekCount.toString(),
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    label = "Top App",
                    value = topAppName ?: "None",
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Most frequent: $mostCommonReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSimulate,
                    modifier = Modifier
                        .size(36.dp)
                        .background(colors.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test Nudge",
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun InterventionLogCard(
    log: InterventionLogEntity,
    appName: String,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = log.packageName, size = 44.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                PillBadge(
                    text = String.format(Locale.US, "%.0f min", log.sessionDurationMins),
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Log",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.errorContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.triggerReason,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInterventionState(
    hasAnyLogs: Boolean,
    onTestNudge: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(colors.surfaceContainerHigh, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasAnyLogs) "No interventions match this filter" else "No interventions triggered yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyLogs) "Try switching filter options above." else "Simulens actively monitors your tracked apps and will intervene when circadian boundaries are crossed.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onTestNudge,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Sample Nudge")
            }
        }
    }
}

@Composable
private fun NudgePreviewDialog(
    nudge: NudgePreview,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(colors = listOf(colors.primary, colors.secondary)),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Text(
                text = nudge.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    AppIcon(packageName = nudge.packageName, size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = nudge.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = nudge.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Learn More Link (prepared for RAG Chatbot)
                TextButton(
                    onClick = onLearnMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Learn More (Ask AI Chatbot)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Accept (Take Break)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Snooze 15m")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colors.surfaceContainerLow
    )
}