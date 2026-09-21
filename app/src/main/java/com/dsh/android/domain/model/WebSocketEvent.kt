package com.dsh.android.domain.model

sealed class WebSocketEvent {
    data class Connected(val sessionId: String) : WebSocketEvent()
    data class Disconnected(val reason: String? = null) : WebSocketEvent()
    data class MessageReceived(val message: Message) : WebSocketEvent()
    data class ToolCallReceived(val toolCall: ToolCall) : WebSocketEvent()
    data class AgentStatusChanged(val status: AgentStatus) : WebSocketEvent()
    data class Error(val error: Throwable) : WebSocketEvent()
}

enum class AgentStatus {
    IDLE,
    RUNNING,
    WAITING_CONFIRMATION
}
