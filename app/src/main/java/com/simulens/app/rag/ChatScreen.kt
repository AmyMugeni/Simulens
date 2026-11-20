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

    // GENERAL is the default; don't let it overwrite the topic a nudge just set.
    LaunchedEffect(topic) { if (topic != InterventionTopic.GENERAL) viewModel.setTopic(topic) }
    var showConsent by remember { mutableStateOf(false) }

    val last = state.messages.lastOrNull()
    val chipLabels = if (!state.sending) state.seedChips.map { it.label } else emptyList()
    val followUps = if (
        chipLabels.isEmpty() && !state.sending && last != null && !last.fromUser && last.mode != Mode.CRISIS
    ) state.remainingPrompts.take(4) else emptyList()

    val itemCount = if (state.messages.isEmpty()) 2
    else state.messages.size + (if (state.sending) 1 else 0) +
            (if (followUps.isNotEmpty() || chipLabels.isNotEmpty()) 1 else 0)

    LaunchedEffect(itemCount) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            containerColor = InkSurface,
            title = { Text("Send your data to a remote server?", fontWeight = FontWeight.Bold, color = InkTextPrimary) },
            text = {
                Text(
                    "To tailor answers, Simulens will send a short summary of your own data to a remote server " +
                            "and an AI model each time you send a message: your BSMAS score and risk level, rough " +
                            "daily usage, busiest hours, how often you opened social media in class, near bedtime " +
                            "or after waking, which nudges you got recently, and your wake and bed times. " +
                            "App names and raw usage logs are not sent.\n\n" +
                            "This leaves your device, so it is no longer only on-device. You can turn it off at " +
                            "any time. Turn on personalization?",
                    color = InkTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPersonalization(true)
                    showConsent = false
                }) { Text("Yes, send my data", color = IceBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) {
                    Text("No, keep it private", color = InkTextSecondary)
                }
            }
        )
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
                                "Your friendly focus buddy",
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RiskHighBg,
                            border = BorderStroke(1.dp, RiskHigh.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RiskHigh,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { viewModel.dismissError() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Dismiss", color = RiskHigh, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                    onToggle = {
                        if (state.isPersonalized) viewModel.setPersonalization(false) else showConsent = true
                    },
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
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = InkSurface,
                        border = BorderStroke(1.dp, InkBorder),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = IceBlue
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Analyzing scientific literature & drafting response...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = InkTextSecondary
                            )
                        }
                    }
                }
            }

            if (chipLabels.isNotEmpty()) {
                item {
                    PromptList(
                        title = "Tap to reply",
                        prompts = chipLabels,
                        onPick = viewModel::pickChip,
                        enabled = true
                    )
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
                    if (summary.isNotBlank()) "Personalized: $summary" else "Personalized. A summary of your data is sent with your messages."
                } else "Personalization is off. Tap to allow tailored answers (asks first).",
                style = MaterialTheme.typography.bodySmall,
                color = InkTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isPersonalized) "Turn off" else "Turn on",
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
                        text = if (topic != InterventionTopic.GENERAL) "Focus Notice Topic" else "Simulens Buddy ✨",
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
            if (m.fromUser) "..." else "I wasn't able to generate a response. Please check your connection or try asking again."
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
            modifier = Modifier.widthIn(max = 340.dp)
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
            val distinctSources = remember(m.sources) {
                m.sources.groupBy { it.url.ifBlank { it.title } }.values.map { list ->
                    val numbers = list.map { it.n }.distinct().sorted().joinToString(", ")
                    list.first() to numbers
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                distinctSources.forEach { (s, numbers) ->
                    AssistChip(
                        onClick = { if (s.url.startsWith("http")) uriHandler.openUri(s.url) },
                        label = {
                            Text(
                                "[$numbers] ${s.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp)
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
            var trimmed = line.trimEnd()
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

            if (isHeading) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = headingColor, fontSize = 15.sp))
            }

            // Regex for bold (**text** or __text__) and italic (*text* or _text_)
            val formatRegex = Regex("""(\*\*|__)(.*?)\1|(\*|_)(.*?)\3""")
            var lastMatchEnd = 0
            val matches = formatRegex.findAll(trimmed).toList()

            if (matches.isEmpty()) {
                append(trimmed)
            } else {
                for (match in matches) {
                    // Append any unformatted text preceding the match
                    if (match.range.first > lastMatchEnd) {
                        append(trimmed.substring(lastMatchEnd, match.range.first))
                    }

                    val boldGroup = match.groupValues[2]
                    val italicGroup = match.groupValues[4]

                    if (boldGroup.isNotEmpty()) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(boldGroup)
                        pop()
                    } else if (italicGroup.isNotEmpty()) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(italicGroup)
                        pop()
                    } else {
                        append(match.value)
                    }

                    lastMatchEnd = match.range.last + 1
                }
                // Append trailing text if any
                if (lastMatchEnd < trimmed.length) {
                    append(trimmed.substring(lastMatchEnd))
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
