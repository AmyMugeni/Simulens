package com.cognilens.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognilens.app.data.chat.ChatClient
import com.cognilens.app.data.chat.Mode
import com.cognilens.app.data.chat.Source
import com.cognilens.app.data.chat.Turn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val mode: Mode = Mode.ANSWER
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val client: ChatClient = ChatClient(),
    // Short, consented summary, e.g. "BSMAS risk: moderate. Usually most active 23:00-01:00."
    // Build it from your own data and keep it under ~600 characters. Never send raw usage logs.
    private val contextProvider: () -> String = { "" }
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return

        // Crisis replies are not sent back as history, so they don't steer later answers.
        val history = _state.value.messages
            .filter { it.mode == Mode.ANSWER }
            .map { Turn(if (it.fromUser) "user" else "assistant", it.text) }

        _state.update {
            it.copy(messages = it.messages + ChatMessage(true, message), sending = true, error = null)
        }
        viewModelScope.launch {
            runCatching { client.ask(message, history, contextProvider()) }
                .onSuccess { reply ->
                    _state.update {
                        it.copy(
                            messages = it.messages + ChatMessage(false, reply.answer, reply.sources, reply.mode),
                            sending = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(sending = false, error = e.message ?: "Could not reach the assistant.")
                    }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
