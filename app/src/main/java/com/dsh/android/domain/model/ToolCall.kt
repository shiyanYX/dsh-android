package com.dsh.android.domain.model

data class ToolCall(
    val id: String,
    val toolName: String,
    val args: Map<String, Any>,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    ERROR
}
