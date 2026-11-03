package com.simulens.app.rag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Pass the topic of the intervention that opened the chat. Defaults to general suggestions.
    topic: InterventionTopic = InterventionTopic.GENERAL
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(topic) { viewModel.setTopic(topic) }

    // After a normal reply, offer up to three suggestions that have not been asked yet.
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
                title = { Text("Ask CogniLens", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Column(
                modifier = Modifier
                    .background(colors.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        placeholder = { Text("Ask about social media and your wellbeing") },
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceContainerLow,
                            unfocusedContainerColor = colors.surfaceContainerLow,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { viewModel.send(input); input = "" },
                        enabled = input.isNotBlank() && !state.sending,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty()) {
                item { IntroCard(state.topic) }
                item {
                    PromptList(
                        title = "Suggested questions",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Thinking... the first reply can take up to a minute.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            if (followUps.isNotEmpty()) {
                item {
                    PromptList(
                        title = "You could also ask",
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            if (topic != InterventionTopic.GENERAL) {
                Text(
                    "About this notice",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(topic.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Answers come from research on social media and wellbeing, with links to source studies. " +
                        "This is general information, not medical advice or a formal diagnosis.",
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
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
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
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
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (m.fromUser) 20.dp else 6.dp,
                bottomEnd = if (m.fromUser) 6.dp else 20.dp
            ),
            color = when {
                m.fromUser -> colors.primary
                crisis -> colors.errorContainer
                else -> colors.surfaceContainerLow
            },
            border = if (m.fromUser) null else BorderStroke(1.dp, if (crisis) colors.error else colors.outlineVariant),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = m.text,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    m.fromUser -> colors.onPrimary
                    crisis -> colors.onErrorContainer
                    else -> colors.onSurface
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        if (m.sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                m.sources.forEach { s ->
                    AssistChip(
                        onClick = { if (s.url.startsWith("http")) uriHandler.openUri(s.url) },
                        label = {
                            Text(
                                "[${s.n}] ${s.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp)
                            )
                        },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
    }
}
