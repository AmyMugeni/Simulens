package com.simulens.app.rag

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.chat.ChatMessage
import com.simulens.app.data.chat.ChatRepository
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.bsmas.BsmasScoring
import com.simulens.app.domain.chat.ChatSeed
import com.simulens.app.domain.chat.ChatSeedBuilder
import com.simulens.app.domain.chat.SeedChip
import com.simulens.app.domain.rules.intervention.InterventionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val topic: InterventionTopic = InterventionTopic.GENERAL,
    val askedPrompts: Set<String> = emptySet(),
    /** Off by default. Turning it on needs the user's confirmation (see ChatScreen). */
    val isPersonalized: Boolean = false,
    val personalizedSummary: String = "",
    /** Tappable replies under the nudge opener. Cleared as soon as the user sends anything. */
    val seedChips: List<SeedChip> = emptyList()
) {
    /** Suggestions for the current topic that the user has not asked yet. */
    val remainingPrompts: List<String>
        get() = topic.prompts.filter { it !in askedPrompts }
}

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val client: ChatClient = ChatClient(),
    // Short, consented summary. Only sent when personalization is on. Never send raw usage logs.
    private val contextProvider: () -> String = { "" }
) : AndroidViewModel(application) {

    private val repository = ChatRepository(SimulensDatabase.getDatabase(application).chatDao())
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ChatUiState(isPersonalized = prefs.getBoolean(KEY_PERSONALIZED, false))
    )
    val state: StateFlow<ChatUiState> = _state

    private var handledSeedId: String? = null

    init {
        viewModelScope.launch {
            repository.pruneOldMessages()
            repository.messages.collect { msgs ->
                val asked = msgs.filter { it.fromUser }.map { it.text }.toSet()
                _state.update {
                    it.copy(
                        messages = msgs,
                        askedPrompts = it.askedPrompts + asked
                    )
                }
            }
        }
        viewModelScope.launch {
            if (_state.value.isPersonalized) refreshPersonalizedSummary()
        }
    }

    /** Turning on must only be called after the user confirmed the disclosure dialog. */
    fun setPersonalization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSONALIZED, enabled).apply()
        _state.update {
            it.copy(isPersonalized = enabled, personalizedSummary = if (enabled) it.personalizedSummary else "")
        }
        if (enabled) viewModelScope.launch { refreshPersonalizedSummary() }
    }

    private suspend fun refreshPersonalizedSummary() {
        val summary = buildPersonalizedContext()
        _state.update { it.copy(personalizedSummary = summary) }
    }

    /**
     * A short, coarse summary of the user's own data. Sent to the remote model only when
     * personalization is on. Typed triggers, a 7/14 day window, no app names, no invented values.
     */
    private suspend fun buildPersonalizedContext(): String = withContext(Dispatchers.IO) {
        try {
            val db = SimulensDatabase.getDatabase(getApplication())
            val now = System.currentTimeMillis()
            val parts = mutableListOf<String>() // highest priority first

            val bsmas = db.bsmasResultDao().getLatest()
            if (bsmas != null) {
                val band = BsmasScoring.score(bsmas.items())?.band?.name?.lowercase()
                parts.add(
                    if (band != null) "BSMAS score ${bsmas.total}/30 ($band risk)."
                    else "BSMAS score ${bsmas.total}/30."
                )
            }

            val sessions = db.behavioralSessionDao().getSessionsSince(now - 7 * DAY_MS)
            if (sessions.isNotEmpty()) {
                val zone = ZoneId.systemDefault()
                val days = sessions
                    .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                    .distinct().size.coerceAtLeast(1)
                val avgMins = sessions.sumOf { it.elapsedDuration } / days
                val peak = sessions.groupBy { it.timeOfDay }
                    .maxByOrNull { (_, v) -> v.sumOf { it.elapsedDuration } }?.key
                var usage = "Last 7 days: about ${formatMins(avgMins)}/day on days with use, " +
                        "${sessions.size} sessions"
                if (peak != null) usage += ", busiest around %02d:00".format(peak)
                parts.add("$usage.")

                val extras = buildList {
                    sessions.count { it.scheduleConflict == 1 }.takeIf { it > 0 }
                        ?.let { add("$it during class") }
                    sessions.count { it.bedtimeSocialMedia == 1 }.takeIf { it > 0 }
                        ?.let { add("$it near bedtime") }
                    sessions.count { it.morningSocialMedia == 1 }.takeIf { it > 0 }
                        ?.let { add("$it soon after waking") }
                }
                if (extras.isNotEmpty()) parts.add("Sessions " + extras.joinToString(", ") + ".")
            }

            val logs = db.interventionLogDao().getAllLogs().firstOrNull().orEmpty()
            val top = logs.asSequence()
                .filter { it.timestamp >= now - 14 * DAY_MS }
                // Skips SNOOZED / DISMISSED rows, which do not start with a rule name.
                .mapNotNull { l ->
                    runCatching { InterventionType.valueOf(l.triggerReason.substringBefore(":").trim()) }
                        .getOrNull()
                }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(2)
            if (top.isNotEmpty()) {
                parts.add(
                    "Recent nudges (14 days): " +
                            top.joinToString(", ") { "${plainName(it.key)} (${it.value}x)" } + "."
                )
            }

            val profile = UserProfileRepository(getApplication()).userProfileFlow.firstOrNull()
            if (profile != null && profile.isSleepConfigured) {
                parts.add("Wakes ${profile.wakeUpTime}, bedtime ${profile.sleepTime}.")
            }

            fit(parts, USER_BUDGET)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    /** Whole parts only, in priority order, so nothing is cut mid-sentence. */
    private fun fit(parts: List<String>, budget: Int): String {
        val sb = StringBuilder()
        for (p in parts) {
            val add = if (sb.isEmpty()) p else " $p"
            if (sb.length + add.length <= budget) sb.append(add)
        }
        return sb.toString()
    }

    private fun plainName(t: InterventionType) = when (t) {
        InterventionType.SCHEDULE_CONFLICT -> "phone use in class"
        InterventionType.SLEEP_WINDOW -> "use during sleep hours"
        InterventionType.WIND_DOWN -> "use before bed"
        InterventionType.MORNING -> "use soon after waking"
        InterventionType.HIGH_SESSION_DEVIATION -> "unusually long visits"
        InterventionType.HIGH_DAILY_DEVIATION -> "heavier-than-usual days"
    }

    private fun formatMins(mins: Double): String {
        val total = mins.roundToInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Called when the chat opens. A new intervention swaps the suggestions; the conversation stays. */
    fun setTopic(topic: InterventionTopic) {
        _state.update { if (it.topic == topic) it else it.copy(topic = topic) }
    }

    /**
     * Opens the conversation from a nudge. The opener is written on the device and stored as
     * [Mode.LOCAL], so it is shown but never sent to the server as history.
     */
    fun startFromSeed(seed: ChatSeed) {
        if (seed.id == handledSeedId) return
        handledSeedId = seed.id

        val opener = ChatSeedBuilder.opener(seed)
        _state.update { it.copy(topic = topicFor(seed), seedChips = opener.chips, error = null) }
        viewModelScope.launch {
            repository.add(ChatMessage(fromUser = false, text = opener.text, mode = Mode.LOCAL))
        }
    }

    /** A chip sends its query, so the answer is grounded in sources like any other question. */
    fun pickChip(label: String) {
        val chip = _state.value.seedChips.firstOrNull { it.label == label } ?: return
        send(chip.query)
    }

    private fun topicFor(seed: ChatSeed) = when {
        seed.bsmasScore != null -> InterventionTopic.BSMAS_HIGH
        seed.riskProbability != null -> InterventionTopic.LONG_SESSION
        seed.type == InterventionType.SCHEDULE_CONFLICT -> InterventionTopic.LECTURE_HOURS
        seed.type == InterventionType.SLEEP_WINDOW || seed.type == InterventionType.WIND_DOWN -> InterventionTopic.LATE_NIGHT
        seed.type == InterventionType.MORNING -> InterventionTopic.MORNING_USE
        seed.type == InterventionType.HIGH_SESSION_DEVIATION || seed.type == InterventionType.HIGH_DAILY_DEVIATION ->
            InterventionTopic.LONG_SESSION
        else -> InterventionTopic.GENERAL
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return

        val userMsg = ChatMessage(fromUser = true, text = message)

        // Only real answers go back as history. Crisis replies and the local opener are left out,
        // so they don't steer later answers and the opener's app/class details stay on the device.
        val history = _state.value.messages
            .filter { it.mode == Mode.ANSWER }
            .map { Turn(if (it.fromUser) "user" else "assistant", it.text) }

        _state.update {
            it.copy(
                askedPrompts = it.askedPrompts + message,
                seedChips = emptyList(),
                sending = true,
                error = null
            )
        }

        viewModelScope.launch {
            repository.add(userMsg)

            // Rebuilt on every send so it never goes stale. Nothing personal leaves the device
            // unless the user turned personalization on.
            val userData = if (_state.value.isPersonalized) {
                val fresh = buildPersonalizedContext()
                _state.update { it.copy(personalizedSummary = fresh) }
                listOf(fresh, contextProvider()).filter { it.isNotBlank() }.joinToString(" ")
            } else ""

            // The generic topic line goes first so the length cap can never cut it off.
            val topicLine = _state.value.topic.contextLine.take(TOPIC_BUDGET)
            val room = (MAX_CONTEXT - topicLine.length - 1).coerceAtLeast(0)
            val context = listOf(topicLine, userData.take(room))
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(MAX_CONTEXT)

            runCatching { client.ask(message, history, context) }
                .onSuccess { reply ->
                    val assistantMsg = ChatMessage(
                        fromUser = false,
                        text = reply.answer,
                        sources = reply.sources,
                        mode = reply.mode
                    )
                    repository.add(assistantMsg)
                    _state.update { it.copy(sending = false) }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update {
                        it.copy(sending = false, error = e.message ?: "Could not reach the assistant.")
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clear()
            _state.update { it.copy(messages = emptyList(), askedPrompts = emptySet(), seedChips = emptyList()) }
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val PREFS = "chat_prefs"
        const val KEY_PERSONALIZED = "personalization_enabled"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val MAX_CONTEXT = 600   // must match the server's MAX_CONTEXT_CHARS
        const val TOPIC_BUDGET = 150
        const val USER_BUDGET = 440
    }
}
