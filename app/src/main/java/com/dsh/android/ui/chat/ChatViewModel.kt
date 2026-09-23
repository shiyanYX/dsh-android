package com.dsh.android.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.android.domain.model.*
import com.dsh.android.domain.repository.DshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TokenStats(
    val turns: Int = 0,
    val steps: Int = 0,
    val ttft: Long = 0 // time to first token in ms
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val agentStatus: AgentStatus = AgentStatus.IDLE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val availableModels: List<DshModel> = emptyList(),
    val selectedModel: String? = null,
    val showModelSelector: Boolean = false,
    val tokenStats: TokenStats = TokenStats(),
    val selectedMessageId: String? = null, // for context menu
    val showMessageActions: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: DshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null
    private var sendTimestamp: Long = 0L

    init {
        observeWebSocketEvents()
        loadModels()
    }

    fun setSession(sessionId: String) {
        currentSessionId = sessionId
        // Load history first, then connect WebSocket for real-time updates
        loadHistory(sessionId)
        repository.connectWebSocket(sessionId)
    }

    private fun loadHistory(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.getSessionHistory(sessionId, maxMessages = 200)
            result.fold(
                onSuccess = { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    Log.w("ChatVM", "Failed to load history: ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            val result = repository.getModels()
            result.fold(
                onSuccess = { models ->
                    _uiState.value = _uiState.value.copy(
                        availableModels = models,
                        selectedModel = models.firstOrNull()?.id
                    )
                },
                onFailure = { /* Models unavailable, use default */ }
            )
        }
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
                        val ttft = if (sendTimestamp > 0) System.currentTimeMillis() - sendTimestamp else 0L
                        val messages = _uiState.value.messages + event.message
                        val stats = _uiState.value.tokenStats.copy(
                            turns = _uiState.value.tokenStats.turns + 1,
                            ttft = ttft
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            tokenStats = stats
                        )
                    }
                    is WebSocketEvent.ToolCallReceived -> {
                        val toolCalls = _uiState.value.toolCalls + event.toolCall
                        val stats = _uiState.value.tokenStats.copy(
                            steps = _uiState.value.tokenStats.steps + 1
                        )
                        _uiState.value = _uiState.value.copy(
                            toolCalls = toolCalls,
                            agentStatus = AgentStatus.WAITING_CONFIRMATION,
                            tokenStats = stats
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

    fun toggleModelSelector() {
        _uiState.value = _uiState.value.copy(
            showModelSelector = !_uiState.value.showModelSelector
        )
    }

    fun selectModel(modelId: String) {
        _uiState.value = _uiState.value.copy(
            selectedModel = modelId,
            showModelSelector = false
        )
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || currentSessionId == null) return

        sendTimestamp = System.currentTimeMillis()

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

    // Message actions
    fun showMessageActions(messageId: String) {
        _uiState.value = _uiState.value.copy(
            selectedMessageId = messageId,
            showMessageActions = true
        )
    }

    fun hideMessageActions() {
        _uiState.value = _uiState.value.copy(
            selectedMessageId = null,
            showMessageActions = false
        )
    }

    fun deleteMessage(messageId: String) {
        val messages = _uiState.value.messages.filter { it.id != messageId }
        _uiState.value = _uiState.value.copy(
            messages = messages,
            selectedMessageId = null,
            showMessageActions = false
        )
    }

    fun regenerateMessage(messageId: String) {
        // Find the user message that preceded this assistant message
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return

        // Find the previous user message
        var userMessage: Message? = null
        for (i in (0 until index).reversed()) {
            if (messages[i].role == MessageRole.USER) {
                userMessage = messages[i]
                break
            }
        }
        if (userMessage == null) return

        // Remove all messages from this assistant message onward
        val trimmedMessages = messages.subList(0, index).toList()

        _uiState.value = _uiState.value.copy(
            messages = trimmedMessages,
            agentStatus = AgentStatus.RUNNING,
            selectedMessageId = null,
            showMessageActions = false
        )

        // Resend the user message
        sendTimestamp = System.currentTimeMillis()
        viewModelScope.launch {
            repository.sendMessage(currentSessionId!!, userMessage.content)
        }
    }

    fun copyMessageContent(messageId: String): String? {
        return _uiState.value.messages.find { it.id == messageId }?.content
    }

    fun stopAgent() {
        viewModelScope.launch {
            repository.sendMessage(currentSessionId!!, "/stop")
            _uiState.value = _uiState.value.copy(agentStatus = AgentStatus.IDLE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectWebSocket()
    }
}
