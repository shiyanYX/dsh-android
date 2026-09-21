package com.dsh.android.domain.model

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val toolCalls: List<ToolCall> = emptyList()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
