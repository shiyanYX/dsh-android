package com.dsh.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.domain.model.*
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val agentStatus: AgentStatus = AgentStatus.IDLE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: DshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        observeWebSocketEvents()
    }

    fun setSession(sessionId: String) {
        currentSessionId = sessionId
        repository.connectWebSocket(sessionId)
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            repository.webSocketEvents.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    is WebSocketEvent.Disconnected -> {
                        _uiState.value = _uiState.value.copy(error = "连接断开")
                    }
                    is WebSocketEvent.MessageReceived -> {
                        val messages = _uiState.value.messages + event.message
                        _uiState.value = _uiState.value.copy(messages = messages)
                    }
                    is WebSocketEvent.ToolCallReceived -> {
                        val toolCalls = _uiState.value.toolCalls + event.toolCall
                        _uiState.value = _uiState.value.copy(
                            toolCalls = toolCalls,
                            agentStatus = AgentStatus.WAITING_CONFIRMATION
                        )
                    }
                    is WebSocketEvent.AgentStatusChanged -> {
                        _uiState.value = _uiState.value.copy(agentStatus = event.status)
                    }
                    is WebSocketEvent.Error -> {
                        _uiState.value = _uiState.value.copy(error = event.error.message)
                    }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || currentSessionId == null) return

        val userMessage = Message(
            id = System.currentTimeMillis().toString(),
            sessionId = currentSessionId!!,
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            agentStatus = AgentStatus.RUNNING
        )

        viewModelScope.launch {
            repository.sendMessage(currentSessionId!!, text)
        }
    }

    fun confirmToolCall(callId: String, approved: Boolean) {
        val toolCalls = _uiState.value.toolCalls.map { tc ->
            if (tc.id == callId) {
                tc.copy(status = if (approved) ToolCallStatus.APPROVED else ToolCallStatus.REJECTED)
            } else {
                tc
            }
        }
        _uiState.value = _uiState.value.copy(
            toolCalls = toolCalls,
            agentStatus = AgentStatus.RUNNING
        )

        viewModelScope.launch {
            repository.confirmToolCall(currentSessionId!!, callId, approved)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectWebSocket()
    }
}
