package com.simulens.app.rag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.chat.ChatMessage
import com.simulens.app.data.chat.ChatRepository
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.bsmas.BsmasScoring
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val topic: InterventionTopic = InterventionTopic.GENERAL,
    val askedPrompts: Set<String> = emptySet(),
    val isPersonalized: Boolean = true,
    val personalizedSummary: String = ""
) {
    /** Suggestions for the current topic that the user has not asked yet. */
    val remainingPrompts: List<String>
        get() = topic.prompts.filter { it !in askedPrompts }
}

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val client: ChatClient = ChatClient(),
    // Short, consented summary, e.g. "BSMAS risk: moderate. Usually most active 23:00-01:00."
    // Build it from your own data and keep it under ~600 characters. Never send raw usage logs.
    private val contextProvider: () -> String = { "" }
) : AndroidViewModel(application) {

    private val repository = ChatRepository(SimulensDatabase.getDatabase(application).chatDao())

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    init {
        viewModelScope.launch {
            repository.pruneOldMessages()
            refreshPersonalizedSummary()
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
    }

    fun togglePersonalization() {
        _state.update { it.copy(isPersonalized = !it.isPersonalized) }
    }

    private suspend fun refreshPersonalizedSummary() {
        val summary = buildPersonalizedContext()
        _state.update { it.copy(personalizedSummary = summary) }
    }

    private suspend fun buildPersonalizedContext(): String {
        return runCatching {
            val db = SimulensDatabase.getDatabase(getApplication())
            val bsmas = db.bsmasResultDao().getLatest()
            val logs = db.interventionLogDao().getAllLogs().firstOrNull() ?: emptyList()
            val profile = UserProfileRepository(getApplication()).userProfileFlow.firstOrNull()

            val parts = mutableListOf<String>()

            if (bsmas != null) {
                val scoreObj = BsmasScoring.score(bsmas.items())
                val bandName = scoreObj?.band?.name ?: "MODERATE"
                parts.add("BSMAS score: ${bsmas.total}/30 ($bandName risk).")
            }

            if (logs.isNotEmpty()) {
                val topTriggers = logs.groupBy { it.triggerReason }
                    .mapValues { it.value.size }
                    .entries.sortedByDescending { it.value }
                    .take(2)
                    .joinToString(", ") { "${it.key} (${it.value}x)" }
                if (topTriggers.isNotBlank()) {
                    parts.add("Top struggles: $topTriggers.")
                }
            }

            if (profile != null && profile.isSleepConfigured) {
                parts.add("Schedule: Wake ${profile.wakeUpTime}, Bed ${profile.sleepTime}.")
            }

            parts.joinToString(" ").take(500)
        }.getOrDefault("")
    }

    /** Called when the chat opens. A new intervention swaps the suggestions; the conversation stays. */
    fun setTopic(topic: InterventionTopic) {
        _state.update { if (it.topic == topic) it else it.copy(topic = topic) }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return

        val userMsg = ChatMessage(fromUser = true, text = message)

        // Crisis replies are not sent back as history, so they don't steer later answers.
        val history = _state.value.messages
            .filter { it.mode == Mode.ANSWER }
            .map { Turn(if (it.fromUser) "user" else "assistant", it.text) }

        _state.update {
            it.copy(
                askedPrompts = it.askedPrompts + message,
                sending = true,
                error = null
            )
        }

        viewModelScope.launch {
            repository.add(userMsg)
            val personalizedContext = if (_state.value.isPersonalized) {
                _state.value.personalizedSummary.ifBlank { buildPersonalizedContext() }
            } else ""

            val context = listOf(personalizedContext, contextProvider(), _state.value.topic.contextLine)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(600)

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
                    _state.update {
                        it.copy(sending = false, error = e.message ?: "Could not reach the assistant.")
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clear()
            _state.update { it.copy(messages = emptyList(), askedPrompts = emptySet()) }
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
