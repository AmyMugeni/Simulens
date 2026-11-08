package com.simulens.app.rag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simulens.app.data.chat.ChatMessage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    topic: InterventionTopic = InterventionTopic.GENERAL
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(topic) { viewModel.setTopic(topic) }

    val last = state.messages.lastOrNull()
    val followUps = if (
        !state.sending && last != null && !last.fromUser && last.mode != Mode.CRISIS
    ) state.remainingPrompts.take(3) else emptyList()

    val itemCount = if (state.messages.isEmpty()) 2
    else state.messages.size + (if (state.sending) 1 else 0) + (if (followUps.isNotEmpty()) 1 else 0)

    LaunchedEffect(itemCount) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Chat History", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all saved chat messages? Messages older than 6 days are cleaned up automatically.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colors.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Simulens AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground
                            )
                            Text(
                                "Your Friendly Focus Sidekick ✨",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                    }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = colors.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            Surface(
                color = colors.surfaceContainerLow,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    state.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.error,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.take(1000); if (state.error != null) viewModel.dismissError() },
                            placeholder = { Text("Ask about social media & your habits...") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceContainer,
                                unfocusedContainerColor = colors.surfaceContainer,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outlineVariant
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        FilledIconButton(
                            onClick = { viewModel.send(input); input = "" },
                            enabled = input.isNotBlank() && !state.sending,
                            modifier = Modifier.size(50.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PersonalizationCard(
                    isPersonalized = state.isPersonalized,
                    onToggle = { viewModel.togglePersonalization() },
                    summary = state.personalizedSummary
                )
            }

            if (state.messages.isEmpty()) {
                item { IntroCard(state.topic) }
                item {
                    PromptList(
                        title = "Suggested Questions to Ask",
                        prompts = state.remainingPrompts,
                        onPick = viewModel::send,
                        enabled = !state.sending
                    )
                }
            }

            items(state.messages, key = { it.id }) { m ->
                MessageBubble(
                    m = m,
                    onDelete = { viewModel.deleteMessage(m.id) }
                )
            }

            if (state.sending) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = colors.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Analyzing scientific sources & generating response...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
                }
            }

            if (followUps.isNotEmpty()) {
                item {
                    PromptList(
                        title = "You Could Also Ask",
                        prompts = followUps,
                        onPick = viewModel::send,
                        enabled = true
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroCard(topic: InterventionTopic) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (topic != InterventionTopic.GENERAL) "Focus Notice Topic" else "Welcome to Simulens AI ✨",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(topic.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Replies draw from peer-reviewed scientific studies on digital wellbeing, with direct citations. " +
                        "This provides educational insights, not medical or clinical diagnosis.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PromptList(
    title: String,
    prompts: List<String>,
    onPick: (String) -> Unit,
    enabled: Boolean
) {
    if (prompts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        prompts.forEach { PromptCard(text = it, enabled = enabled, onClick = { onPick(it) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptCard(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(m: ChatMessage, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val crisis = m.mode == Mode.CRISIS

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start
    ) {
        val displayText = m.text.ifBlank {
            if (m.fromUser) "..." else "I'm here to help. Could you rephrase your question?"
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (m.fromUser) 20.dp else 4.dp,
                bottomEnd = if (m.fromUser) 4.dp else 20.dp
            ),
            color = when {
                m.fromUser -> colors.primary
                crisis -> colors.errorContainer
                else -> colors.surfaceContainerLow
            },
            border = when {
                m.fromUser -> null
                crisis -> BorderStroke(1.dp, colors.error)
                else -> BorderStroke(1.dp, colors.outlineVariant)
            },
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Box(
                modifier = if (m.fromUser) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(colors.primary, colors.secondary)
                        )
                    )
                } else Modifier
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        m.fromUser -> colors.onPrimary
                        crisis -> colors.onErrorContainer
                        else -> colors.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        if (m.sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                m.sources.forEach { s ->
                    AssistChip(
                        onClick = { if (s.url.startsWith("http")) uriHandler.openUri(s.url) },
                        label = {
                            Text(
                                "[${s.n}] ${s.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 210.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colors.primaryContainer,
                            labelColor = colors.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizationCard(
    isPersonalized: Boolean,
    onToggle: () -> Unit,
    summary: String
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPersonalized) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, if (isPersonalized) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPersonalized) Icons.Default.Psychology else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isPersonalized) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isPersonalized) "Personalization Active" else "Personalization Off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isPersonalized) {
                            if (summary.isNotBlank()) summary else "Uses BSMAS score & top focus struggles to tailor advice"
                        } else "Standard research guidance mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isPersonalized,
                onCheckedChange = { onToggle() },
                thumbContent = {
                    if (isPersonalized) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            )
        }
    }
}
