package com.simulens.app.rag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.simulens.app.data.chat.ChatMessage
import com.simulens.app.ui.components.InkAvatar
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.highlighter
import com.simulens.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit = {},
    topic: InterventionTopic = InterventionTopic.GENERAL,
    showBackButton: Boolean = true,
    username: String = "S"
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(topic) { viewModel.setTopic(topic) }

    val last = state.messages.lastOrNull()
    val followUps = if (
        !state.sending && last != null && !last.fromUser && last.mode != Mode.CRISIS
    ) state.remainingPrompts.take(4) else emptyList()

    val itemCount = if (state.messages.isEmpty()) 2
    else state.messages.size + (if (state.sending) 1 else 0) + (if (followUps.isNotEmpty()) 1 else 0)

    LaunchedEffect(itemCount) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = InkSurface,
            title = { Text("Clear Chat History", fontWeight = FontWeight.Bold, color = InkTextPrimary) },
            text = { Text("Are you sure you want to delete all saved chat messages?", color = InkTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    }
                ) {
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
                                "Your friendly focus sidekick",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Simulens AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = InkTextPrimary,
                                modifier = Modifier.highlighter()
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            SquigglyDoodle(color = IceBlue, modifier = Modifier.padding(end = 12.dp))
                            InkAvatar(initial = username.ifBlank { "S" }, size = 34.dp)
                            if (state.messages.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { showClearConfirm = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Clear History",
                                        tint = InkTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkTextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkBackground)
            )
        },
        bottomBar = {
            Surface(
                color = InkSurfaceLow,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, InkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    state.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = RiskHigh,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Amber search / sparkle icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(InkAmber)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = InkOnAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it.take(1000)
                                if (state.error != null) viewModel.dismissError()
                            },
                            placeholder = {
                                Text(
                                    "Ask about social media & your habits",
                                    color = InkTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = InkSurfaceHigh,
                                unfocusedContainerColor = InkSurfaceHigh,
                                focusedBorderColor = IceBlue,
                                unfocusedBorderColor = InkBorder,
                                focusedTextColor = InkTextPrimary,
                                unfocusedTextColor = InkTextPrimary
                            )
                        )

                        Spacer(Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = {
                                viewModel.send(input)
                                input = ""
                            },
                            enabled = input.isNotBlank() && !state.sending,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = InkAmber,
                                contentColor = InkOnAmber,
                                disabledContainerColor = InkSurfaceHigh,
                                disabledContentColor = InkTextMuted
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PersonalizationBanner(
                    isPersonalized = state.isPersonalized,
                    onToggle = { viewModel.togglePersonalization() },
                    summary = state.personalizedSummary
                )
            }

            if (state.messages.isEmpty()) {
                item { IntroCard(state.topic) }
                item {
                    PromptList(
                        title = "Suggested Questions",
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
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = IceBlue
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Analyzing scientific sources & generating response...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = IceBlue
                        )
                    }
                }
            }

            if (followUps.isNotEmpty()) {
                item {
                    PromptList(
                        title = "Quick Follow-ups",
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
private fun PersonalizationBanner(
    isPersonalized: Boolean,
    onToggle: () -> Unit,
    summary: String
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = InkSurface,
        border = BorderStroke(1.dp, InkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isPersonalized) IceBlue else InkTextMuted, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isPersonalized) {
                    if (summary.isNotBlank()) summary else "Tuned to your check-in and your Gentle tone setting."
                } else "Standard focus assistance mode.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Why?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = IceBlue
            )
        }
    }
}

@Composable
private fun IntroCard(topic: InterventionTopic) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = InkSurface),
            border = BorderStroke(1.dp, InkBorder)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = InkAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (topic != InterventionTopic.GENERAL) "Focus Notice Topic" else "Simulens Sidekick ✨",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    topic.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Replies draw from peer-reviewed scientific studies on digital wellbeing, with direct citations to help you build healthy digital habits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTextSecondary
                )
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = InkTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(prompts) { prompt ->
                Surface(
                    onClick = { onPick(prompt) },
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    color = InkSurfaceHigh,
                    border = BorderStroke(1.dp, InkBorder)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = InkTextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(m: ChatMessage, onDelete: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val crisis = m.mode == Mode.CRISIS

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start
    ) {
        val rawText = m.text.ifBlank {
            if (m.fromUser) "..." else "I'm here to help. Could you rephrase your question?"
        }

        val formattedText = remember(rawText, m.fromUser) {
            if (m.fromUser) AnnotatedString(rawText)
            else parseMarkdownToAnnotatedString(rawText)
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (m.fromUser) 20.dp else 4.dp,
                bottomEnd = if (m.fromUser) 4.dp else 20.dp
            ),
            color = when {
                m.fromUser -> IceBlue
                crisis -> RiskHighBg
                else -> InkSurface
            },
            border = when {
                m.fromUser -> null
                crisis -> BorderStroke(1.dp, RiskHigh)
                else -> BorderStroke(1.dp, InkBorder)
            },
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = formattedText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    m.fromUser -> Color.White
                    crisis -> RiskHigh
                    else -> InkTextPrimary
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
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
                            containerColor = InkSurfaceHigh,
                            labelColor = IceBlueLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownToAnnotatedString(
    markdown: String,
    headingColor: Color = IceBlue
): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.split("\n")
        lines.forEachIndexed { index, line ->
            var trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (index < lines.size - 1) append("\n")
                return@forEachIndexed
            }

            // Headings check (#, ##, ###)
            val isHeading = trimmed.startsWith("#")
            if (isHeading) {
                trimmed = trimmed.replace(Regex("^#+\\s*"), "")
            }

            // Bullet points check (*, -, +)
            val isBullet = trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ")
            if (isBullet) {
                trimmed = "•  " + trimmed.substring(2).trim()
            }

            // Pattern for **bold** or *italic*
            val formatRegex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|__(.*?)__|_(.*?)_")

            var lastMatchEnd = 0
            val matches = formatRegex.findAll(trimmed).toList()

            if (isHeading) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = headingColor, fontSize = 15.sp))
            }

            if (matches.isEmpty()) {
                append(trimmed.replace("*", ""))
            } else {
                for (match in matches) {
                    val prevText = trimmed.substring(lastMatchEnd, match.range.first).replace("*", "")
                    append(prevText)

                    val groupValues = match.groupValues
                    val isBold = match.value.startsWith("**") || match.value.startsWith("__")
                    val innerText = when {
                        groupValues[1].isNotEmpty() -> groupValues[1]
                        groupValues[2].isNotEmpty() -> groupValues[2]
                        groupValues[3].isNotEmpty() -> groupValues[3]
                        groupValues[4].isNotEmpty() -> groupValues[4]
                        else -> match.value.replace("*", "").replace("_", "")
                    }

                    if (isBold) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    } else {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    }
                    append(innerText)
                    pop()

                    lastMatchEnd = match.range.last + 1
                }
                if (lastMatchEnd < trimmed.length) {
                    append(trimmed.substring(lastMatchEnd).replace("*", ""))
                }
            }

            if (isHeading) {
                pop()
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}
